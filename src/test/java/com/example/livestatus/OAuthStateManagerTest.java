package com.example.livestatus;

import com.example.livestatus.oauth.OAuthStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OAuthStateManagerTest {

    private OAuthStateManager stateManager;

    @BeforeEach
    void setUp() {
        this.stateManager = new OAuthStateManager("plugin_secure_test_hmac_secret_2026");
    }

    @Test
    @DisplayName("1. OAuth state creation binds player UUID and name securely")
    void testStateCreationAndBinding() {
        UUID playerUuid = UUID.randomUUID();
        String playerName = "PokeHowlet";

        String stateToken = stateManager.createState(playerUuid, playerName);
        assertNotNull(stateToken);
        assertFalse(stateToken.isBlank());

        OAuthStateManager.StatePayload payload = stateManager.validateAndConsume(stateToken);
        assertNotNull(payload);
        assertEquals(playerUuid, payload.playerUuid());
        assertEquals(playerName, payload.playerName());
        assertFalse(payload.isExpired());
    }

    @Test
    @DisplayName("2. OAuth state is one-time use (anti-replay)")
    void testOneTimeUse() {
        UUID playerUuid = UUID.randomUUID();
        String stateToken = stateManager.createState(playerUuid, "Steve");

        assertNotNull(stateManager.validateAndConsume(stateToken));
        // Second attempt must return null
        assertNull(stateManager.validateAndConsume(stateToken));
    }

    @Test
    @DisplayName("3. Rejects tampered OAuth state tokens")
    void testTamperedState() {
        UUID playerUuid = UUID.randomUUID();
        String stateToken = stateManager.createState(playerUuid, "Alex");

        String tampered = stateToken.substring(0, stateToken.length() - 5) + "ABCDE";
        assertNull(stateManager.validateAndConsume(tampered));
    }

    @Test
    @DisplayName("4. Rejects null, blank, or malformed state tokens")
    void testMalformedState() {
        assertNull(stateManager.validateAndConsume(null));
        assertNull(stateManager.validateAndConsume(""));
        assertNull(stateManager.validateAndConsume("not-a-valid-token"));
    }
}

