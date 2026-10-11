package com.craftlatro.client.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");
    public static final int DEFAULT_PORT = 18080;
    private static final int MAX_PORT_ATTEMPTS = 10;

    private HttpServer server;
    private final Path serveDirectory;
    private int port;
    private boolean running;
    private ExecutorService executor;

    public LocalHttpServer(Path serveDirectory) {
        this.serveDirectory = serveDirectory.normalize().toAbsolutePath();
    }

    public boolean start() {
        return start(DEFAULT_PORT);
    }

    public boolean start(int port) {
        if (running) {
            LOGGER.warn("LocalHttpServer is already running");
            return false;
        }

        for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
            int candidatePort = port + attempt;
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", candidatePort), 0);
                server.createContext("/", new CoopCoepFileHandler(serveDirectory));
                ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "craftlatro-http-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        server.setExecutor(Executors.newFixedThreadPool(8, threadFactory));
                server.start();
                running = true;
                this.port = candidatePort;
                LOGGER.info("Local HTTP server started on port {}", candidatePort);
                return true;
            } catch (IOException e) {
                LOGGER.warn("Port {} unavailable, trying next...", candidatePort);
            }
        }

        LOGGER.error("Failed to start local HTTP server on ports {}-{}", port, port + MAX_PORT_ATTEMPTS - 1);
        return false;
    }

    public void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            LOGGER.info("Local HTTP server stopped");
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public String getBaseUrl() {
        return "http://localhost:" + port + "/";
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    private static class CoopCoepFileHandler implements HttpHandler {

        private final Path rootDir;

        public CoopCoepFileHandler(Path rootDir) {
            this.rootDir = rootDir.normalize().toAbsolutePath();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.isEmpty() || path.endsWith("/")) {
                path = path + "index.html";
            }

            // Normalize path to prevent directory traversal
            Path filePath = rootDir.resolve(path).normalize().toAbsolutePath();
            if (!filePath.startsWith(rootDir)) {
                sendError(exchange, 403, "Forbidden");
                return;
            }

            if (Files.notExists(filePath) || Files.isDirectory(filePath)) {
                // Try appending .html
                Path htmlFile = filePath.resolveSibling(filePath.getFileName() + ".html").normalize().toAbsolutePath();
                if (!htmlFile.startsWith(rootDir)) {
                    sendError(exchange, 404, "Not Found");
                    return;
                }
                if (Files.exists(htmlFile)) {
                    filePath = htmlFile;
                } else {
                    sendError(exchange, 404, "Not Found");
                    return;
                }
            }

            byte[] content = Files.readAllBytes(filePath);
            String mimeType = probeContentType(filePath);

            // Set COOP/COEP headers required for love.js WebAssembly/SharedArrayBuffer
            exchange.getResponseHeaders().set("Cross-Origin-Opener-Policy", "same-origin");
            exchange.getResponseHeaders().set("Cross-Origin-Embedder-Policy", "require-corp");
            exchange.getResponseHeaders().set("Cross-Origin-Resource-Policy", "cross-origin");
            exchange.getResponseHeaders().set("Content-Type", mimeType);

            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }

        private String probeContentType(Path file) {
            String fileName = file.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".wasm")) {
                return "application/wasm";
            }
            if (fileName.endsWith(".js")) {
                return "application/javascript";
            }
            if (fileName.endsWith(".css")) {
                return "text/css";
            }
            if (fileName.endsWith(".html")) {
                return "text/html";
            }
            if (fileName.endsWith(".json")) {
                return "application/json";
            }
            if (fileName.endsWith(".lua")) {
                return "text/plain";
            }
            try {
                String type = Files.probeContentType(file);
                return type != null ? type : "application/octet-stream";
            } catch (IOException e) {
                return "application/octet-stream";
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            exchange.getResponseHeaders().set("Cross-Origin-Opener-Policy", "same-origin");
            exchange.getResponseHeaders().set("Cross-Origin-Embedder-Policy", "require-corp");
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(code, message.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(message.getBytes());
            }
        }
    }
}
