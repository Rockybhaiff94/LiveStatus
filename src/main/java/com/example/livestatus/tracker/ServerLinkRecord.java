package com.example.livestatus.tracker;

import java.time.Instant;

/**
 * Model representing a configured server referral link.
 */
public record ServerLinkRecord(
        String linkId,
        String label,
        String targetAddress,
        long clicksCount,
        long joinsCount,
        Instant createdAt
) {
    public ServerLinkRecord withIncrementedClicks() {
        return new ServerLinkRecord(linkId, label, targetAddress, clicksCount + 1, joinsCount, createdAt);
    }

    public ServerLinkRecord withIncrementedJoins() {
        return new ServerLinkRecord(linkId, label, targetAddress, clicksCount, joinsCount + 1, createdAt);
    }
}
