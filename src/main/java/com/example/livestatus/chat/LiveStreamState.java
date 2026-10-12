package com.example.livestatus.chat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable aggregated model representing the complete live state and announcement configuration
 * for a streamer at an instantaneous point in time.
 */
public record LiveStreamState(
        UUID minecraftUuid,
        String playerName,
        String youtubeChannelId,
        boolean authorized,
        boolean linked,
        boolean currentlyLive,
        String videoId,
        String streamTitle,
        String streamUrl,
        boolean eligibleMinecraftStream,
        boolean chatEnabled,
        IntervalMode intervalMode,
        int customIntervalMinutes,
        String customTitle,
        Instant lastAnnouncementTime,
        Instant nextEligibleTime
) {
    public LiveStreamState {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        playerName = playerName != null ? playerName : "Unknown";
        intervalMode = intervalMode != null ? intervalMode : IntervalMode.DEFAULT;
    }

    /**
     * Evaluates whether this streamer meets all criteria to have a chat announcement sent right now.
     */
    public boolean isEligibleForAnnouncement(Instant now, boolean globalAdminEnabled) {
        return globalAdminEnabled
                && chatEnabled
                && authorized
                && linked
                && currentlyLive
                && eligibleMinecraftStream
                && videoId != null
                && !videoId.isBlank()
                && (nextEligibleTime == null || !nextEligibleTime.isAfter(now));
    }

    /**
     * Resolves the effective title to display in announcements.
     * Uses custom title if non-blank, otherwise falls back to current YouTube stream title.
     * If neither exists, returns a clean default.
     */
    public String getEffectiveTitle() {
        if (customTitle != null && !customTitle.isBlank()) {
            return customTitle.trim();
        }
        if (streamTitle != null && !streamTitle.isBlank()) {
            return streamTitle.trim();
        }
        return "Minecraft Live Stream";
    }
}
