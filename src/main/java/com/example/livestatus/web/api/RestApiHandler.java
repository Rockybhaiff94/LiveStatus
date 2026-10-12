package com.example.livestatus.web.api;

import com.example.livestatus.LiveStatusPlugin;
import com.example.livestatus.chat.StreamerChatSettings;
import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.history.LiveHistoryTracker;
import com.example.livestatus.premium.PaymentWebhookHandler;
import com.example.livestatus.premium.PremiumRecord;
import com.example.livestatus.premium.PremiumService;
import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.storage.StreamerConfigStore;
import com.example.livestatus.tracker.ServerLinkTracker;
import com.example.livestatus.storage.GsonFactory;
import com.example.livestatus.web.AuditLogger;
import com.example.livestatus.web.PanelSession;
import com.example.livestatus.web.WebSecurityManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Dispatches all REST API requests under /api/v1/...
 * Enforces rate limiting, session authentication, input validation, and safe auditing.
 */
public class RestApiHandler implements HttpHandler {

    private final LiveStatusPlugin plugin;
    private final WebSecurityManager securityManager;
    private final LiveHistoryTracker historyTracker;
    private final ServerLinkTracker linkTracker;
    private final PremiumService premiumService;
    private final PaymentWebhookHandler webhookHandler;
    private final AuditLogger auditLogger;
    private final Logger logger;
    private final Gson gson = GsonFactory.createGson();

