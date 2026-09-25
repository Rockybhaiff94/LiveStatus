package com.example.livestatus.storage;

import java.util.UUID;

/**
 * Record representing a linked Minecraft player and their YouTube credentials and metadata.
 */
public record AccountRecord(
        UUID minecraftUuid,
        String playerName,
        String youtubeChannelId,
        String channelTitle,
        String customUrl,
        String accessToken,
        String refreshToken,
        long tokenExpiresAt,
        long linkedAt,
        long lastVerifiedAt,
        String linkStatus
) {
    /**
     * Checks if the OAuth access token has expired or is expiring within a 60-second safety window.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= (tokenExpiresAt - 60_000);
    }
}

