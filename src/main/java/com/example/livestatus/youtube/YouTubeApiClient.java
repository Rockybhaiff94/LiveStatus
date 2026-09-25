package com.example.livestatus.youtube;

import com.example.livestatus.config.PluginConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Direct client for official YouTube Data API v3 and Live Streaming API.
 */
public class YouTubeApiClient {

    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";

    private final PluginConfig config;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public record ChannelInfo(String channelId, String title, String customUrl) {}

    public YouTubeApiClient(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                .build();
    }

    /**
     * Retrieves the channel details for the authenticated user.
     */
    public ChannelInfo getMyChannel(String accessToken) {
        String url = BASE_URL + "/channels?part=snippet&mine=true";
        JsonObject json = executeGet(url, accessToken);
        if (json == null || !json.has("items")) {
            return null;
        }

        JsonArray items = json.getAsJsonArray("items");
        if (items.isEmpty()) {
            return null;
        }

        JsonObject item = items.get(0).getAsJsonObject();
        String channelId = item.get("id").getAsString();
        JsonObject snippet = item.getAsJsonObject("snippet");
        String title = snippet.has("title") ? snippet.get("title").getAsString() : "";
        String customUrl = snippet.has("customUrl") ? snippet.get("customUrl").getAsString() : "";

        return new ChannelInfo(channelId, title, customUrl);
    }

    /**
     * Lists active live broadcasts for the authenticated channel.
     */
    public List<StreamEligibilityDetector.BroadcastMetadata> getActiveBroadcasts(String accessToken) {
        String url = BASE_URL + "/liveBroadcasts?part=snippet,status&broadcastStatus=active&mine=true";
        JsonObject json = executeGet(url, accessToken);
        if (json == null || !json.has("items")) {
            return Collections.emptyList();
        }

        List<StreamEligibilityDetector.BroadcastMetadata> results = new ArrayList<>();
        JsonArray items = json.getAsJsonArray("items");
        for (JsonElement elem : items) {
            JsonObject item = elem.getAsJsonObject();
            String id = item.get("id").getAsString();

            JsonObject snippet = item.has("snippet") ? item.getAsJsonObject("snippet") : new JsonObject();
            String title = snippet.has("title") ? snippet.get("title").getAsString() : "";

            JsonObject status = item.has("status") ? item.getAsJsonObject("status") : new JsonObject();
            String lifeCycleStatus = status.has("lifeCycleStatus") ? status.get("lifeCycleStatus").getAsString() : "unknown";
            String privacyStatus = status.has("privacyStatus") ? status.get("privacyStatus").getAsString() : "public";

            results.add(new StreamEligibilityDetector.BroadcastMetadata(id, title, lifeCycleStatus, privacyStatus));
        }

        return results;
    }

    /**
     * Retrieves video details including categoryId and topicCategories.
     */
    public StreamEligibilityDetector.VideoMetadata getVideoDetails(String accessToken, String videoId) {
        String url = BASE_URL + "/videos?part=snippet,topicDetails&id=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8);
        JsonObject json = executeGet(url, accessToken);
        if (json == null || !json.has("items")) {
            return null;
        }

        JsonArray items = json.getAsJsonArray("items");
        if (items.isEmpty()) {
            return null;
        }

        JsonObject item = items.get(0).getAsJsonObject();
        JsonObject snippet = item.has("snippet") ? item.getAsJsonObject("snippet") : new JsonObject();
        String categoryId = snippet.has("categoryId") ? snippet.get("categoryId").getAsString() : "";
        String title = snippet.has("title") ? snippet.get("title").getAsString() : "";
        String description = snippet.has("description") ? snippet.get("description").getAsString() : "";

        List<String> topicCategories = new ArrayList<>();
        if (item.has("topicDetails")) {
            JsonObject topicDetails = item.getAsJsonObject("topicDetails");
            if (topicDetails.has("topicCategories")) {
                for (JsonElement topicElem : topicDetails.getAsJsonArray("topicCategories")) {
                    topicCategories.add(topicElem.getAsString());
                }
            }
        }

        return new StreamEligibilityDetector.VideoMetadata(videoId, categoryId, topicCategories, title, description);
    }

    private JsonObject executeGet(String url, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return gson.fromJson(response.body(), JsonObject.class);
            } else {
                if (config.isDebugEnabled()) {
                    logger.warning("YouTube API GET " + url + " returned HTTP " + response.statusCode());
                }
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception invoking YouTube API: " + e.getMessage());
            return null;
        }
    }
}

