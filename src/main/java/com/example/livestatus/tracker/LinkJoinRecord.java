package com.example.livestatus.tracker;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record representing a tracked join or referral event.
 */
public record LinkJoinRecord(
        UUID joinId,
        UUID minecraftUuid,
        String playerName,
        Instant joinTime,
        String linkId,
        String referralSource
) {}
