package com.example.livestatus.web;

import com.example.livestatus.LiveStatusPlugin;
import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.history.LiveHistoryTracker;
import com.example.livestatus.premium.PaymentWebhookHandler;
import com.example.livestatus.premium.PremiumService;
import com.example.livestatus.tracker.ServerLinkRecord;
import com.example.livestatus.tracker.ServerLinkTracker;
import com.example.livestatus.web.api.RestApiHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Embedded HTTP/HTTPS server hosting the LiveStatus Web Control Panel,
 * REST API, and public server referral link endpoints directly from the Paper server.
 */
public class WebServerManager {

    private final LiveStatusPlugin plugin;
    private final PluginConfig config;
    private final WebSecurityManager securityManager;
    private final LiveHistoryTracker historyTracker;
    private final ServerLinkTracker linkTracker;
    private final PremiumService premiumService;
    private final PaymentWebhookHandler webhookHandler;
    private final AuditLogger auditLogger;
    private final Logger logger;

    private HttpServer server;

    public WebServerManager(
            LiveStatusPlugin plugin,
            PluginConfig config,
            WebSecurityManager securityManager,
            LiveHistoryTracker historyTracker,
            ServerLinkTracker linkTracker,
            PremiumService premiumService,
            PaymentWebhookHandler webhookHandler,
            AuditLogger auditLogger,
            Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.securityManager = securityManager;
        this.historyTracker = historyTracker;
        this.linkTracker = linkTracker;
        this.premiumService = premiumService;
        this.webhookHandler = webhookHandler;
        this.auditLogger = auditLogger;
        this.logger = logger;
    }

    public synchronized void start() {
        if (!config.isWebEnabled()) {
            logger.info("[WebPanel] Web control panel is disabled in config.yml.");
            return;
        }

        try {
            InetSocketAddress address = new InetSocketAddress(config.getWebHost(), config.getWebPort());

            if (config.isWebSslEnabled()) {
                SSLContext sslContext = createSslContext(config.getWebSslKeystorePath(), config.getWebSslKeystorePassword());
                if (sslContext != null) {
                    HttpsServer httpsServer = HttpsServer.create(address, 0);
                    httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                        @Override
                        public void configure(HttpsParameters params) {
                            try {
                                params.setSSLParameters(sslContext.getDefaultSSLParameters());
                            } catch (Exception e) {
                                logger.warning("[WebPanel] Could not configure SSL parameters: " + e.getMessage());
                            }
                        }
                    });
                    this.server = httpsServer;
                    logger.info("[WebPanel] HTTPS mode enabled.");
                } else {
                    logger.warning("[WebPanel] SSL failed to initialize. Falling back to HTTP mode.");
                    this.server = HttpServer.create(address, 0);
                }
            } else {
                this.server = HttpServer.create(address, 0);
            }

            this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // 1. Dashboard panel root handler (/panel/...)
            this.server.createContext("/panel", new PanelHandler());

            // 2. Static CSS / JS assets
            this.server.createContext("/static", new StaticAssetHandler());

            // 3. REST API layer (/api/v1/...)
            RestApiHandler apiHandler = new RestApiHandler(
                    plugin, securityManager, historyTracker, linkTracker, premiumService, webhookHandler, auditLogger, logger
            );
            this.server.createContext("/api/v1", apiHandler);

            // 4. Server link referral tracker (/join/... or /r/...)
            this.server.createContext("/join", new ServerLinkRedirectHandler());
            this.server.createContext("/r", new ServerLinkRedirectHandler());