    public RestApiHandler(
            LiveStatusPlugin plugin,
            WebSecurityManager securityManager,
            LiveHistoryTracker historyTracker,
            ServerLinkTracker linkTracker,
            PremiumService premiumService,
            PaymentWebhookHandler webhookHandler,
            AuditLogger auditLogger,
            Logger logger
    ) {
        this.plugin = plugin;
        this.securityManager = securityManager;
        this.historyTracker = historyTracker;
        this.linkTracker = linkTracker;
        this.premiumService = premiumService;
        this.webhookHandler = webhookHandler;
        this.auditLogger = auditLogger;
        this.logger = logger;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

        // 1. IP Lockout check
        if (securityManager.isIpLockedOut(clientIp)) {
            sendJsonResponse(exchange, 403, Map.of("error", "Your IP is temporarily locked out due to multiple failed authentication attempts."));
            return;
        }

        // 2. Rate limit check
        if (securityManager.isRateLimited(clientIp)) {
            sendJsonResponse(exchange, 429, Map.of("error", "Too many requests. Please slow down."));
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);

        // Webhook endpoint does NOT require admin session token, it uses HMAC signature verification
        if (path.equals("/api/v1/payment/webhook") && "POST".equals(method)) {
            handlePaymentWebhook(exchange, clientIp);
            return;
        }

        // 3. Extract and validate session token
        String token = extractToken(exchange);
        if (!securityManager.isValidSession(token)) {
            securityManager.recordFailedAttempt(clientIp);
            sendJsonResponse(exchange, 401, Map.of("error", "Unauthorized: panel session expired or invalid."));
            return;
        }
        securityManager.recordSuccessfulAttempt(clientIp);
        PanelSession session = securityManager.getSession(token).orElse(null);
        String actor = session != null ? session.issuer() : "Admin";

        try {
            switch (path) {
                case "/api/v1/dashboard" -> handleDashboard(exchange);
                case "/api/v1/youtubers" -> handleYouTubers(exchange);
                case "/api/v1/youtubers/unlink" -> handleYouTuberUnlink(exchange, actor, clientIp);
                case "/api/v1/youtubers/allow" -> handleYouTuberAllow(exchange, actor, clientIp);
                case "/api/v1/youtubers/toggle" -> handleYouTuberToggle(exchange, actor, clientIp);
                case "/api/v1/history" -> handleHistory(exchange);
                case "/api/v1/links" -> handleLinks(exchange);
                case "/api/v1/links/create" -> handleCreateLink(exchange, actor, clientIp);
                case "/api/v1/settings" -> {
                    if ("POST".equals(method)) {
                        handleSaveSettings(exchange, actor, clientIp);
                    } else {
                        handleGetSettings(exchange);
                    }
                }
                case "/api/v1/premium" -> handlePremium(exchange);
                case "/api/v1/premium/redeem" -> handlePremiumRedeem(exchange, actor, clientIp);
                case "/api/v1/premium/purchase" -> handlePremiumPurchase(exchange, actor, clientIp);
                case "/api/v1/premium/manual" -> handlePremiumManual(exchange, actor, clientIp);
                case "/api/v1/premium/revoke" -> handlePremiumRevoke(exchange, actor, clientIp);
                case "/api/v1/audit" -> handleAudit(exchange);
                case "/api/v1/session/revoke" -> handleRevokeSession(exchange, token, actor, clientIp);
                default -> sendJsonResponse(exchange, 404, Map.of("error", "Endpoint not found: " + path));
            }
        } catch (Exception e) {
            logger.warning("[RestApi] Error handling " + path + ": " + e.getMessage());
            sendJsonResponse(exchange, 500, Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        AccountStorage accounts = plugin.getAccountStorage();
        int totalYouTubers = accounts != null ? accounts.getAllAccounts().size() : 0;
        int currentlyLive = historyTracker.getCurrentlyLiveCount();
        int totalEvents = historyTracker.getTotalEventsCount();
        int totalJoins = linkTracker.getTotalJoins();
        int premiumUsers = premiumService.getActiveCount();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalYouTubers", totalYouTubers);
        data.put("currentlyLive", currentlyLive);
        data.put("totalEvents", totalEvents);
        data.put("totalLinkJoins", totalJoins);
        data.put("premiumUsers", premiumUsers);

        // Server Status
        Map<String, Object> serverStatus = new LinkedHashMap<>();
        serverStatus.put("minecraftOnline", true);
        serverStatus.put("onlinePlayerCount", Bukkit.getOnlinePlayers().size());
        serverStatus.put("pluginVersion", plugin.getPluginMeta().getVersion());
        serverStatus.put("webPanelOnline", true);
        serverStatus.put("activeWebSessions", securityManager.getActiveSessionCount());
        data.put("serverStatus", serverStatus);

        // Recent live events
        data.put("recentEvents", historyTracker.getRecentEvents(10));

        sendJsonResponse(exchange, 200, data);
    }

    private void handleYouTubers(HttpExchange exchange) throws IOException {
        AccountStorage accountStorage = plugin.getAccountStorage();
        StreamerConfigStore configStore = plugin.getStreamerConfigStore();

        List<Map<String, Object>> list = new ArrayList<>();
        if (accountStorage != null) {
            for (AccountRecord acc : accountStorage.getAllAccounts()) {
                UUID uuid = acc.minecraftUuid();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("uuid", uuid.toString());
                item.put("playerName", acc.playerName() != null ? acc.playerName() : "Unknown");
                item.put("channelTitle", acc.channelTitle() != null ? acc.channelTitle() : "-");
                item.put("channelId", acc.youtubeChannelId() != null ? acc.youtubeChannelId() : "-");
                item.put("customUrl", acc.customUrl() != null ? acc.customUrl() : "");

                boolean isLive = historyTracker.isCurrentlyLive(uuid);
                item.put("currentlyLive", isLive);
                item.put("totalSessions", historyTracker.getSessionCount(uuid));
                item.put("totalLiveSeconds", historyTracker.getTotalLiveSeconds(uuid));
                Instant lastLive = historyTracker.getLastLiveTime(uuid);
                item.put("lastLive", lastLive != null ? lastLive.toString() : "-");
                item.put("dateLinked", acc.linkedAt() > 0 ? Instant.ofEpochMilli(acc.linkedAt()).toString() : "-");

                boolean isPrem = premiumService.isPremium(uuid);
                item.put("isPremium", isPrem);
                if (isPrem) {
                    premiumService.getRecord(uuid).ifPresent(r -> item.put("premiumTier", r.tier()));
                }

                // Chat announcement enabled
                if (configStore != null) {
                    StreamerChatSettings s = configStore.get(uuid).orElse(null);
                    item.put("chatEnabled", s == null || s.isEffectivelyEnabled());
                    item.put("intervalMode", s != null ? s.getIntervalMode().name() : "DEFAULT");
                } else {
                    item.put("chatEnabled", true);
                    item.put("intervalMode", "DEFAULT");
                }

                list.add(item);
            }
        }

        sendJsonResponse(exchange, 200, Map.of("youtubers", list));
    }

    private void handleYouTuberUnlink(HttpExchange exchange, String actor, String clientIp) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        JsonObject json = parseJsonBody(exchange);
        if (json == null || !json.has("uuid")) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing uuid"));
            return;
        }

        try {
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            boolean unlinked = plugin.getAccountStorage().deleteByUuid(uuid);
            if (plugin.getLinkService() != null) {
                plugin.getLinkService().updateAccount(uuid, false, null);
            }
            historyTracker.onStreamEnd(uuid);

            // Clean up player nametag if online
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && plugin.getNametagProvider() != null) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getNametagProvider().removeLiveIndicator(player));
            }

            auditLogger.log(actor, "YOUTUBER_UNLINK", uuid.toString(), clientIp, "Unlinked YouTube channel for " + (player != null ? player.getName() : uuid));
            sendJsonResponse(exchange, 200, Map.of("success", true, "unlinked", unlinked));
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, Map.of("error", "Invalid UUID format"));
        }
    }

    private void handleYouTuberAllow(HttpExchange exchange, String actor, String clientIp) throws IOException {
        JsonObject json = parseJsonBody(exchange);
        if (json == null || !json.has("playerName")) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing playerName"));
            return;
        }
        String playerName = json.get("playerName").getAsString().trim();
        UUID uuid = resolvePlayerUuid(playerName);

        plugin.getAllowedStorage().allowPlayer(uuid, playerName);
        if (plugin.getLinkService() != null) {
            plugin.getLinkService().allowLink(uuid, playerName);
        }

        auditLogger.log(actor, "YOUTUBER_ALLOW", playerName, clientIp, "Allowed player to link YouTube");
        sendJsonResponse(exchange, 200, Map.of("success", true, "playerName", playerName, "uuid", uuid.toString()));
    }

    private void handleYouTuberToggle(HttpExchange exchange, String actor, String clientIp) throws IOException {
        JsonObject json = parseJsonBody(exchange);
        if (json == null || !json.has("uuid") || !json.has("enabled")) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing uuid or enabled parameter"));
            return;
        }
        try {
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            boolean enabled = json.get("enabled").getAsBoolean();
            StreamerConfigStore store = plugin.getStreamerConfigStore();
            if (store != null) {
                StreamerChatSettings s = store.getOrCreate(uuid, "Player");
                s.setAdminDisabled(!enabled);
                store.saveOrUpdate(s);
            }
            auditLogger.log(actor, "YOUTUBER_TOGGLE", uuid.toString(), clientIp, "Set chat enabled=" + enabled);
            sendJsonResponse(exchange, 200, Map.of("success", true, "enabled", enabled));
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, Map.of("error", "Invalid UUID"));
        }
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        sendJsonResponse(exchange, 200, Map.of("history", historyTracker.getRecentEvents(100)));
    }

    private void handleLinks(HttpExchange exchange) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalJoins", linkTracker.getTotalJoins());
        data.put("joinsToday", linkTracker.getJoinsToday());
        data.put("joinsThisWeek", linkTracker.getJoinsThisWeek());
        data.put("joinsThisMonth", linkTracker.getJoinsThisMonth());
        data.put("links", linkTracker.getAllLinks());
        data.put("recentJoins", linkTracker.getRecentJoins(50));
        sendJsonResponse(exchange, 200, data);
    }

    private void handleCreateLink(HttpExchange exchange, String actor, String clientIp) throws IOException {
        JsonObject json = parseJsonBody(exchange);
        if (json == null || !json.has("linkId") || !json.has("label") || !json.has("targetAddress")) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing required link fields"));
            return;
        }
        String linkId = json.get("linkId").getAsString().trim().toLowerCase(Locale.ROOT);
        String label = json.get("label").getAsString().trim();
        String target = json.get("targetAddress").getAsString().trim();

        linkTracker.createOrUpdateLink(linkId, label, target);
        auditLogger.log(actor, "LINK_CREATE", linkId, clientIp, "Created server link: " + label);
        sendJsonResponse(exchange, 200, Map.of("success", true, "linkId", linkId));
    }

    private void handleGetSettings(HttpExchange exchange) throws IOException {
        PluginConfig config = plugin.getPluginConfig();
        Map<String, Object> settings = new LinkedHashMap<>();

        // Safe exposed settings (secrets masked!)
        settings.put("youtubeClientId", WebSecurityManager.maskSecret(config.getClientId()));
        settings.put("youtubeClientSecret", WebSecurityManager.maskSecret(config.getClientSecret()));
        settings.put("redirectUri", config.getRedirectUri());
        settings.put("callbackPort", config.getCallbackPort());
        settings.put("pollingIntervalSeconds", config.getPollingIntervalSeconds());
        settings.put("nametagEnabled", config.isNametagEnabled());
        settings.put("nametagProvider", config.getNametagProvider());
        settings.put("liveText", config.getLiveText());
        settings.put("liveFormat", config.getLiveFormat());
        settings.put("requireGamingCategory", config.isRequireGamingCategory());
        settings.put("requireMinecraft", config.isRequireMinecraft());
        settings.put("chatEnabled", config.isChatEnabled());
        settings.put("adminChatEnabled", config.isAdminChatEnabled());
        settings.put("globalMinimumGapSeconds", config.getGlobalMinimumGapSeconds());
        settings.put("defaultIntervalMinutes", config.getDefaultIntervalMinutes());
        settings.put("announceOfflineStreamers", config.isAnnounceOfflineStreamers());

        sendJsonResponse(exchange, 200, Map.of("settings", settings));
    }

    private void handleSaveSettings(HttpExchange exchange, String actor, String clientIp) throws IOException {
        JsonObject json = parseJsonBody(exchange);
        if (json == null) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing JSON payload"));
            return;
        }

        PluginConfig config = plugin.getPluginConfig();
        if (json.has("liveText")) config.setLiveText(json.get("liveText").getAsString());
        if (json.has("liveFormat")) config.setLiveFormat(json.get("liveFormat").getAsString());
        if (json.has("adminChatEnabled")) config.setAdminChatEnabled(json.get("adminChatEnabled").getAsBoolean());
        if (json.has("pollingIntervalSeconds")) config.setPollingIntervalSeconds(json.get("pollingIntervalSeconds").getAsInt());
        if (json.has("globalMinimumGapSeconds")) config.setGlobalMinimumGapSeconds(json.get("globalMinimumGapSeconds").getAsInt());
        if (json.has("defaultIntervalMinutes")) config.setDefaultIntervalMinutes(json.get("defaultIntervalMinutes").getAsInt());

        auditLogger.log(actor, "SETTINGS_UPDATE", "PluginConfig", clientIp, "Updated server live settings");
        sendJsonResponse(exchange, 200, Map.of("success", true, "message", "Settings updated successfully"));
    }

    private void handlePremium(HttpExchange exchange) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalActive", premiumService.getActiveCount());
        data.put("activeUsers", premiumService.getAllActive());
        data.put("codesCount", premiumService.getAllCodes().size());
        sendJsonResponse(exchange, 200, data);
    }

    private void handlePremiumRedeem(HttpExchange exchange, String actor, String clientIp) throws IOException {
        JsonObject json = parseJsonBody(exchange);
        if (json == null || !json.has("code") || !json.has("playerName")) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing code or playerName"));
            return;
        }
        String code = json.get("code").getAsString().trim();
        String playerName = json.get("playerName").getAsString().trim();
        UUID uuid = resolvePlayerUuid(playerName);

        PremiumService.RedemptionResult result = premiumService.redeemCode(code, uuid, playerName);
        if (result.success()) {
            auditLogger.log(actor, "PREMIUM_REDEEM", playerName, clientIp, "Redeemed code: " + code);
            sendJsonResponse(exchange, 200, Map.of("success", true, "message", result.message(), "record", result.record()));
        } else {
            auditLogger.log(actor, "PREMIUM_REDEEM_FAIL", playerName, clientIp, "Failed redeem attempt: " + result.message());
            sendJsonResponse(exchange, 400, Map.of("success", false, "error", result.message()));
        }
    }

    private void handlePremiumPurchase(HttpExchange exchange, String actor, String clientIp) throws IOException {
        JsonObject json = parseJsonBody(exchange);
        if (json == null || !json.has("tier") || !json.has("playerName")) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing tier or playerName"));
            return;
        }
        String tier = json.get("tier").getAsString();
        String playerName = json.get("playerName").getAsString();
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        // Generates safe redirect URL pointing to merchant gateway (Stripe/PayPal/Tebex)
        String checkoutUrl = "https://checkout.example.com/pay?order=" + orderId + "&tier=" + tier + "&player=" + playerName;

        auditLogger.log(actor, "PREMIUM_PURCHASE_INIT", playerName, clientIp, "Initiated purchase for tier " + tier + " (Order: " + orderId + ")");
        sendJsonResponse(exchange, 200, Map.of("success", true, "orderId", orderId, "checkoutUrl", checkoutUrl));
    }

    private void handlePremiumManual(HttpExchange exchange, String actor, String clientIp) throws IOException {
        JsonObject json = parseJsonBody(exchange);
        if (json == null || !json.has("playerName") || !json.has("tier")) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing playerName or tier"));
            return;
        }
        String playerName = json.get("playerName").getAsString().trim();
        String tier = json.get("tier").getAsString().trim();
        int days = json.has("durationDays") ? json.get("durationDays").getAsInt() : 30;
        UUID uuid = resolvePlayerUuid(playerName);

        PremiumRecord r = premiumService.setAdminPremium(uuid, playerName, tier, days, null, null);
        auditLogger.log(actor, "PREMIUM_MANUAL_SET", playerName, clientIp, "Manually granted " + tier + " for " + days + "d");
        sendJsonResponse(exchange, 200, Map.of("success", true, "record", r));
    }

    private void handlePremiumRevoke(HttpExchange exchange, String actor, String clientIp) throws IOException {
        JsonObject json = parseJsonBody(exchange);
        if (json == null || !json.has("uuid")) {
            sendJsonResponse(exchange, 400, Map.of("error", "Missing uuid"));
            return;
        }
        try {
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            boolean revoked = premiumService.revokePremium(uuid);
            auditLogger.log(actor, "PREMIUM_REVOKE", uuid.toString(), clientIp, "Revoked premium");
            sendJsonResponse(exchange, 200, Map.of("success", true, "revoked", revoked));
        } catch (IllegalArgumentException e) {
            sendJsonResponse(exchange, 400, Map.of("error", "Invalid UUID"));
        }
    }

    private void handleAudit(HttpExchange exchange) throws IOException {
        sendJsonResponse(exchange, 200, Map.of("auditLogs", auditLogger.getRecentEntries(50)));
    }

    private void handleRevokeSession(HttpExchange exchange, String token, String actor, String clientIp) throws IOException {
        securityManager.revokeSession(token);
        auditLogger.log(actor, "SESSION_REVOKE", token.substring(0, 8) + "...", clientIp, "Admin logged out and revoked panel session");
        sendJsonResponse(exchange, 200, Map.of("success", true, "message", "Session revoked successfully."));
    }

    private void handlePaymentWebhook(HttpExchange exchange, String clientIp) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        String signatureHeader = exchange.getRequestHeaders().getFirst("X-Signature-256");
        if (signatureHeader == null) {
            signatureHeader = exchange.getRequestHeaders().getFirst("X-Hub-Signature-256");
        }

        try {
            JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            String event = json.has("event") ? json.get("event").getAsString() : "";
            String orderId = json.has("orderId") ? json.get("orderId").getAsString() : "";
            String playerName = json.has("playerName") ? json.get("playerName").getAsString() : "Unknown";
            String tier = json.has("tier") ? json.get("tier").getAsString() : "PRO";
            int durationDays = json.has("durationDays") ? json.get("durationDays").getAsInt() : 30;
            UUID playerUuid = json.has("minecraftUuid") ? UUID.fromString(json.get("minecraftUuid").getAsString()) :
                    resolvePlayerUuid(playerName);

            PaymentWebhookHandler.WebhookResult result = webhookHandler.processWebhook(
                    body,
                    signatureHeader,
                    event,
                    orderId,
                    playerUuid,
                    playerName,
                    tier,
                    durationDays
            );

            if (result.success()) {
                auditLogger.log("PaymentWebhook", "PAYMENT_CONFIRMED", playerName, clientIp, "Webhook verified for order " + orderId);
            }
            sendJsonResponse(exchange, result.httpStatus(), Map.of("message", result.responseMessage()));
        } catch (Exception e) {
            logger.warning("[PaymentWebhook] Error processing webhook: " + e.getMessage());
            sendJsonResponse(exchange, 400, Map.of("error", "Malformed payload"));
        }
    }

    private String extractToken(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7).trim();
        }
        // Fallback to query parameter ?token=...
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && "token".equalsIgnoreCase(pair[0])) {
                    return pair[1].trim();
                }
            }
        }
        return null;
    }

    private JsonObject parseJsonBody(HttpExchange exchange) {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) return null;
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private UUID resolvePlayerUuid(String playerName) {
        if (playerName == null || playerName.isBlank()) return UUID.randomUUID();
        Player online = Bukkit.getPlayerExact(playerName);
        return online != null ? online.getUniqueId() :
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }
}
