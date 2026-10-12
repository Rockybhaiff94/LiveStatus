package com.example.livestatus.history;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record representing a detected YouTube live broadcast session.
 */
public record LiveEventRecord(
        UUID eventId,
        UUID minecraftUuid,
        String playerName,
        String youtubeChannelId,
        String channelTitle,
        String videoId,
        String streamTitle,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        String status
) {
    public LiveEventRecord withEnded(Instant endedAt) {
        long duration = (startedAt != null && endedAt != null)
                ? Math.max(0, endedAt.getEpochSecond() - startedAt.getEpochSecond())
                : this.durationSeconds;
        return new LiveEventRecord(
                this.eventId,
                this.minecraftUuid,
                this.playerName,
                this.youtubeChannelId,
                this.channelTitle,
                this.videoId,
                this.streamTitle,
                this.startedAt,
                endedAt,
                duration,
                "ENDED"
        );
    }
}
