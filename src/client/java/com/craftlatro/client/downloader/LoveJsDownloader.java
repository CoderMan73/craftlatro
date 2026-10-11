package com.craftlatro.client.downloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Downloads the love.js web player files from the 2dengine/love.js GitHub repository.
 *
 * <p>Source: https://github.com/2dengine/love.js (MIT License)
 * Attribution: LÖVE Development Team (zLib License), 2dengine LLC (MIT License),
 * David Khachaturov (MIT License), Tanner Rogalsky (MIT License), Luke Haas (MIT License),
 * Marcelo Silva Nascimento Mancini (MIT License), Hamdy Elzanqali (MIT License).
 *
 * <p>Files downloaded:
 * - player.js (root loader script)
 * - 11.5/love.js (compiled LÖVE engine JavaScript glue)
 * - 11.5/love.wasm (compiled LÖVE engine WebAssembly binary)
 * - 11.5/license.txt (LÖVE license)
 */
public class LoveJsDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");

    private static final String BASE_URL = "https://raw.githubusercontent.com/2dengine/love.js/main/";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, String> FILES = new LinkedHashMap<>();
    static {
        FILES.put("player.js", "player.js");
        FILES.put("style.css", "style.css");
        FILES.put("11.5/love.js", "11.5/love.js");
        FILES.put("11.5/love.wasm", "11.5/love.wasm");
        FILES.put("11.5/license.txt", "11.5/license.txt");
        FILES.put("lua/normalize1.lua", "lua/normalize1.lua");
        FILES.put("lua/normalize2.lua", "lua/normalize2.lua");
    }

    public static boolean downloadAll(Path targetDir) {
        for (Map.Entry<String, String> entry : FILES.entrySet()) {
            String remotePath = entry.getKey();
            String localName = entry.getValue();
            Path localPath = targetDir.resolve(localName);

            if (Files.exists(localPath) && safeFileSize(localPath) > 0) {
                LOGGER.info("Skipping {} (already exists)", localName);
                continue;
            }

            LOGGER.info("Downloading {} from 2dengine/love.js (MIT License)...", localName);
            if (!downloadFile(remotePath, localPath)) {
                LOGGER.error("Failed to download {}", localName);
                return false;
            }
            LOGGER.info("Downloaded {} ({} bytes)", localName, safeFileSize(localPath));
        }

        return true;
    }

    private static long safeFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    public static boolean downloadFile(String remotePath, Path localPath) {
        try {
            Files.createDirectories(localPath.getParent());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + remotePath))
                    .header("User-Agent", "craftlatro-mod/1.0")
                    .GET()
                    .build();

            HttpResponse<Path> response = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofFile(localPath));

            if (response.statusCode() != 200) {
                LOGGER.error("HTTP {} downloading {}", response.statusCode(), remotePath);
                Files.deleteIfExists(localPath);
                return false;
            }

            return true;
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error downloading {}: {}", remotePath, e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static boolean isPlayerPresent(Path targetDir) {
        for (String localName : FILES.values()) {
            Path file = targetDir.resolve(localName);
            if (!Files.exists(file) || safeFileSize(file) == 0) {
                return false;
            }
        }
        return true;
    }
}
