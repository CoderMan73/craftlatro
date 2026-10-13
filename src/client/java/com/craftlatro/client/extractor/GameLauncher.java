package com.craftlatro.client.extractor;

import com.craftlatro.client.CraftlatroPaths;
import com.craftlatro.client.browser.BrowserScreen;
import com.craftlatro.client.config.CraftlatroConfig;
import com.craftlatro.client.downloader.LoveJsDownloader;
import com.craftlatro.client.server.LocalHttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class GameLauncher {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");

    private static LocalHttpServer httpServer = null;
    private static volatile boolean extractionInProgress = false;

    public static boolean isServerRunning() {
        return httpServer != null && httpServer.isRunning();
    }

    public static boolean isExtractionInProgress() {
        return extractionInProgress;
    }

    public static void shutdown() {
        if (httpServer != null) {
            httpServer.stop();
            LOGGER.info("HTTP server stopped");
        }
    }

    public static String getBaseUrl() {
        return httpServer != null ? httpServer.getBaseUrl() : "http://localhost:18080/";
    }

    public static int getPort() {
        if (httpServer != null && httpServer.isRunning()) {
            return httpServer.getPort();
        }
        return LocalHttpServer.DEFAULT_PORT;
    }

    public static boolean isReady() {
        return isServerRunning();
    }

    /**
     * The {@code ?g=} cache key the served page currently points at: the mtime of the extracted
     * {@code game.love}, or {@code "0"} when there is none. love.js keys its IndexedDB package cache
     * on that value, so it is also what a save export records and an import compares against.
     */
    public static String currentGameVersion() {
        Path gameDir = getGameDirectory();
        return gameDir == null ? "0" : loveVersion(gameDir);
    }

    public static void tryAutoLoad(Minecraft client) {
        if (isReady()) return;
        if (extractionInProgress) return;

        Path gameDir = getGameDirectory();
        if (gameDir == null) return;

        Path loveFile = gameDir.resolve("game.love");

        if (Files.exists(loveFile) && LoveJsDownloader.isPlayerPresent(gameDir) && Files.exists(gameDir.resolve("index.html"))) {
            LOGGER.info("Game data exists, auto-starting local HTTP server");
            extractionInProgress = true;
            sendChatInfo(client, "Starting local server...");
            CompletableFuture.runAsync(() -> {
                ensureIndexHtml(gameDir);
                Minecraft.getInstance().execute(() -> {
                    if (httpServer != null && httpServer.isRunning()) {
                        httpServer.stop();
                    }
                    httpServer = new LocalHttpServer(gameDir);
                    if (!httpServer.start()) {
                        sendChatError("Failed to start local HTTP server");
                        extractionInProgress = false;
                        return;
                    }
                    String url = httpServer.getBaseUrl();
                    LOGGER.info("Game available at {}", url);
                    sendChatInfo(client, "Ready! Press B or use /craftlatro open to launch.");
                    extractionInProgress = false;
                });
            });
        }
    }

    public static void promptForBalatroExe(Minecraft client) {
        sendChatInfo(client, "Searching for Balatro.exe...");

        Path autoDetected = SteamLocator.findBalatroExe();
        if (autoDetected != null) {
            sendChatInfo(client, "Auto-detected Balatro.exe at: " + autoDetected);
            handleSelectedFile(autoDetected, client);
            return;
        }

        sendChatInfo(client, "Auto-detect failed. Trying file picker...");

        if (isSwingAvailable()) {
            openSwingFilePicker(client);
        } else {
            openTextPathPrompt(client);
        }
    }

    private static boolean isSwingAvailable() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                System.setProperty("java.awt.headless", "false");
            }
            return !GraphicsEnvironment.isHeadless();
        } catch (Exception e) {
            return false;
        }
    }

    private static void openSwingFilePicker(Minecraft client) {
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Select Balatro.exe");
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                FileNameExtensionFilter filter = new FileNameExtensionFilter("Executable (*.exe)", "exe");
                fileChooser.setFileFilter(filter);

                String appData = System.getenv("APPDATA");
                if (appData != null) {
                    File balatroDir = new File(appData, "Balatro");
                    if (balatroDir.exists()) {
                        fileChooser.setCurrentDirectory(balatroDir);
                    }
                }

                int result = fileChooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selected = fileChooser.getSelectedFile();
                    if (selected != null) {
                        Minecraft.getInstance().execute(() -> handleSelectedFile(selected.toPath(), client));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Swing file picker failed, falling back to text input", e);
                Minecraft.getInstance().execute(() -> openTextPathPrompt(client));
            }
        });
    }

    private static void openTextPathPrompt(Minecraft client) {
        sendChatInfo(client, "Enter: /craftlatro load <path-to-Balatro.exe>");
    }

    public static void loadGame(String balatroExePath, Minecraft client) {
        Path exePath = Path.of(balatroExePath);
        if (!Files.exists(exePath)) {
            sendChatError("File not found: " + balatroExePath);
            return;
        }
        if (!exePath.getFileName().toString().toLowerCase().endsWith(".exe")) {
            sendChatError("Selected file is not an .exe file");
            return;
        }
        handleSelectedFile(exePath, client);
    }

    private static void handleSelectedFile(Path balatroExe, Minecraft client) {
        LOGGER.info("Selected Balatro.exe: {}", balatroExe);

        CraftlatroConfig.getInstance().setBalatroExePath(balatroExe.toString());

        if (extractionInProgress) {
            sendChatError("Already loading a game. Please wait.");
            return;
        }
        extractionInProgress = true;

        sendChatInfo(client, "Extracting game data from Balatro.exe...");

        Path gameDir = getGameDirectory();
        if (gameDir == null) {
            sendChatError("Failed to create game directory");
            extractionInProgress = false;
            return;
        }

        Path loveFile = gameDir.resolve("game.love");

        CompletableFuture.runAsync(() -> {
            // Step 1: Extract .love from Balatro.exe
            boolean extracted = LoveExtractor.extractLoveFromExe(balatroExe, loveFile);
            if (!extracted) {
                sendChatError("Failed to extract game data. See logs for details.");
                extractionInProgress = false;
                return;
            }

            // Step 1b: Patch the .love archive for love.js compatibility
            // love.js uses Lua 5.1 (no bit module, no threads, different math)
            // We inject compatibility shims (bit.lua, web_patches.lua, nativefs.lua, lovely.lua)
            // and patch game files (main.lua, globals.lua, hologram.fs) directly into the archive.
            // See ai-docs/notes/lovejs-patching.md for details.
            sendChatInfo(client, "Applying love.js compatibility patches...");
            if (!LoveExtractor.patchLoveArchive(loveFile)) {
                sendChatError("Warning: Failed to patch .love file. Game may not load correctly.");
                LOGGER.warn("Failed to patch .love file, game may fail to start");
            }

            sendChatInfo(client, "Game data extracted. Downloading love.js web player...");

            // Step 2: Download love.js player (only if missing)
            if (!LoveJsDownloader.downloadAll(gameDir)) {
                sendChatError("Failed to download love.js player. Check internet connection, " +
                        "or download manually from github.com/2dengine/love.js");
                extractionInProgress = false;
                return;
            }

            // Step 3: Ensure index.html exists
            ensureIndexHtml(gameDir);

            sendChatInfo(client, "Starting local server...");

            // Step 4: Start HTTP server and open browser (must be on render thread)
            Minecraft.getInstance().execute(() -> {
                if (httpServer != null && httpServer.isRunning()) {
                    httpServer.stop();
                }
                httpServer = new LocalHttpServer(gameDir);
                if (!httpServer.start()) {
                    sendChatError("Failed to start local HTTP server");
                    extractionInProgress = false;
                    return;
                }

                String url = httpServer.getBaseUrl();
                LOGGER.info("Game available at {}", url);

                sendChatInfo(client, "Ready! Press B or use /craftlatro open to launch.");
                extractionInProgress = false;

                Screen currentScreen = Minecraft.getInstance().gui.screen();
                if (currentScreen instanceof BrowserScreen browserScreen) {
                    Minecraft.getInstance().execute(browserScreen::rebind);
                }
            });
        });
    }

    private static void ensureIndexHtml(Path gameDir) {
        File indexFile = gameDir.resolve("index.html").toFile();
        // love.js caches fetched packages in IndexedDB keyed by the ?g= value,
        // so embed the .love's last-modified time as a version prefix. A
        // regenerated game.love gets a new key, forcing a fresh fetch instead
        // of serving the stale cached copy. LocalHttpServer maps
        // "<version>/game.love" back to game.love on disk.
        String version = loveVersion(gameDir);
        String html = """
                    <!doctype html>
                    <html lang="en-us">
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                        <title>Balatro (love.js)</title>
                        <link rel="stylesheet" href="style.css">
                    </head>
                    <body>
                        <canvas id="canvas"></canvas>
                        <div id="spinner" class="pending"></div>
                        <script src="player.js?g=%s/game.love"></script>
                    </body>
                    </html>
                    """.formatted(version);
        try {
            Files.writeString(indexFile.toPath(), html);
            LOGGER.info("Wrote index.html (game.love version={}) at {}", version, indexFile.toPath());
        } catch (Exception e) {
            LOGGER.error("Failed to write index.html", e);
        }
    }

    private static String loveVersion(Path gameDir) {
        Path loveFile = gameDir.resolve("game.love");
        if (Files.exists(loveFile)) {
            try {
                return String.valueOf(Files.getLastModifiedTime(loveFile).toMillis());
            } catch (IOException e) {
                LOGGER.warn("Could not read game.love mtime for cache busting", e);
            }
        }
        return "0";
    }

    private static Path getGameDirectory() {
        Path gameDir = CraftlatroPaths.gameDirectory();
        try {
            Files.createDirectories(gameDir);
            return gameDir;
        } catch (Exception e) {
            LOGGER.error("Failed to create game directory", e);
            return null;
        }
    }

    private static void sendChatError(String message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal("§c" + message));
            }
        });
    }

    private static void sendChatInfo(Minecraft client, String message) {
        Minecraft.getInstance().execute(() -> {
            if (client != null && client.player != null) {
                client.player.sendSystemMessage(
                        Component.literal("§e" + message));
            }
        });
    }
}
