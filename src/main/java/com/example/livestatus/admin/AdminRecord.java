package com.example.livestatus.admin;

import java.util.UUID;

/**
 * Immutable representation of a persistently granted MCast administrator.
 */
public record AdminRecord(
        UUID uuid,
        String name,
        long grantedAt,
        String grantedBy
) {
    public AdminRecord {
        if (name == null || name.isBlank()) {
            name = uuid != null ? uuid.toString() : "Unknown";
        }
    }
}
