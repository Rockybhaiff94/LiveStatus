package com.example.livestatus.mcast;

import com.example.livestatus.youtube.YouTubeStatus;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous HTTP client communicating directly with the centralized MCast API (api.mcast.fun).
 * Eliminates per-server Google Cloud credentials, executing all registration, linking,
 * and live-status queries with strict server identity authentication.
 */
public class MCastApiClient {

    private final String baseUrl;
    private final MCastServerIdentity identity;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private volatile Runnable onUnregistered;

    public void setOnUnregistered(Runnable onUnregistered) {
        this.onUnregistered = onUnregistered;
    }

    public record LinkSessionResponse(
            String sessionId,
            String code,
            String linkUrl,
            long expiresAt,
            String playerUuid,
            String playerName
    ) {}

    public record LinkSessionStatus(
            String sessionId,
            String code,
            String status, // pending, completed, expired, cancelled
            String channelId,
            String channelTitle,
            String customUrl
    ) {
        public boolean isCompleted() {
            return "completed".equalsIgnoreCase(status);
        }

        public boolean isExpired() {
            return "expired".equalsIgnoreCase(status);
        }
    }

    public record PlayerLiveStatus(
            UUID playerUuid,
            boolean linked,
            String channelId,
            String channelTitle,
            YouTubeStatus status,
            boolean isLive,
            boolean isMinecraft,
            String videoId,
            String title,
            String streamUrl,
            String error
    ) {
        public PlayerLiveStatus(UUID playerUuid, boolean linked, String channelId, String channelTitle,
                                YouTubeStatus status, boolean isLive, boolean isMinecraft,
                                String videoId, String title, String streamUrl) {
            this(playerUuid, linked, channelId, channelTitle, status, isLive, isMinecraft, videoId, title, streamUrl, null);
        }
    }

    public MCastApiClient(String baseUrl, MCastServerIdentity identity, Logger logger) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.identity = Objects.requireNonNull(identity, "identity");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * Registers or updates this server installation with the MCast API.
     */
    public CompletableFuture<Boolean> registerServer(String serverName, String pluginVersion, String paperVersion, List<String> capabilities) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id", identity.getServerId());
        body.addProperty("server_secret", identity.getServerSecret());
        body.addProperty("server_name", serverName);
        body.addProperty("plugin_version", pluginVersion);
        body.addProperty("paper_version", paperVersion);

