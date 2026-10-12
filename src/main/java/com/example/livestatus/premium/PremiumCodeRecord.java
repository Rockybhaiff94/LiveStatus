package com.example.livestatus.premium;

import java.time.Instant;
import java.util.UUID;

/**
 * Record representing a pre-generated premium redemption code.
 */
public record PremiumCodeRecord(
        String code,
        String tier,
        int durationDays, // 0 for lifetime
        boolean redeemed,
        UUID redeemedByUuid,
        String redeemedByName,
        Instant redeemedAt
) {
    public PremiumCodeRecord withRedeemed(UUID playerUuid, String playerName) {
        return new PremiumCodeRecord(
                this.code,
                this.tier,
                this.durationDays,
                true,
                playerUuid,
                playerName,
                Instant.now()
        );
    }
}
