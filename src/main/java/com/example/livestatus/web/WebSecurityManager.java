package com.example.livestatus.web;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles temporary panel session generation, cryptographically secure tokens,
 * automatic session expiration, rate limiting, and brute-force IP lockout protection.
 */
public class WebSecurityManager {

    private final SecureRandom secureRandom = new SecureRandom();
    private final Logger logger;

    // Active temporary sessions: token -> PanelSession
    private final Map<String, PanelSession> activeSessions = new ConcurrentHashMap<>();

    // Rate limiting tracking: IP -> list of request timestamps
    private final Map<String, Queue<Instant>> requestBuckets = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 120;

    // Brute force / login attempts lockout: IP -> LockoutState
    private static class LockoutState {
        int failedAttempts = 0;
        Instant lockedUntil = Instant.EPOCH;
    }
    private final Map<String, LockoutState> ipLockouts = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    public WebSecurityManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Generates a new 256-bit cryptographically secure random session token and human-readable access code.
     */
    public synchronized PanelSession createSession(String issuer, Duration validityDuration) {
        // 32 bytes = 256 bits
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);

        // Generate clean access code: e.g. "LIVE-7A39-4B81"
        byte[] codeBytes = new byte[4];
        secureRandom.nextBytes(codeBytes);
        String hex = HexFormat.of().formatHex(codeBytes).toUpperCase(Locale.ROOT);
        String accessCode = "LIVE-" + hex.substring(0, 4) + "-" + hex.substring(4, 8);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(validityDuration != null ? validityDuration : Duration.ofHours(5));

        PanelSession session = new PanelSession(token, accessCode, issuer != null ? issuer : "Console", now, expiresAt);
        activeSessions.put(token, session);

        if (logger != null) {
            logger.info("[WebSecurity] Generated temporary panel session for " + issuer + " (Expires in " +
                    validityDuration.toHours() + "h)");
        }
        return session;
    }

    /**
     * Validates if a token is present, non-null, and non-expired.
     */
    public boolean isValidSession(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        PanelSession session = activeSessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.isExpired()) {
            activeSessions.remove(token);
            return false;
        }
        return true;
    }

    public Optional<PanelSession> getSession(String token) {
        if (token == null) return Optional.empty();
        PanelSession session = activeSessions.get(token);
        if (session != null && session.isExpired()) {
            activeSessions.remove(token);
            return Optional.empty();
        }
        return Optional.ofNullable(session);
    }

    public synchronized void revokeAll() {
        int count = activeSessions.size();
        activeSessions.clear();
        if (logger != null) {
            logger.info("[WebSecurity] Revoked all " + count + " active panel sessions.");
        }
    }

    public synchronized boolean revokeSession(String token) {
        return activeSessions.remove(token) != null;
    }

    public int getActiveSessionCount() {
        cleanExpiredSessions();
        return activeSessions.size();
    }

    public void cleanExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Checks if IP is currently rate-limited (sliding window of 1 minute).
     */
    public boolean isRateLimited(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minus(1, java.time.temporal.ChronoUnit.MINUTES);

        Queue<Instant> timestamps = requestBuckets.computeIfAbsent(ipAddress, k -> new LinkedList<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peek().isBefore(oneMinuteAgo)) {
                timestamps.poll();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                return true;
            }
            timestamps.add(now);
            return false;
        }
    }

    /**
     * Checks if IP is currently locked out due to brute force login attempts.
     */
    public boolean isIpLockedOut(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) return false;
        LockoutState state = ipLockouts.get(ipAddress);
        if (state == null) return false;
        if (Instant.now().isBefore(state.lockedUntil)) {
            return true;
        }
        // Lock expired
        if (state.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            ipLockouts.remove(ipAddress);
        }
        return false;
    }

    public void recordFailedAttempt(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) return;
        LockoutState state = ipLockouts.computeIfAbsent(ipAddress, k -> new LockoutState());
        synchronized (state) {
            state.failedAttempts++;
            if (state.failedAttempts >= MAX_FAILED_ATTEMPTS) {
                state.lockedUntil = Instant.now().plus(LOCKOUT_DURATION);
                if (logger != null) {
                    logger.warning("[WebSecurity] IP " + ipAddress + " locked out for 15 minutes due to multiple failed panel authentication attempts.");
                }
            }
        }
    }

    public void recordSuccessfulAttempt(String ipAddress) {
        if (ipAddress != null) {
            ipLockouts.remove(ipAddress);
        }
    }

    /**
     * Utility to mask secret credentials when returning configuration to the web frontend.
     */
    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "Not configured";
        }
        return "••••••••";
    }
}