        JsonArray capArray = new JsonArray();
        for (String cap : capabilities) {
            capArray.add(cap);
        }
        body.add("capabilities", capArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/servers/register"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() == 200) {
                        logger.info("Successfully registered server installation with MCast API: " + identity.getServerId());
                        return true;
                    } else {
                        logger.warning("MCast server registration returned HTTP " + res.statusCode() + ": " + res.body());
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    logger.warning("Failed to reach MCast API during server registration: " + ex.getMessage());
                    return false;
                });
    }

    /**
     * Sends an authenticated heartbeat ping to MCast API.
     */
    public CompletableFuture<Boolean> sendHeartbeat() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/servers/heartbeat"))
                .header("X-MCast-Server-Id", identity.getServerId())
                .header("Authorization", "Bearer " + identity.getServerSecret())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> res.statusCode() == 200)
                .exceptionally(ex -> false);
    }

    /**
     * Creates a cryptographically random, single-use linking session for an authorized player.
     */
    public CompletableFuture<LinkSessionResponse> createLinkSession(UUID playerUuid, String playerName) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", playerUuid.toString());
        body.addProperty("player_name", playerName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/servers/link-sessions"))
                .header("X-MCast-Server-Id", identity.getServerId())
                .header("Authorization", "Bearer " + identity.getServerSecret())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() == 200) {
                        JsonObject json = gson.fromJson(res.body(), JsonObject.class);
                        return new LinkSessionResponse(
                                json.get("session_id").getAsString(),
                                json.get("code").getAsString(),
                                json.get("link_url").getAsString(),
                                json.get("expires_at").getAsLong(),
                                json.get("player_uuid").getAsString(),
                                json.get("player_name").getAsString()
                        );
                    } else {
                        logger.warning("Failed to create link session on MCast API (HTTP " + res.statusCode() + "): " + res.body());
                        if (res.statusCode() == 401 && onUnregistered != null) {
                            onUnregistered.run();
                        }
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Exception calling MCast createLinkSession: " + ex.getMessage(), ex);
                    return null;
                });
    }

    /**
     * Polls the status of an active linking session.
     */
    public CompletableFuture<LinkSessionStatus> pollLinkSession(String sessionId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/servers/link-sessions/" + sessionId))
                .header("X-MCast-Server-Id", identity.getServerId())
                .header("Authorization", "Bearer " + identity.getServerSecret())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() == 200) {
                        JsonObject json = gson.fromJson(res.body(), JsonObject.class);
                        String status = json.has("status") ? json.get("status").getAsString() : "unknown";
                        String channelId = json.has("youtube_channel_id") && !json.get("youtube_channel_id").isJsonNull()
                                ? json.get("youtube_channel_id").getAsString() : null;
                        String channelTitle = json.has("channel_title") && !json.get("channel_title").isJsonNull()
                                ? json.get("channel_title").getAsString() : null;
                        String customUrl = json.has("custom_url") && !json.get("custom_url").isJsonNull()
                                ? json.get("custom_url").getAsString() : null;

                        return new LinkSessionStatus(
                                json.get("session_id").getAsString(),
                                json.get("code").getAsString(),
                                status,
                                channelId,
                                channelTitle,
                                customUrl
                        );
                    }
                    return null;
                })
                .exceptionally(ex -> null);
    }

    /**
     * Unlinks a player association from this Minecraft server on the MCast API.
     */
    public CompletableFuture<Boolean> unlinkPlayer(UUID playerUuid) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", playerUuid.toString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/servers/players/unlink"))
                .header("X-MCast-Server-Id", identity.getServerId())
                .header("Authorization", "Bearer " + identity.getServerSecret())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> res.statusCode() == 200)
                .exceptionally(ex -> {
                    logger.warning("Failed to unlink player on MCast API: " + ex.getMessage());
                    return false;
                });
    }

    /**
     * Batches live status queries for all online players into a single efficient HTTPS request.
     */
    public CompletableFuture<Map<UUID, PlayerLiveStatus>> fetchBatchStatus(Collection<UUID> onlineUuids) {
        if (onlineUuids == null || onlineUuids.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        JsonObject body = new JsonObject();
        JsonArray uuidArray = new JsonArray();
        for (UUID uuid : onlineUuids) {
            uuidArray.add(uuid.toString());
        }
        body.add("player_uuids", uuidArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/servers/players/batch-status"))
                .header("X-MCast-Server-Id", identity.getServerId())
                .header("Authorization", "Bearer " + identity.getServerSecret())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() != 200) {
                        logger.warning("MCast batch status returned HTTP " + res.statusCode() + ": " + res.body());
                        if (res.statusCode() == 401 && onUnregistered != null) {
                            onUnregistered.run();
                        }
                        return Collections.<UUID, PlayerLiveStatus>emptyMap();
                    }

                    JsonObject json = gson.fromJson(res.body(), JsonObject.class);
                    if (!json.has("statuses")) {
                        return Collections.<UUID, PlayerLiveStatus>emptyMap();
                    }

                    JsonObject statusesObj = json.getAsJsonObject("statuses");
                    Map<UUID, PlayerLiveStatus> results = new HashMap<>();

                    for (Map.Entry<String, JsonElement> entry : statusesObj.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            JsonObject sObj = entry.getValue().getAsJsonObject();
                            boolean linked = sObj.has("linked") && sObj.get("linked").getAsBoolean();

                            if (!linked) {
                                results.put(uuid, new PlayerLiveStatus(
                                        uuid, false, null, null, YouTubeStatus.NOT_LINKED,
                                        false, false, null, null, null
                                ));
                                continue;
                            }

                            String channelId = sObj.has("channel_id") && !sObj.get("channel_id").isJsonNull()
                                    ? sObj.get("channel_id").getAsString() : null;
                            String channelTitle = sObj.has("channel_title") && !sObj.get("channel_title").isJsonNull()
                                    ? sObj.get("channel_title").getAsString() : "YouTube Channel";
                            String statusStr = sObj.has("status") ? sObj.get("status").getAsString() : "UNKNOWN";

                            YouTubeStatus ytStatus = switch (statusStr) {
                                case "LIVE_MINECRAFT" -> YouTubeStatus.LIVE_MINECRAFT;
                                case "LIVE_NOT_MINECRAFT" -> YouTubeStatus.LIVE_NOT_MINECRAFT;
                                case "OFFLINE" -> YouTubeStatus.OFFLINE;
                                default -> YouTubeStatus.UNKNOWN;
                            };

                            boolean isLive = sObj.has("is_live") && sObj.get("is_live").getAsBoolean();
                            boolean isMinecraft = sObj.has("is_minecraft") && sObj.get("is_minecraft").getAsBoolean();
                            String videoId = sObj.has("video_id") && !sObj.get("video_id").isJsonNull() ? sObj.get("video_id").getAsString() : null;
                            String title = sObj.has("title") && !sObj.get("title").isJsonNull() ? sObj.get("title").getAsString() : null;
                            String streamUrl = sObj.has("stream_url") && !sObj.get("stream_url").isJsonNull() ? sObj.get("stream_url").getAsString() : null;
                            String error = sObj.has("error") && !sObj.get("error").isJsonNull() ? sObj.get("error").getAsString() : null;

                            results.put(uuid, new PlayerLiveStatus(
                                    uuid, true, channelId, channelTitle, ytStatus,
                                    isLive, isMinecraft, videoId, title, streamUrl, error
                            ));
                        } catch (Exception e) {
                            logger.warning("Error parsing player status entry: " + entry.getKey());
                        }
                    }

                    return results;
                })
                .exceptionally(ex -> {
                    logger.warning("Failed to fetch batch live status from MCast API: " + ex.getMessage());
                    return Collections.emptyMap();
                });
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
