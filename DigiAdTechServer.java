package com.Webpage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

/**
 * DigiAdTechMediaTN Secure HTTPS & HTTP Web Server
 * Zero-dependency native Java TLS/SSL HTTP server.
 * Serves the responsive website over HTTPS and handles lead inquiries.
 */
public class DigiAdTechServer {

    private static final int DEFAULT_HTTPS_PORT = 8443;
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final String DEFAULT_KEYSTORE_PASSWORD = "changeit";
    private static final String KEYSTORE_FILE_NAME = "keystore.p12";

    private static Path staticDir;
    private static final Path LEADS_FILE = Paths.get("leads.jsonl");

    public static void main(String[] args) {
        try {
            int httpsPort = DEFAULT_HTTPS_PORT;
            int httpPort = DEFAULT_HTTP_PORT;
            String keystorePassword = DEFAULT_KEYSTORE_PASSWORD;

            if (args.length > 0) {
                try { httpsPort = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
            }
            if (args.length > 1) {
                try { httpPort = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
            }
            if (args.length > 2) {
                keystorePassword = args[2];
            }

            // Locate static web directory (check public/ subfolder or current folder)
            Path currentDir = Paths.get("").toAbsolutePath();
            Path publicSubdir = currentDir.resolve("public");

            if (Files.exists(publicSubdir) && Files.isDirectory(publicSubdir)) {
                staticDir = publicSubdir;
            } else {
                staticDir = currentDir;
            }

            // Locate or auto-create SSL KeyStore
            Path keystorePath = currentDir.resolve(KEYSTORE_FILE_NAME);
            if (!Files.exists(keystorePath)) {
                ensureKeystoreExists(keystorePath, keystorePassword);
            }

            // Initialize SSLContext
            SSLContext sslContext = createSSLContext(keystorePath, keystorePassword);

            // 1. Create and configure Secure HTTPS Server
            HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(httpsPort), 0);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    try {
                        SSLContext context = getSSLContext();
                        SSLEngine engine = context.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());
                        SSLParameters defaultSSLParameters = context.getDefaultSSLParameters();
                        params.setSSLParameters(defaultSSLParameters);
                    } catch (Exception ex) {
                        System.err.println("Failed to configure HTTPS parameters: " + ex.getMessage());
                    }
                }
            });

            // Register HTTPS Handlers
            httpsServer.createContext("/api/contact", new ContactApiHandler());
            httpsServer.createContext("/api/health", new HealthCheckHandler("HTTPS"));
            httpsServer.createContext("/", new StaticFileHandler());
            httpsServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            httpsServer.start();

            // 2. Create HTTP Server for automatic redirection to HTTPS
            final int redirectPort = httpsPort;
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
            httpServer.createContext("/", exchange -> {
                String host = exchange.getRequestHeaders().getFirst("Host");
                if (host != null && host.contains(":")) {
                    host = host.substring(0, host.indexOf(":"));
                } else if (host == null || host.isBlank()) {
                    host = "localhost";
                }
                String targetUrl = "https://" + host + ":" + redirectPort + exchange.getRequestURI();
                exchange.getResponseHeaders().set("Location", targetUrl);
                exchange.getResponseHeaders().set("Connection", "close");
                exchange.sendResponseHeaders(301, -1);
                exchange.close();
            });
            httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            httpServer.start();

            System.out.println("=========================================================");
            System.out.println("   DigiAdTechMediaTN Secure HTTPS Server (Java " + System.getProperty("java.version") + ")");
            System.out.println("=========================================================");
            System.out.println(" 🔒 Secure Webpage:      https://localhost:" + httpsPort + "/");
            System.out.println(" 🔄 HTTP Auto-Redirect:  http://localhost:" + httpPort + "/ -> https://localhost:" + httpsPort + "/");
            System.out.println(" 🔑 SSL Keystore:        " + keystorePath.toAbsolutePath());
            System.out.println(" 📁 Serving Files From:  " + staticDir.toAbsolutePath());
            System.out.println(" 📡 API Endpoints:       POST /api/contact");
            System.out.println("                         GET  /api/health");
            System.out.println(" 📝 Leads Log:           " + LEADS_FILE.toAbsolutePath());
            System.out.println("=========================================================");
            System.out.println("Server running securely with TLS encryption... Press Ctrl+C to stop.");

        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads SSLContext from PKCS12 Keystore
     */
    private static SSLContext createSSLContext(Path keystorePath, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream fis = Files.newInputStream(keystorePath)) {
            ks.load(fis, password.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    /**
     * Automatically generates a local self-signed SSL keystore using Java's keytool if none exists
     */
    private static void ensureKeystoreExists(Path keystorePath, String password) {
        try {
            System.out.println("Generating self-signed SSL certificate in " + keystorePath.getFileName() + "...");
            ProcessBuilder pb = new ProcessBuilder(
                    "keytool",
                    "-genkeypair",
                    "-alias", "digiadtech",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-storetype", "PKCS12",
                    "-keystore", keystorePath.toString(),
                    "-storepass", password,
                    "-validity", "365",
                    "-dname", "CN=localhost, OU=DigiAdTechMediaTN, O=DigiAdTechMediaTN, L=Chennai, ST=Tamil Nadu, C=IN"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor();
            System.out.println("SSL Keystore created successfully.");
        } catch (Exception e) {
            System.err.println("Could not auto-generate keystore: " + e.getMessage());
        }
    }

    /**
     * Handler to serve static website files (Webpage.html, index.html, Styleforweb.CSS, Frontend.js, etc.)
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }

            String pathStr = exchange.getRequestURI().getPath();
            if (pathStr == null || pathStr.equals("/")) {
                // Check Webpage.html or index.html
                if (Files.exists(staticDir.resolve("Webpage.html"))) {
                    pathStr = "/Webpage.html";
                } else {
                    pathStr = "/index.html";
                }
            }

            Path targetFile = staticDir.resolve(pathStr.substring(1)).normalize();
            if (!targetFile.startsWith(staticDir.normalize())) {
                sendResponse(exchange, 403, "text/plain", "Forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }

            if (!Files.exists(targetFile) || Files.isDirectory(targetFile)) {
                // Fallback to Webpage.html or index.html
                Path fallbackWebpage = staticDir.resolve("Webpage.html");
                Path fallbackIndex = staticDir.resolve("index.html");

                if (Files.exists(fallbackWebpage)) {
                    targetFile = fallbackWebpage;
                } else if (Files.exists(fallbackIndex)) {
                    targetFile = fallbackIndex;
                } else {
                    sendResponse(exchange, 404, "text/plain", "404 Not Found".getBytes(StandardCharsets.UTF_8));
                    return;
                }
            }

            String contentType = determineContentType(targetFile.getFileName().toString());
            byte[] fileBytes = Files.readAllBytes(targetFile);

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, must-revalidate");
            exchange.sendResponseHeaders(200, fileBytes.length);

            if (!"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileBytes);
                }
            }
        }

        private String determineContentType(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".ico")) return "image/x-icon";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            return "application/octet-stream";
        }
    }

    /**
     * API Handler for receiving lead submissions
     */
    static class ContactApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            System.out.println("\n[SECURE LEAD RECEIVED at " + timestamp + "]");
            System.out.println(body);

            try {
                String logEntry = "{\"receivedAt\":\"" + timestamp + "\",\"protocol\":\"HTTPS\",\"data\":" + (body.trim().startsWith("{") ? body.trim() : "\"" + body + "\"") + "}\n";
                Files.writeString(LEADS_FILE, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception e) {
                System.err.println("Could not append to leads file: " + e.getMessage());
            }

            String jsonResponse = "{\"status\":\"success\",\"message\":\"Thank you! Your inquiry has been securely submitted. Our team will contact you shortly.\"}";
            sendResponse(exchange, 200, "application/json", jsonResponse.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Health check endpoint
     */
    static class HealthCheckHandler implements HttpHandler {
        private final String protocol;
        public HealthCheckHandler(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "{\"status\":\"UP\",\"service\":\"DigiAdTechMediaTN Secure Server\",\"protocol\":\""
                    + protocol + "\",\"javaVersion\":\"" + System.getProperty("java.version") + "\"}";
            sendResponse(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] responseBytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}