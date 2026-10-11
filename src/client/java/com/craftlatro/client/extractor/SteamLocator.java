package com.craftlatro.client.extractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SteamLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");

    private static final String[] STEAM_PATHS = {
            "C:\\Program Files (x86)\\Steam",
            "C:\\Program Files\\Steam",
    };

    private static final String[] STEAM_LIBRARY_PATHS = {
            "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Balatro",
            "C:\\Program Files\\Steam\\steamapps\\common\\Balatro",
    };

    public static Path findBalatroExe() {
        // Check common Steam default paths
        for (String path : STEAM_LIBRARY_PATHS) {
            Path candidate = Paths.get(path, "Balatro.exe");
            if (Files.exists(candidate)) {
                LOGGER.info("Found Balatro.exe at Steam default path: {}", candidate);
                return candidate;
            }
        }

        // Parse Steam libraries from library folders metadata
        for (String steamPath : STEAM_PATHS) {
            File steamDir = new File(steamPath);
            if (!steamDir.exists()) continue;

            Path libraryFile = Paths.get(steamPath, "steamapps", "libraryfolders.vdf");
            if (Files.exists(libraryFile)) {
                Path found = parseLibraryFolders(libraryFile);
                if (found != null) {
                    return found;
                }
            }
        }

        // Check AppData (some installations)
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            Path appDataPath = Paths.get(appData, "Balatro", "Balatro.exe");
            if (Files.exists(appDataPath)) {
                LOGGER.info("Found Balatro.exe at AppData: {}", appDataPath);
                return appDataPath;
            }
        }

        LOGGER.warn("Could not auto-detect Balatro.exe location");
        return null;
    }

    private static Path parseLibraryFolders(Path vdfFile) {
        try {
            String content = Files.readString(vdfFile);
            // Simple parse of libraryfolders.vdf to find Steam library paths
            // Format: "path"		"D:\\SteamLibrary"
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.contains("\"path\"")) {
                    String[] parts = line.split("\"");
                    if (parts.length >= 4) {
                        String libraryPath = parts[3];
                        if (!libraryPath.isEmpty()) {
                            Path candidate = Paths.get(libraryPath, "steamapps", "common", "Balatro", "Balatro.exe");
                            if (Files.exists(candidate)) {
                                LOGGER.info("Found Balatro.exe at Steam library: {}", candidate);
                                return candidate;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to parse libraryfolders.vdf", e);
        }
        return null;
    }
}
