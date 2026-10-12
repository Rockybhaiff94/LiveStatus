package com.example.livestatus.web;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable model representing an active temporary web panel administrative session.
 */
public record PanelSession(
        String token,
        String accessCode,
        String issuer,
        Instant createdAt,
        Instant expiresAt
) {
    public PanelSession {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(accessCode, "accessCode");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
