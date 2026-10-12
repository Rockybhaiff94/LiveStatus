package com.example.livestatus.premium;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record representing an active or expired premium subscription/license.
 */
public record PremiumRecord(
        UUID minecraftUuid,
        String playerName,
        String tier,
        Instant activatedAt,
        Instant expiresAt, // null for lifetime
        String activationMethod, // "CODE", "PAYMENT", "ADMIN_OVERRIDE"
        String referenceId, // orderId or redeemedCode
        // Extensible custom premium settings
        String customLiveText,
        String customLiveIcon,
        String customLiveFormat
) {
    public boolean isActive() {
        if (expiresAt == null) {
            return true; // Lifetime
        }
        return Instant.now().isBefore(expiresAt);
    }
}
