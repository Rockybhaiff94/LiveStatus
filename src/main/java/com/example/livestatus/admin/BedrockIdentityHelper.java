package com.example.livestatus.admin;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

/**
 * Helper utility for resolving and matching Java and Bedrock player identities.
 * Detects Bedrock/Floodgate identity prefixes and supports optional Floodgate API reflection
 * without introducing a hard compile or runtime dependency.
 */
public final class BedrockIdentityHelper {

    private static final String FLOODGATE_CLASS = "org.geysermc.floodgate.api.FloodgateApi";
    private static final boolean FLOODGATE_AVAILABLE;

    static {
        boolean available = false;
        try {
            Class.forName(FLOODGATE_CLASS);
            available = true;
        } catch (Throwable ignored) {
        }
        FLOODGATE_AVAILABLE = available;
    }

    private BedrockIdentityHelper() {}

    /**
     * Checks if Floodgate is active on the server.
     */
    public static boolean isFloodgateAvailable() {
        return FLOODGATE_AVAILABLE;
    }

    /**
     * Determines whether the given player is a Bedrock player via Floodgate or name prefix.
     */
    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        if (FLOODGATE_AVAILABLE) {
            try {
                Class<?> apiClass = Class.forName(FLOODGATE_CLASS);
                Method getInstance = apiClass.getMethod("getInstance");
                Object instance = getInstance.invoke(null);
                Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                Object result = isFloodgatePlayer.invoke(instance, player.getUniqueId());
                if (result instanceof Boolean b && b) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        String name = player.getName();
        return isBedrockName(name);
    }

    /**
     * Determines whether a given name matches common Bedrock conventions (e.g. Floodgate prefixes).
     */
    public static boolean isBedrockName(String name) {
        if (name == null || name.isBlank()) return false;
        char first = name.charAt(0);
        return first == '.' || first == '*' || first == '_';
    }

    /**
     * Matches a configured identity entry against a player's UUID and name.
     * Supports:
     * 1. Exact UUID matching.
     * 2. Case-insensitive username matching.
     * 3. Bedrock prefix matching where appropriate while preventing ambiguous collisions.
     *
     * @param configuredEntry The entry from config or storage (UUID string or username)
     * @param targetUuid The target player's UUID (may be null)
     * @param targetName The target player's in-game name (may be null)
     * @return true if the identity matches safely
     */
    public static boolean matches(String configuredEntry, UUID targetUuid, String targetName) {
        if (configuredEntry == null || configuredEntry.isBlank()) {
            return false;
        }
        String entryTrimmed = configuredEntry.trim();

        // 1. UUID matching
        if (targetUuid != null) {
            try {
                UUID entryUuid = UUID.fromString(entryTrimmed);
                if (entryUuid.equals(targetUuid)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Not a UUID string, proceed to name matching
            }
        }

        // 2. Name matching
        if (targetName == null || targetName.isBlank()) {
            return false;
        }
        String targetNameTrimmed = targetName.trim();

        // Exact match (case-insensitive)
        if (entryTrimmed.equalsIgnoreCase(targetNameTrimmed)) {
            return true;
        }

        // 3. Bedrock prefix matching
        // Example: entry is ".Steve" and target is ".Steve" (already matched above).
        // If entry is ".Steve" and target is "Steve", or entry is "Steve" and target is ".Steve":
        // Only match if the non-prefixed parts are identical and Bedrock identity is indicated.
        if (isBedrockName(entryTrimmed) && !isBedrockName(targetNameTrimmed)) {
            String strippedEntry = entryTrimmed.substring(1);
            if (strippedEntry.equalsIgnoreCase(targetNameTrimmed)) {
                return true;
            }
        } else if (!isBedrockName(entryTrimmed) && isBedrockName(targetNameTrimmed)) {
            String strippedTarget = targetNameTrimmed.substring(1);
            if (strippedTarget.equalsIgnoreCase(entryTrimmed)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Normalizes a username for storage/comparison.
     */
    public static String normalizeName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
