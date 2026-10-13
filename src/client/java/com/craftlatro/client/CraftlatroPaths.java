package com.craftlatro.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Every file path the mod owns, in one place.
 *
 * <p>Minecraft's convention is that a mod keeps its settings under the instance's config directory and
 * its generated data under the instance root, never under the player's home directory: players move
 * instances between launchers, back them up, and delete them, and a mod that writes to {@code ~}
 * survives none of that. Cloth Config reads and writes {@code config/} for exactly this reason.
 *
 * <p>The mod used to keep everything in {@code ~/.craftlatro}, so the first read of each path migrates
 * what is already there — settings, save exports and the cached game package all move across, and the
 * move is best-effort because every one of them can also be rebuilt.
 */
public final class CraftlatroPaths {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");

    private static final String LEGACY_DIRECTORY_NAME = ".craftlatro";
    private static final String CONFIG_FILE_NAME = "craftlatro.properties";
    private static final String MOD_DIRECTORY_NAME = "craftlatro";
    private static final String SAVES_DIRECTORY_NAME = "saves";
    private static final String GAME_DIRECTORY_NAME = "game";

    private CraftlatroPaths() {
    }

    /** {@code config/craftlatro.properties}, in the instance's config directory like Cloth Config uses. */
    public static Path configFile() {
        Path target = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        migrateLegacyFile(target, "config");
        return target;
    }

    /** {@code config/craftlatro/saves}, where save exports, import staging and backups live. */
    public static Path savesDirectory() {
        Path target = FabricLoader.getInstance().getConfigDir()
                .resolve(MOD_DIRECTORY_NAME)
                .resolve(SAVES_DIRECTORY_NAME);
        migrateLegacyDirectory(target);
        return target;
    }

    /** {@code <instance>/craftlatro/game}, the extracted game package and the player runtime. */
    public static Path gameDirectory() {
        Path target = FabricLoader.getInstance().getGameDir()
                .resolve(MOD_DIRECTORY_NAME)
                .resolve(GAME_DIRECTORY_NAME);
        migrateLegacyDirectory(target);
        return target;
    }

    private static Path legacyDirectory() {
        String userHome = System.getProperty("user.home");
        return userHome == null ? null : Path.of(userHome, LEGACY_DIRECTORY_NAME);
    }

    private static void migrateLegacyFile(Path target, String legacyParent) {
        Path legacy = legacyDirectory();
        if (legacy == null || Files.exists(target)) {
            return;
        }
        Path source = legacy.resolve(legacyParent).resolve(CONFIG_FILE_NAME);
        if (!Files.isRegularFile(source)) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
            LOGGER.info("Migrated config from {} to {}", source, target);
        } catch (IOException failure) {
            LOGGER.warn("Could not migrate config from {} to {}", source, target, failure);
        }
    }

    private static void migrateLegacyDirectory(Path target) {
        Path legacy = legacyDirectory();
        if (legacy == null || hasContent(target)) {
            return;
        }
        // The legacy layout mirrors the new one one level down, so saves stays saves and game stays game.
        Path source = legacy.resolve(target.getFileName().toString());
        if (!hasContent(source)) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target);
            LOGGER.info("Migrated {} from {} to {}", target.getFileName(), source, target);
        } catch (IOException failure) {
            LOGGER.warn("Could not migrate {} from {} to {}; it will be rebuilt",
                    target.getFileName(), source, target, failure);
        }
    }

    private static boolean hasContent(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.findFirst().isPresent();
        } catch (IOException failure) {
            return false;
        }
    }
}
