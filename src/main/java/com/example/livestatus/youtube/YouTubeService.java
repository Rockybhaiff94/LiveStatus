package com.example.livestatus.youtube;

import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.oauth.GoogleOAuthClient;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * High-level YouTube query and caching service running directly within the Paper plugin.
 */
public class YouTubeService {

    private final AccountStorage accountStorage;
    private final PlayerLinkService linkService;
    private final GoogleOAuthClient oAuthClient;
    private final YouTubeApiClient youTubeApiClient;
    private final StreamEligibilityDetector eligibilityDetector;
    private final PluginConfig config;
    private final Logger logger;

    private final Map<UUID, CachedBroadcastStatus> cache = new ConcurrentHashMap<>();

    public record CachedBroadcastStatus(
            YouTubeStatus status,
            YouTubeBroadcast broadcast,
            long timestamp,
            String error
    ) {
        public boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    public YouTubeService(
            AccountStorage accountStorage,
            PlayerLinkService linkService,
            GoogleOAuthClient oAuthClient,
            YouTubeApiClient youTubeApiClient,
            StreamEligibilityDetector eligibilityDetector,
            PluginConfig config,
            Logger logger
    ) {
        this.accountStorage = Objects.requireNonNull(accountStorage, "accountStorage");
        this.linkService = Objects.requireNonNull(linkService, "linkService");
        this.oAuthClient = Objects.requireNonNull(oAuthClient, "oAuthClient");
        this.youTubeApiClient = Objects.requireNonNull(youTubeApiClient, "youTubeApiClient");
        this.eligibilityDetector = Objects.requireNonNull(eligibilityDetector, "eligibilityDetector");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Checks and updates the live status for a single player asynchronously.
     */
    public CompletableFuture<YouTubeStatus> checkStatus(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> queryPlayerStatus(uuid));
    }

    /**
     * Checks and updates live status for a collection of players in batch asynchronously.
     */
    public CompletableFuture<Map<UUID, YouTubeStatus>> checkBatchStatus(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, YouTubeStatus> results = new HashMap<>();
            for (UUID uuid : uuids) {
                results.put(uuid, queryPlayerStatus(uuid));
            }
            return results;
        });
    }

    private YouTubeStatus queryPlayerStatus(UUID uuid) {
        Optional<AccountRecord> opt = accountStorage.findByUuid(uuid);
        if (opt.isEmpty()) {
            linkService.updateAccount(uuid, false, null);
            linkService.updateStatus(uuid, YouTubeStatus.NOT_LINKED, null);
            return YouTubeStatus.NOT_LINKED;
        }

        AccountRecord record = opt.get();
        YouTubeAccount account = new YouTubeAccount(record.youtubeChannelId(), record.channelTitle(), record.customUrl());
        linkService.updateAccount(uuid, true, account);

        // Check cache (TTL = polling interval)
        long ttlMs = config.getPollingIntervalSeconds() * 1000L;
        CachedBroadcastStatus cached = cache.get(uuid);
        if (cached != null && !cached.isExpired(ttlMs)) {
            linkService.updateStatus(uuid, cached.status(), cached.error());
            return cached.status();
        }

        // Live check required
        CachedBroadcastStatus fresh = fetchLiveBroadcast(record);
        cache.put(uuid, fresh);
        linkService.updateStatus(uuid, fresh.status(), fresh.error());
        return fresh.status();
    }

    private CachedBroadcastStatus fetchLiveBroadcast(AccountRecord record) {
        String accessToken = record.accessToken();

        // 1. Refresh token if expired
        if (record.isExpired()) {
            if (record.refreshToken() != null && !record.refreshToken().isBlank()) {
                GoogleOAuthClient.TokenResponse refreshed = oAuthClient.refreshToken(record.refreshToken());
                if (refreshed != null && refreshed.accessToken() != null) {
                    accessToken = refreshed.accessToken();
                    long expiresAt = System.currentTimeMillis() + refreshed.expiresInMs();
                    accountStorage.updateTokens(record.minecraftUuid(), accessToken, expiresAt);
                } else {
                    return new CachedBroadcastStatus(YouTubeStatus.UNKNOWN, null, System.currentTimeMillis(), "Failed to refresh expired OAuth token");
                }
            } else {
                return new CachedBroadcastStatus(YouTubeStatus.UNKNOWN, null, System.currentTimeMillis(), "OAuth token expired with no refresh token");
            }
        }

        // 2. Query active broadcasts
        try {
            List<StreamEligibilityDetector.BroadcastMetadata> broadcasts = youTubeApiClient.getActiveBroadcasts(accessToken);
            if (broadcasts == null || broadcasts.isEmpty()) {
                return new CachedBroadcastStatus(YouTubeStatus.OFFLINE, null, System.currentTimeMillis(), null);
            }

            StreamEligibilityDetector.BroadcastMetadata active = broadcasts.get(0);
            StreamEligibilityDetector.VideoMetadata video = youTubeApiClient.getVideoDetails(accessToken, active.broadcastId());

            StreamEligibilityDetector.Eligibility eligibility = eligibilityDetector.detectEligibility(active, video);

            YouTubeStatus status = switch (eligibility) {
                case ELIGIBLE_MINECRAFT_LIVE -> YouTubeStatus.LIVE_MINECRAFT;
                case NOT_MINECRAFT -> YouTubeStatus.LIVE_NOT_MINECRAFT;
                case NOT_LIVE -> YouTubeStatus.OFFLINE;
                case UNKNOWN -> YouTubeStatus.UNKNOWN;
            };

            YouTubeBroadcast broadcast = new YouTubeBroadcast(
                    active.broadcastId(),
                    active.title(),
                    active.lifeCycleStatus(),
                    active.privacyStatus(),
                    video != null ? video.categoryId() : null,
                    video != null ? video.topicCategories() : Collections.emptyList(),
                    status == YouTubeStatus.LIVE_MINECRAFT
            );

            return new CachedBroadcastStatus(status, broadcast, System.currentTimeMillis(), null);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking live status for " + record.minecraftUuid() + ": " + e.getMessage());
            return new CachedBroadcastStatus(YouTubeStatus.UNKNOWN, null, System.currentTimeMillis(), e.getMessage());
        }
    }

    public void invalidateCache(UUID uuid) {
        cache.remove(uuid);
    }

    public Optional<CachedBroadcastStatus> getCachedStatus(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }
}

