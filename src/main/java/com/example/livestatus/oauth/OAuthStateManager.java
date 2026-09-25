package com.example.livestatus.oauth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates and validates cryptographically secure OAuth state parameters to prevent CSRF attacks
 * and securely bind OAuth callbacks to the initiating Minecraft player UUID.
 */
public class OAuthStateManager {

    // 5-minute short expiration window as requested
    private static final long STATE_TTL_MS = 5 * 60 * 1000;
    private final byte[] secretKeyBytes;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, StatePayload> activeStates = new ConcurrentHashMap<>();

    public record StatePayload(UUID playerUuid, String playerName, long createdAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > STATE_TTL_MS;
        }
    }

    public OAuthStateManager(String secret) {
        String key = (secret != null && !secret.isBlank()) ? secret : "ytlivetag_default_hmac_secret_2026";
        this.secretKeyBytes = key.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a signed, tamper-proof state token associated with the player UUID.
     */
    public String createState(UUID playerUuid, String playerName) {
        cleanupExpired();

        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String nonceStr = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        long now = System.currentTimeMillis();

        String rawData = playerUuid + ":" + now + ":" + nonceStr;
        String signature = sign(rawData);
        String stateToken = Base64.getUrlEncoder().withoutPadding().encodeToString((rawData + ":" + signature).getBytes(StandardCharsets.UTF_8));

        activeStates.put(stateToken, new StatePayload(playerUuid, playerName, now));
        return stateToken;
    }

    /**
     * Validates that the state token is valid, untampered, non-expired, and returns the associated player payload.
     * Consumes the token upon successful validation (one-time use).
     */
    public StatePayload validateAndConsume(String stateToken) {
        cleanupExpired();

        if (stateToken == null || stateToken.isBlank()) {
            return null;
        }

        StatePayload payload = activeStates.remove(stateToken);
        if (payload == null || payload.isExpired()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(stateToken), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 4) {
                return null;
            }

            String uuidStr = parts[0];
            String timeStr = parts[1];
            String nonceStr = parts[2];
            String expectedSig = parts[3];

            String rawData = uuidStr + ":" + timeStr + ":" + nonceStr;
            String calculatedSig = sign(rawData);

            if (!calculatedSig.equals(expectedSig)) {
                return null;
            }

            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private void cleanupExpired() {
        activeStates.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public int getActiveStateCount() {
        cleanupExpired();
        return activeStates.size();
    }
}

