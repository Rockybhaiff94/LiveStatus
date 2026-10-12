package com.example.livestatus.history;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service managing live stream lifecycles: starts, ends, duration calculations,
 * and deduplication so periodic polling refreshes do not create duplicate events.
 */
public class LiveHistoryTracker {

    private final LiveHistoryStorage storage;
    private final Logger logger;
    // Map of Player UUID -> Active LiveEventRecord
    private final Map<UUID, LiveEventRecord> activeStreams = new ConcurrentHashMap<>();

    public LiveHistoryTracker(LiveHistoryStorage storage, Logger logger) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.logger = logger;
        // Resume any open LIVE records from storage if needed
        for (LiveEventRecord record : storage.getAll()) {
            if ("LIVE".equalsIgnoreCase(record.status()) && record.minecraftUuid() != null) {
                activeStreams.putIfAbsent(record.minecraftUuid(), record);
            }
        }
    }

    /**
     * Called when a player is detected live.
     * Deduplicates by videoId: if the streamer is already recorded with this videoId, no new event is created.
     */
    public synchronized LiveEventRecord onStreamStart(
            UUID playerUuid,
            String playerName,
            String channelId,
            String channelTitle,
            String videoId,
            String streamTitle
    ) {
        LiveEventRecord existing = activeStreams.get(playerUuid);
        if (existing != null) {
            // If already active with the same video ID, just return existing record (no duplicate)
            if (Objects.equals(existing.videoId(), videoId)) {
                return existing;
            }
            // If active with a different video ID, conclude previous stream first
            onStreamEnd(playerUuid);
        }

        LiveEventRecord newEvent = new LiveEventRecord(
                UUID.randomUUID(),
                playerUuid,
                playerName,
                channelId,
                channelTitle,
                videoId,
                streamTitle,
                Instant.now(),
                null,
                0,
                "LIVE"
        );

        activeStreams.put(playerUuid, newEvent);
        storage.put(newEvent);
        storage.save();
        if (logger != null) {
            logger.info("[LiveHistory] Started tracking stream event for " + playerName + " (Video: " + videoId + ")");
        }
        return newEvent;
    }

    /**
     * Called when a player's stream has ended or they go offline.
     */
    public synchronized Optional<LiveEventRecord> onStreamEnd(UUID playerUuid) {
        LiveEventRecord active = activeStreams.remove(playerUuid);
        if (active == null) {
            return Optional.empty();
        }

        LiveEventRecord ended = active.withEnded(Instant.now());
        storage.put(ended);
        storage.save();
        if (logger != null) {
            logger.info("[LiveHistory] Ended stream event for " + active.playerName() + " (Duration: " + ended.durationSeconds() + "s)");
        }
        return Optional.of(ended);
    }

    public boolean isCurrentlyLive(UUID playerUuid) {
        return activeStreams.containsKey(playerUuid);
    }

    public Optional<LiveEventRecord> getActiveStream(UUID playerUuid) {
        return Optional.ofNullable(activeStreams.get(playerUuid));
    }

    public List<LiveEventRecord> getRecentEvents(int limit) {
        return storage.getAll().stream().limit(limit).toList();
    }

    public int getTotalEventsCount() {
        return storage.getAll().size();
    }

    public int getCurrentlyLiveCount() {
        return activeStreams.size();
    }

    public long getTotalLiveSeconds(UUID playerUuid) {
        long total = storage.getForPlayer(playerUuid).stream()
                .mapToLong(LiveEventRecord::durationSeconds)
                .sum();
        LiveEventRecord active = activeStreams.get(playerUuid);
        if (active != null && active.startedAt() != null) {
            total += Math.max(0, Instant.now().getEpochSecond() - active.startedAt().getEpochSecond());
        }
        return total;
    }

    public int getSessionCount(UUID playerUuid) {
        return storage.getForPlayer(playerUuid).size();
    }

    public Instant getLastLiveTime(UUID playerUuid) {
        List<LiveEventRecord> records = storage.getForPlayer(playerUuid);
        if (!records.isEmpty()) {
            return records.get(0).startedAt();
        }
        return null;
    }

    public LiveHistoryStorage getStorage() {
        return storage;
    }
}