            // 5. Default root handler
            this.server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/") || path.isBlank()) {
                    String html = "<html><body style='font-family:sans-serif;text-align:center;padding:50px;background:#1e1e24;color:#eee;'>"
                            + "<h2>LiveStatus Web Server Online</h2>"
                            + "<p>To access the administrative control panel, run <code style='color:#f39c12;'>/ytlive panel</code> in Minecraft.</p>"
                            + "</body></html>";
                    sendResponse(exchange, 200, "text/html; charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
                } else {
                    sendResponse(exchange, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
                }
            });

            this.server.start();
            String proto = config.isWebSslEnabled() ? "https" : "http";
            logger.info("[WebPanel] Web control panel online at " + proto + "://" + config.getWebHost() + ":" + config.getWebPort() + "/");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[WebPanel] Failed to start web control panel on port " + config.getWebPort() + ": " + e.getMessage());
            logger.warning("[WebPanel] Ensure the port is open and not in use by another service. Core plugin remains online.");
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            logger.info("[WebPanel] Web control panel stopped.");
        }
    }

    private SSLContext createSslContext(String keystorePath, String password) {
        if (keystorePath == null || keystorePath.isBlank()) {
            return null;
        }
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, password.toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password.toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            return sslContext;
        } catch (Exception e) {
            logger.warning("[WebPanel] Error loading SSL keystore from " + keystorePath + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Serves the Single Page Application admin dashboard for valid tokens,
     * or a clean expiration message if the token is invalid/expired.
     */
    private class PanelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath(); // e.g. /panel/<token>
            String token = "";
            if (path.startsWith("/panel/")) {
                token = path.substring("/panel/".length()).trim();
            } else if (path.equals("/panel")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("token=")) {
                    token = query.substring(6).trim();
                }
            }

            if (!securityManager.isValidSession(token)) {
                // Return clean, informative "Link Expired" page as required by specification
                String expiredHtml = """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>Panel Link Expired - LiveStatus</title>
                            <style>
                                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f1117; color: #e1e4ea; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                                .card { background: #1a1d26; border: 1px solid #2a2e3d; border-radius: 8px; padding: 40px; max-width: 480px; text-align: center; box-shadow: 0 4px 20px rgba(0,0,0,0.5); }
                                h1 { font-size: 22px; color: #ff5555; margin-bottom: 12px; }
                                p { font-size: 14px; line-height: 1.6; color: #a0a5b5; margin-bottom: 24px; }
                                code { background: #262b3a; color: #f1c40f; padding: 4px 10px; border-radius: 4px; font-weight: bold; font-size: 15px; }
                                .badge { display: inline-block; background: #2f1d24; color: #ff6b6b; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: bold; margin-bottom: 16px; }
                            </style>
                        </head>
                        <body>
                            <div class="card">
                                <div class="badge">ACCESS EXPIRED</div>
                                <h1>Panel Link Has Expired</h1>
                                <p>This temporary administrative link or session is no longer active.<br><br>
                                To generate a new secure control panel session, run the following command in-game or from server console:</p>
                                <p><code>/ytlive panel</code></p>
                            </div>
                        </body>
                        </html>
                        """;
                sendResponse(exchange, 401, "text/html; charset=UTF-8", expiredHtml.getBytes(StandardCharsets.UTF_8));
                return;
            }

            // Valid session: serve index.html with injected session token
            byte[] htmlBytes = loadResourceBytes("/web/index.html");
            if (htmlBytes == null) {
                sendResponse(exchange, 500, "text/plain", "Dashboard template not found.".getBytes(StandardCharsets.UTF_8));
                return;
            }

            String html = new String(htmlBytes, StandardCharsets.UTF_8);
            html = html.replace("{{SESSION_TOKEN}}", escapeHtml(token));
            sendResponse(exchange, 200, "text/html; charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Serves static assets (CSS, JS).
     */
    private class StaticAssetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("style.css")) {
                byte[] css = loadResourceBytes("/web/style.css");
                if (css != null) {
                    sendResponse(exchange, 200, "text/css; charset=UTF-8", css);
                    return;
                }
            } else if (path.endsWith("app.js")) {
                byte[] js = loadResourceBytes("/web/app.js");
                if (js != null) {
                    sendResponse(exchange, 200, "application/javascript; charset=UTF-8", js);
                    return;
                }
            }
            sendResponse(exchange, 404, "text/plain", "Asset not found".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Public server referral redirect: /join/<linkId>
     */
    private class ServerLinkRedirectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String linkId = "default";
            if (path.startsWith("/join/")) {
                linkId = path.substring("/join/".length()).trim();
            } else if (path.startsWith("/r/")) {
                linkId = path.substring("/r/".length()).trim();
            }

            linkTracker.recordClick(linkId);
            Optional<ServerLinkRecord> opt = linkTracker.getLink(linkId);
            String target = opt.map(ServerLinkRecord::targetAddress).orElse("play.example.com");

            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Join Minecraft Server</title>
                        <style>
                            body { font-family: sans-serif; background: #12141c; color: #fff; text-align: center; padding: 60px; }
                            .box { background: #1d212c; border-radius: 8px; padding: 40px; display: inline-block; max-width: 450px; }
                            .ip { font-size: 20px; font-weight: bold; color: #2ecc71; background: #262c3a; padding: 10px 20px; border-radius: 6px; display: inline-block; margin: 20px 0; }
                        </style>
                    </head>
                    <body>
                        <div class="box">
                            <h2>Connect to Server</h2>
                            <p>Copy the server address below and add it to your Minecraft client:</p>
                            <div class="ip">%s</div>
                            <p style="color:#888; font-size:12px;">Referral tracked: %s</p>
                        </div>
                    </body>
                    </html>
                    """.formatted(escapeHtml(target), escapeHtml(linkId));

            sendResponse(exchange, 200, "text/html; charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
        }
    }

    private byte[] loadResourceBytes(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
