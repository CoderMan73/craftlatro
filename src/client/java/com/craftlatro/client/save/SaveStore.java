package com.craftlatro.client.save;

import com.craftlatro.client.CraftlatroPaths;
import com.craftlatro.client.browser.BalatroBrowser;
import com.craftlatro.client.extractor.GameLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Portable copies of the love.js save store.
 *
 * <p>Balatro's progress does not live in the game package; it lives in the browser's IndexedDB
 * (Emscripten IDBFS plus the love.js package cache), inside the Chromium profile Rinku keeps under
 * its persistent browser-data directory. Nothing in the mod owns that directory, so this class
 * treats it as a black box: an export is a copy of the {@code IndexedDB} directories found under the
 * cache root, and an import is a staged copy of them back.
 *
 * <p>Version awareness matters here. love.js keys its package cache on the {@code ?g=} value, which
 * this mod derives from the mtime of {@code game.love} (see {@link GameLauncher#ensureIndexHtml}).
 * A different {@code ?g=} value is a different cache key, so a store exported against one package
 * can carry entries a newer package will never read again. Every export therefore records the
 * package version it was taken against, and an import against a different one warns instead of
 * silently promising parity — whether the save store itself survives a version bump is unverified
 * without a launch.
 *
 * <p>Both directions are deliberately conservative because this cannot be tested headlessly:
 * exporting drops the browser session first, which is best-effort — love.js writes through
 * Emscripten's IndexedDB layer and whether the last write reached disk before the session dropped is
 * unverified without a launch, so an export can in principle be one save behind. Importing stages the
 * copy and applies it at the next game start, before Rinku initializes CEF, so no browser process can
 * be holding the store's LevelDB locks. The store that is about to be replaced is automatically
 * backed up first.
 */
public final class SaveStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");

    private static final String STAGING_DIRECTORY = "staging";
    private static final String BACKUP_DIRECTORY = "backups";
    private static final String INDEXED_DB_DIRECTORY = "IndexedDB";
    /**
     * Asks the page to delete every database except the two love.js package caches, which hold the
     * game archive itself: {@code EM_PRELOAD_CACHE} and {@code PACKAGES}. Everything else under the
     * origin is save data written through Emscripten's IDBFS layer, which is what goes.
     */
    private static final String CLEAR_STORE_SCRIPT = "(function(){"
            + "var keep = ['EM_PRELOAD_CACHE', 'PACKAGES'];"
            + "indexedDB.databases().then(function(databases){"
            + "databases.forEach(function(entry){"
            + "if (entry.name && keep.indexOf(entry.name) < 0) { indexedDB.deleteDatabase(entry.name); }"
            + "});"
            + "});"
            + "})();";
    private static final String STORE_PREFIX = "indexeddb/";
    private static final String META_FILE_NAME = "meta.json";
    private static final String PENDING_RESTORE_FILE = "pending-restore.json";
    private static final int CACHE_SEARCH_DEPTH = 4;
    private static final int RESTORE_ATTEMPTS = 60;

    private static final String META_FORMAT = "format";
    private static final String META_GAME_VERSION = "gameVersion";
    private static final String META_EXPORTED_AT = "exportedAt";
    private static final String META_STORE_ROOTS = "storeRoots";
    private static final String META_FILE_COUNT = "fileCount";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ROOT);

    private static int restoreAttempts;

    private SaveStore() {
    }

    /** A saved export on disk, or the result of a fresh export. */
    public record SaveSummary(String name, long exportedAtMillis, String gameVersion, int fileCount) {
    }

    // ------------------------------------------------------------------ export

    /**
     * Copies the browser's IndexedDB store into {@code saves/<name>.zip}.
     *
     * @return the saved name, or {@code null} when nothing could be exported
     */
    public static String export(String name) {
        Path cacheRoot = browserCacheRoot();
        if (cacheRoot == null || !Files.isDirectory(cacheRoot)) {
            LOGGER.warn("No Rinku browser data directory found; cannot export the save store.");
            return null;
        }
        List<Path> stores = dataDirectories(cacheRoot);
        if (stores.isEmpty()) {
            LOGGER.warn("No IndexedDB store under {}; the game has probably never been opened.", cacheRoot);
            return null;
        }

        // A live snapshot of a LevelDB-backed store can be torn, so the page is dropped first. The
        // next open rebuilds the session, exactly like the reload command already does.
        BalatroBrowser.release();

        Path target = savesDirectory().resolve(safeFileName(name) + ".zip");
        String gameVersion = GameLauncher.currentGameVersion();
        List<String> storeRoots = new ArrayList<>();
        int fileCount = 0;
        try {
            Files.createDirectories(savesDirectory());
        } catch (IOException failure) {
            LOGGER.error("Could not create the save directory {}", savesDirectory(), failure);
            return null;
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (Path store : stores) {
                String root = cacheRoot.relativize(store).toString().replace('\\', '/');
                storeRoots.add(root);
                fileCount += addDirectory(zip, store, STORE_PREFIX + root + "/");
            }
            zip.putNextEntry(entry(META_FILE_NAME));
            zip.write(metaJson(gameVersion, storeRoots, fileCount).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException failure) {
            LOGGER.error("Failed to export the save store to {}", target, failure);
            deleteQuietly(target);
            return null;
        }

        LOGGER.info("Exported the save store ({} files, game version {}) to {}", fileCount, gameVersion, target);
        return target.getFileName().toString();
    }

    private static int addDirectory(ZipOutputStream zip, Path root, String prefix) throws IOException {
        int count = 0;
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::toString));
        for (Path file : files) {
            String entryName = prefix + root.relativize(file).toString().replace('\\', '/');
            zip.putNextEntry(entry(entryName));
            Files.copy(file, zip);
            zip.closeEntry();
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ clear

    /**
     * Erases the game's save data: progress, unlocks and settings the game itself keeps.
     *
     * <p>With a live session the page is asked to delete its own databases, everything except the
     * love.js package caches — {@code EM_PRELOAD_CACHE} and {@code PACKAGES} hold the game archive
     * and would cost a fresh download to rebuild — and the page is reloaded so the game restarts on a
     * clean profile rather than continuing from what it still has in memory. Without one the on-disk
     * store is deleted instead, which is the same thing one session later.
     *
     * @return true when something was found to erase
     */
    public static boolean clear() {
        if (BalatroBrowser.isAvailable()) {
            BalatroBrowser.executeScript(CLEAR_STORE_SCRIPT);
            BalatroBrowser.reloadPage();
            LOGGER.info("Asked the page to erase its save store and restart.");
            return true;
        }
        return clearStoreOnDisk();
    }

    private static boolean clearStoreOnDisk() {
        Path cacheRoot = browserCacheRoot();
        if (cacheRoot == null || !Files.isDirectory(cacheRoot)) {
            LOGGER.warn("No Rinku browser data directory found; there is no save store to erase.");
            return false;
        }
        List<Path> stores = dataDirectories(cacheRoot);
        if (stores.isEmpty()) {
            LOGGER.warn("No IndexedDB store under {}; the game has probably never been opened.", cacheRoot);
            return false;
        }
        boolean erased = false;
        for (Path store : stores) {
            if (deleteTree(store)) {
                erased = true;
            }
        }
        return erased;
    }

    private static boolean deleteTree(Path root) {
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
            return true;
        } catch (IOException failure) {
            LOGGER.error("Could not erase {}", root, failure);
            return false;
        }
    }

    // ------------------------------------------------------------------ import

    /**
     * Stages the named export to be restored at the next game start.
     *
     * @return the staged file name, or {@code null} when the archive could not be staged
     */
    public static String importStaged(String nameOrPath) {
        Path source = resolveExportFile(nameOrPath);
        if (source == null) {
            LOGGER.warn("Save export not found: {}", nameOrPath);
            return null;
        }

        Map<String, String> meta = new LinkedHashMap<>();
        List<Path> stagedFiles = new ArrayList<>();
        try (ZipFile zip = new ZipFile(source.toFile())) {
            ZipEntry metaEntry = zip.getEntry(META_FILE_NAME);
            if (metaEntry == null) {
                LOGGER.warn("{} is not a craftlatro save export (no {})", source, META_FILE_NAME);
                return null;
            }
            meta = parseMeta(readEntry(zip, metaEntry));
            if (!"1".equals(meta.getOrDefault(META_FORMAT, ""))) {
                LOGGER.warn("{} uses an unknown save format: {}", source, meta.get(META_FORMAT));
                return null;
            }
            var entries = zip.entries();
            Path staging = stagingDirectory().resolve(System.currentTimeMillis() + "-" + System.nanoTime());
            Files.createDirectories(staging);
            while (entries.hasMoreElements()) {
                ZipEntry candidate = entries.nextElement();
                String entryName = candidate.getName().replace('\\', '/');
                if (!entryName.startsWith(STORE_PREFIX) || candidate.isDirectory()) {
                    continue;
                }
                Path destination = staging.resolve(entryName).normalize();
                if (!destination.startsWith(staging)) {
                    LOGGER.warn("Skipping unsafe entry in {}: {}", source, entryName);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                try (InputStream in = zip.getInputStream(candidate)) {
                    Files.copy(in, destination);
                }
                stagedFiles.add(destination);
            }
            if (stagedFiles.isEmpty()) {
                deleteQuietly(staging);
                LOGGER.warn("{} carries no store files", source);
                return null;
            }
            writePendingRestore(meta, staging, source);
        } catch (IOException failure) {
            LOGGER.error("Failed to stage the save export {}", source, failure);
            return null;
        }

        // The live page is dropped so the restored store is what the next session reads; the copy is
        // applied before CEF starts, on the next game start.
        BalatroBrowser.release();
        LOGGER.info("Staged save export {} ({} files); it will be restored at the next game start.",
                source.getFileName(), stagedFiles.size());
        return source.getFileName().toString();
    }

    /**
     * Applies a staged restore. Called at mod init, before Rinku has initialized CEF, so no browser
     * process can be holding the store's LevelDB locks, and retried on the client tick in case the
     * browser data directory only appears later.
     *
     * @return true once the restore is done or nothing was pending
     */
    public static synchronized boolean applyPendingRestore() {
        restoreAttempts++;
        Path marker = savesDirectory().resolve(PENDING_RESTORE_FILE);
        if (!Files.exists(marker)) {
            restoreAttempts = 0;
            return true;
        }
        if (restoreAttempts > RESTORE_ATTEMPTS) {
            LOGGER.error("Giving up on the pending save restore; it stays staged under {}", stagingDirectory());
            return false;
        }

        Map<String, String> pending = parseMeta(readFile(marker));
        Path staging = Path.of(pending.getOrDefault("staging", ""));
        if (!Files.isDirectory(staging)) {
            LOGGER.error("Pending restore points at a missing staging directory {}", staging);
            return false;
        }

        Path cacheRoot = browserCacheRoot();
        if (cacheRoot == null) {
            LOGGER.info("No Rinku browser data directory yet; the restore will be retried.");
            return false;
        }

        try {
            backUpCurrentStore(cacheRoot);
            List<Path> stagedRoots = storeRoots(staging);
            for (Path stagedRoot : stagedRoots) {
                // The staged copy keeps the store's path under the cache root ("Default/IndexedDB",
                // and CEF may nest it differently), so the restore puts it back exactly there.
                String relative = staging.relativize(stagedRoot).toString().replace('\\', '/');
                if (relative.startsWith(STORE_PREFIX)) {
                    relative = relative.substring(STORE_PREFIX.length());
                }
                Path target = cacheRoot.resolve(relative).normalize();
                if (!target.startsWith(cacheRoot)) {
                    LOGGER.warn("Skipping staged store outside the browser data directory: {}", stagedRoot);
                    continue;
                }
                replaceStore(target, stagedRoot);
            }
            deleteQuietly(staging);
            deleteQuietly(marker);
            LOGGER.info("Restored the staged save store into {}", cacheRoot);
            return true;
        } catch (IOException failure) {
            LOGGER.error("Could not restore the staged save store; it stays staged.", failure);
            return false;
        }
    }

    /**
     * Swaps the staged store into place, keeping the current one until the new copy is complete.
     *
     * <p>The staged files are first copied next to the target and only then swapped in, so a failure
     * half-way through cannot leave a half-written store behind: the previous one is kept as
     * {@code .replaced} and put back if the swap fails.
     */
    private static void replaceStore(Path target, Path stagedRoot) throws IOException {
        Path holding = target.resolveSibling(target.getFileName() + ".incoming");
        deleteRecursively(holding);
        Files.createDirectories(holding);
        copyTree(stagedRoot, holding);

        Path replaced = target.resolveSibling(target.getFileName() + ".replaced");
        boolean hadTarget = Files.exists(target);
        if (hadTarget) {
            deleteRecursively(replaced);
            Files.move(target, replaced);
        }
        try {
            Files.move(holding, target);
        } catch (IOException failure) {
            if (hadTarget) {
                Files.move(replaced, target);
            }
            throw failure;
        }
        deleteQuietly(replaced);
    }

    private static void copyTree(Path sourceRoot, Path targetRoot) throws IOException {
        try (var walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile).forEach(source -> {
                Path destination = targetRoot.resolve(sourceRoot.relativize(source)).normalize();
                try {
                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            });
        }
    }

    /** Zips the store that is about to be replaced, so a bad import is always reversible. */
    private static void backUpCurrentStore(Path cacheRoot) throws IOException {
        List<Path> stores = dataDirectories(cacheRoot);
        if (stores.isEmpty()) {
            return;
        }
        Path backup = backupDirectory().resolve("auto-" + System.currentTimeMillis() + ".zip");
        List<String> roots = new ArrayList<>();
        int files = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(backup))) {
            for (Path store : stores) {
                String root = cacheRoot.relativize(store).toString().replace('\\', '/');
                roots.add(root);
                files += addDirectory(zip, store, STORE_PREFIX + root + "/");
            }
            zip.putNextEntry(entry(META_FILE_NAME));
            zip.write(metaJson(GameLauncher.currentGameVersion(), roots, files).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        LOGGER.info("Backed up the current save store ({} files) to {}", files, backup);
    }

    private static List<Path> storeRoots(Path staging) throws IOException {
        List<Path> roots = new ArrayList<>();
        try (var walk = Files.walk(staging)) {
            walk.filter(Files::isDirectory)
                    .filter(dir -> dir.getFileName().toString().equals(INDEXED_DB_DIRECTORY))
                    .forEach(roots::add);
        }
        roots.sort(Comparator.comparing(Path::toString));
        return roots;
    }

    // ------------------------------------------------------------------ listing

    /** The exports on disk, newest first. */
    public static List<SaveSummary> listExports() {
        List<SaveSummary> summaries = new ArrayList<>();
        Path directory = savesDirectory();
        if (!Files.isDirectory(directory)) {
            return summaries;
        }
        try (var names = Files.list(directory)) {
            names.filter(path -> path.getFileName().toString().endsWith(".zip")).forEach(file -> {
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    ZipEntry metaEntry = zip.getEntry(META_FILE_NAME);
                    if (metaEntry == null) {
                        return;
                    }
                    Map<String, String> meta = parseMeta(readEntry(zip, metaEntry));
                    summaries.add(new SaveSummary(
                            file.getFileName().toString().replace(".zip", ""),
                            Files.getLastModifiedTime(file).toMillis(),
                            meta.getOrDefault(META_GAME_VERSION, "unknown"),
                            Integer.parseInt(meta.getOrDefault(META_FILE_COUNT, "0"))));
                } catch (IOException | RuntimeException failure) {
                    LOGGER.warn("Could not read save export {}", file, failure);
                }
            });
        } catch (IOException failure) {
            LOGGER.warn("Could not list the save exports in {}", directory, failure);
        }
        summaries.sort(Comparator.comparingLong(SaveSummary::exportedAtMillis).reversed());
        return summaries;
    }

    // ------------------------------------------------------------------ profile lookup

    /**
     * The Rinku browser data directory, or the path it would use.
     *
     * <p>Rinku resolves its persistent cache path from the environment (documented in
     * {@code docs/rinku-api-notes.md}): {@code LOCALAPPDATA} and {@code APPDATA} on Windows,
     * {@code XDG_DATA_HOME} or the user home elsewhere, always with a {@code Rinku/cef-cache} tail.
     * That is an internal of a dependency, so the directory is discovered rather than assumed.
     *
     * <p>When no candidate exists yet the first one is still returned, so transferring a save to a
     * machine that has never run the game creates the profile the restored store belongs to. Callers
     * that need a profile that really exists check for themselves.
     */
    public static Path browserCacheRoot() {
        List<Path> candidates = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            candidates.add(Path.of(localAppData, "Rinku", "cef-cache"));
        }
        String roamingAppData = System.getenv("APPDATA");
        if (roamingAppData != null) {
            candidates.add(Path.of(roamingAppData, "Rinku", "cef-cache"));
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            String dataHome = xdgDataHome != null && !xdgDataHome.isBlank() ? xdgDataHome : Path.of(userHome, ".local", "share").toString();
            candidates.add(Path.of(dataHome, "Rinku", "cef-cache"));
            candidates.add(Path.of(userHome, ".cache", "Rinku", "cef-cache"));
            candidates.add(Path.of(userHome, ".config", "Rinku", "cef-cache"));
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.normalize();
            }
        }
        return candidates.isEmpty() ? null : candidates.getFirst().normalize();
    }

    /** Every {@code IndexedDB} store directory under the cache root, closest to it first. */
    public static List<Path> dataDirectories(Path cacheRoot) {
        List<Path> stores = new ArrayList<>();
        try (var walk = Files.walk(cacheRoot, CACHE_SEARCH_DEPTH)) {
            walk.filter(Files::isDirectory)
                    .filter(dir -> dir.getFileName().toString().equals(INDEXED_DB_DIRECTORY))
                    .forEach(stores::add);
        } catch (IOException failure) {
            LOGGER.warn("Could not scan {} for the browser store.", cacheRoot, failure);
        }
        stores.sort(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString));
        return stores;
    }

    // ------------------------------------------------------------------ files

    public static Path savesDirectory() {
        return CraftlatroPaths.savesDirectory();
    }

    private static Path stagingDirectory() {
        try {
            Path directory = savesDirectory().resolve(STAGING_DIRECTORY);
            Files.createDirectories(directory);
            return directory;
        } catch (IOException failure) {
            LOGGER.error("Could not create the save staging directory.", failure);
            return savesDirectory();
        }
    }

    private static Path backupDirectory() {
        try {
            Path directory = savesDirectory().resolve(BACKUP_DIRECTORY);
            Files.createDirectories(directory);
            return directory;
        } catch (IOException failure) {
            LOGGER.error("Could not create the save backup directory.", failure);
            return savesDirectory();
        }
    }

    private static Path resolveExportFile(String nameOrPath) {
        Path direct = Path.of(nameOrPath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path byName = savesDirectory().resolve(safeFileName(nameOrPath) + ".zip");
        return Files.exists(byName) ? byName : null;
    }

    private static String safeFileName(String raw) {
        String sanitized = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            sanitized = "save";
        }
        return sanitized.length() > 64 ? sanitized.substring(0, 64) : sanitized;
    }

    private static ZipEntry entry(String name) {
        return new ZipEntry(name);
    }

    private static String metaJson(String gameVersion, List<String> storeRoots, int fileCount) {
        return "{\n"
                + "  \"" + META_FORMAT + "\": \"1\",\n"
                + "  \"" + META_GAME_VERSION + "\": \"" + gameVersion + "\",\n"
                + "  \"" + META_EXPORTED_AT + "\": \"" + ZonedDateTime.now().format(TIMESTAMP) + "\",\n"
                + "  \"" + META_STORE_ROOTS + "\": \"" + String.join(",", storeRoots) + "\",\n"
                + "  \"" + META_FILE_COUNT + "\": " + fileCount + "\n"
                + "}";
    }

    private static String readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            LOGGER.warn("Could not read {}", path, failure);
            return "";
        }
    }

    /**
     * Reads the flat {@code "key": "value"} map this class writes. The writer only ever emits
     * numbers, ISO timestamps and forward-slash paths, so a small matcher is safer than a
     * dependency here.
     */
    private static Map<String, String> parseMeta(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : json.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("\"")) {
                int separator = trimmed.indexOf(": ");
                if (separator < 0) {
                    continue;
                }
                String key = trimmed.substring(1, separator - 1);
                String value = trimmed.substring(separator + 2).trim();
                if (value.startsWith("\"")) {
                    value = value.substring(1, Math.max(1, value.length() - 2));
                }
                values.put(key, value);
            }
        }
        return values;
    }

    private static void writePendingRestore(Map<String, String> meta, Path staging, Path source) throws IOException {
        String marker = "{\n"
                + "  \"staging\": \"" + staging.toString().replace('\\', '/') + "\",\n"
                + "  \"source\": \"" + source.getFileName() + "\",\n"
                + "  \"gameVersion\": \"" + meta.getOrDefault(META_GAME_VERSION, "unknown") + "\",\n"
                + "  \"storeRoots\": \"" + meta.getOrDefault(META_STORE_ROOTS, "") + "\"\n"
                + "}";
        Path markerPath = savesDirectory().resolve(PENDING_RESTORE_FILE);
        Files.createDirectories(savesDirectory());
        Files.writeString(markerPath, marker);
    }

    private static void deleteQuietly(Path path) {
        try {
            if (Files.isDirectory(path)) {
                deleteRecursively(path);
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            LOGGER.warn("Could not delete {}", path, failure);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            });
        }
    }
}
