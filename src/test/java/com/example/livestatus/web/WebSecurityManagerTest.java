package com.example.livestatus.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class WebSecurityManagerTest {

    private WebSecurityManager securityManager;

    @BeforeEach
    void setUp() {
        securityManager = new WebSecurityManager(Logger.getLogger("TestLogger"));
    }

    @Test
    @DisplayName("Generate temporary session returns valid 256-bit token and access code")
    void testGenerateSession() {
        PanelSession session = securityManager.createSession("AdminUser", Duration.ofHours(5));

        assertNotNull(session);
        assertNotNull(session.token());
        assertTrue(session.token().length() >= 40, "Token should be long and cryptographically random");
        assertNotNull(session.accessCode());
        assertTrue(session.accessCode().startsWith("LIVE-"), "Access code should start with LIVE-");
        assertFalse(session.isExpired());
        assertEquals("AdminUser", session.issuer());

        // Validate token lookup
        assertTrue(securityManager.isValidSession(session.token()));
        assertTrue(securityManager.getSession(session.token()).isPresent());
        assertEquals(session.token(), securityManager.getSession(session.token()).get().token());
    }

    @Test
    @DisplayName("Revoke sessions invalidates existing tokens immediately")
    void testRevokeSessions() {
        PanelSession session = securityManager.createSession("Console", Duration.ofHours(2));
        assertTrue(securityManager.isValidSession(session.token()));

        securityManager.revokeAll();
        assertFalse(securityManager.isValidSession(session.token()), "Token must not be valid after revocation");
    }

    @Test
    @DisplayName("Expired sessions are rejected by isValidSession")
    void testExpiredSession() {
        PanelSession expiredSession = new PanelSession(
                "token123",
                "LIVE-ABCD-1234",
                "Tester",
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS)
        );

        assertTrue(expiredSession.isExpired());
    }

    @Test
    @DisplayName("Rate limiting allows normal traffic and throttles bursts")
    void testRateLimiter() {
        String testIp = "192.168.1.100";

        for (int i = 0; i < 30; i++) {
            assertFalse(securityManager.isRateLimited(testIp), "Normal traffic under limit should be permitted");
        }

        // Spam requests up to limit
        int blockedCount = 0;
        for (int i = 0; i < 150; i++) {
            if (securityManager.isRateLimited(testIp)) {
                blockedCount++;
            }
        }
        assertTrue(blockedCount > 0, "Bursts above limit must be throttled");
    }

    @Test
    @DisplayName("IP brute force protection locks out attacker after repeated failures")
    void testIpLockout() {
        String attackerIp = "10.0.0.99";

        assertFalse(securityManager.isIpLockedOut(attackerIp));

        // 5 consecutive failures
        for (int i = 0; i < 5; i++) {
            securityManager.recordFailedAttempt(attackerIp);
        }

        assertTrue(securityManager.isIpLockedOut(attackerIp), "IP must be locked out after 5 failures");

        // Successful login resets
        securityManager.recordSuccessfulAttempt(attackerIp);
        assertFalse(securityManager.isIpLockedOut(attackerIp), "Reset clears lockout");
    }

    @Test
    @DisplayName("Mask secret protects API keys and passwords from exposure")
    void testSecretMasking() {
        assertEquals("••••••••", WebSecurityManager.maskSecret("AIzaSyD-SecretApiKey123456789"));
        assertEquals("Not configured", WebSecurityManager.maskSecret(null));
        assertEquals("Not configured", WebSecurityManager.maskSecret(""));
    }
}
