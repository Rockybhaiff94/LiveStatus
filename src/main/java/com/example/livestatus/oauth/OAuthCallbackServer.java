package com.example.livestatus.oauth;

import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeApiClient;
import com.example.livestatus.youtube.YouTubeService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Embedded HTTP server running within the Paper plugin to handle the Google OAuth 2.0 callback directly.
 */
public class OAuthCallbackServer {

    private final Plugin plugin;
    private final PluginConfig config;
    private final OAuthStateManager stateManager;
    private final GoogleOAuthClient oAuthClient;
    private final YouTubeApiClient youTubeApiClient;
    private final AccountStorage accountStorage;
    private final PlayerLinkService linkService;
    private final YouTubeService youTubeService;
    private final Logger logger;

    private HttpServer server;

    public OAuthCallbackServer(
            Plugin plugin,
            PluginConfig config,
            OAuthStateManager stateManager,
            GoogleOAuthClient oAuthClient,
            YouTubeApiClient youTubeApiClient,
            AccountStorage accountStorage,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.stateManager = stateManager;
        this.oAuthClient = oAuthClient;
        this.youTubeApiClient = youTubeApiClient;
        this.accountStorage = accountStorage;
        this.linkService = linkService;
        this.youTubeService = youTubeService;
        this.logger = logger;
    }

    public synchronized void start() {
        if (!config.isOAuthConfigured()) {
            logger.warning("YouTube OAuth credentials are not fully configured (client-id / client-secret). OAuth callback server will not start.");
            return;
        }

        try {
            InetSocketAddress address = new InetSocketAddress(config.getCallbackHost(), config.getCallbackPort());
            this.server = HttpServer.create(address, 0);
            this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            this.server.createContext("/oauth/callback", new CallbackHandler());
            this.server.start();

            logger.info("Embedded OAuth callback receiver started on " + config.getCallbackHost() + ":" + config.getCallbackPort() + "/oauth/callback");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start embedded OAuth callback server on port " + config.getCallbackPort() + ": " + e.getMessage());
            logger.warning("Please verify that port " + config.getCallbackPort() + " is available and not in use by another application.");
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            logger.info("Embedded OAuth callback receiver stopped.");
        }
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendHtml(exchange, 405, renderErrorHtml("Method Not Allowed", "Please follow the authorization link provided in Minecraft."));
                return;
            }

            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String code = queryParams.get("code");
            String state = queryParams.get("state");
            String error = queryParams.get("error");

            if (error != null) {
                sendHtml(exchange, 400, renderErrorHtml("Access Denied", "Authorization was denied or cancelled (" + error + ")."));
                return;
            }

            if (code == null || state == null) {
                sendHtml(exchange, 400, renderErrorHtml("Missing Parameters", "The authorization request did not contain valid callback parameters."));
                return;
            }

            // 1. Validate and consume CSRF state token
            OAuthStateManager.StatePayload payload = stateManager.validateAndConsume(state);
            if (payload == null) {
                sendHtml(exchange, 400, renderErrorHtml("Invalid or Expired Link", "This authorization link is invalid or has expired (links expire after 5 minutes). Please run <code>/link youtube</code> in Minecraft again."));
                return;
            }

            // 2. Exchange authorization code for tokens
            GoogleOAuthClient.TokenResponse tokens = oAuthClient.exchangeCode(code);
            if (tokens == null || tokens.accessToken() == null) {
                sendHtml(exchange, 500, renderErrorHtml("Token Exchange Failed", "Failed to exchange authorization code with Google. Please try again."));
                return;
            }

            // 3. Query YouTube channel details
            YouTubeApiClient.ChannelInfo channel = youTubeApiClient.getMyChannel(tokens.accessToken());
            if (channel == null) {
                sendHtml(exchange, 404, renderErrorHtml("No YouTube Channel Found", "The Google account you authorized does not have an active YouTube channel."));
                return;
            }

            // 4. Store credentials securely
            long now = System.currentTimeMillis();
            AccountRecord record = new AccountRecord(
                    payload.playerUuid(),
                    payload.playerName(),
                    channel.channelId(),
                    channel.title(),
                    channel.customUrl(),
                    tokens.accessToken(),
                    tokens.refreshToken(),
                    now + tokens.expiresInMs(),
                    now,
                    now,
                    "LINKED"
            );

            accountStorage.saveOrUpdate(record);
            youTubeService.invalidateCache(payload.playerUuid());

            YouTubeAccount ytAccount = new YouTubeAccount(channel.channelId(), channel.title(), channel.customUrl());
            linkService.updateAccount(payload.playerUuid(), true, ytAccount);

            logger.info("Successfully linked Minecraft player " + payload.playerName() + " (" + payload.playerUuid() + ") to YouTube channel " + channel.title() + " (" + channel.channelId() + ")");

            // 5. Notify player in Minecraft on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player onlinePlayer = Bukkit.getPlayer(payload.playerUuid());
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    onlinePlayer.sendMessage(Component.text("[LiveStatus] ", NamedTextColor.RED, TextDecoration.BOLD)
                            .append(Component.text("Your YouTube account ", NamedTextColor.GREEN))
                            .append(Component.text(channel.title(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                            .append(Component.text(" has been linked successfully!", NamedTextColor.GREEN)));
                    onlinePlayer.sendMessage(Component.text("Whenever you stream Minecraft live on YouTube, your in-game nametag will display ", NamedTextColor.GRAY)
                            .append(Component.text("LIVE", NamedTextColor.RED, TextDecoration.BOLD)));

                    // Trigger immediate live check for the player
                    youTubeService.checkStatus(payload.playerUuid());
                }
            });

            sendHtml(exchange, 200, renderSuccessHtml(payload.playerName(), channel.title()));
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query == null || query.isBlank()) {
                return params;
            }
            for (String pair : query.split("&")) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, val);
                }
            }
            return params;
        }

        private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String renderSuccessHtml(String playerName, String channelTitle) {
            return """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>YouTube Account Linked</title>
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                            .card { background: #1e293b; padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); text-align: center; max-width: 480px; border: 1px solid #334155; }
                            .badge { background: #ef4444; color: white; font-weight: bold; padding: 4px 12px; border-radius: 6px; font-size: 14px; display: inline-block; margin-bottom: 16px; }
                            h1 { margin: 0 0 16px; font-size: 24px; color: #38bdf8; }
                            p { color: #94a3b8; line-height: 1.6; margin-bottom: 24px; }
                            .channel { background: #0f172a; padding: 12px; border-radius: 8px; font-family: monospace; color: #22c55e; border: 1px solid #334155; margin-bottom: 24px; }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <div class="badge">LiveStatus</div>
                            <h1>Account Linked!</h1>
                            <p>Your YouTube channel has been linked to your Minecraft account <strong>""" + playerName + """
                            </strong>.</p>
                            <div class="channel">Channel: """ + channelTitle + """
                            </div>
                            <p>You may now return to Minecraft. Whenever you stream Minecraft live on YouTube, your in-game nametag will display <strong>LIVE</strong>!</p>
                        </div>
                    </body>
                    </html>
                    """;
        }

        private String renderErrorHtml(String title, String message) {
            return """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Link Failed</title>
                        <style>
                            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                            .card { background: #1e293b; padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); text-align: center; max-width: 480px; border: 1px solid #ef4444; }
                            h1 { margin: 0 0 16px; font-size: 24px; color: #ef4444; }
                            p { color: #94a3b8; line-height: 1.6; margin-bottom: 24px; }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <h1>""" + title + """
                            </h1>
                            <p>""" + message + """
                            </p>
                        </div>
                    </body>
                    </html>
                    """;
        }
    }
}

