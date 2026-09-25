package com.example.livestatus;

import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.JsonAccountStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TokenPersistenceTest {

    @TempDir
    Path tempDir;

    private JsonAccountStorage storage;
    private final Logger logger = Logger.getLogger("TestLogger");

    @BeforeEach
    void setUp() {
        this.storage = new JsonAccountStorage(tempDir.toFile(), logger);
    }

    @Test
    @DisplayName("5. Token persistence saves and survives server restart")
    void testTokenPersistenceAndServerRestart() {
        UUID uuid = UUID.randomUUID();
        long now = System.currentTimeMillis();

        AccountRecord record = new AccountRecord(
                uuid,
                "PokeHowlet",
                "UC1234567890",
                "PokeHowlet Official",
                "@pokehowlet",
                "access_token_abc123",
                "refresh_token_xyz987",
                now + 3600_000,
                now,
                now,
                "LINKED"
        );

        storage.saveOrUpdate(record);

        // Verify loaded in memory
        Optional<AccountRecord> loaded = storage.findByUuid(uuid);
        assertTrue(loaded.isPresent());
        assertEquals("UC1234567890", loaded.get().youtubeChannelId());
        assertEquals("PokeHowlet Official", loaded.get().channelTitle());
        assertEquals("refresh_token_xyz987", loaded.get().refreshToken());
        assertFalse(loaded.get().isExpired());

        // Simulate server restart: create a fresh storage instance on the same directory
        JsonAccountStorage restartedStorage = new JsonAccountStorage(tempDir.toFile(), logger);
        Optional<AccountRecord> afterRestart = restartedStorage.findByUuid(uuid);
        assertTrue(afterRestart.isPresent());
        assertEquals(uuid, afterRestart.get().minecraftUuid());
        assertEquals("PokeHowlet Official", afterRestart.get().channelTitle());
        assertEquals("access_token_abc123", afterRestart.get().accessToken());
    }

    @Test
    @DisplayName("6. Token expiration check and token update")
    void testTokenExpirationAndUpdate() {
        UUID uuid = UUID.randomUUID();
        long now = System.currentTimeMillis();

        // Expired token (expires in 10 seconds, within 60s buffer)
        AccountRecord record = new AccountRecord(
                uuid,
                "Steve",
                "UC_STEVE",
                "Steve Channel",
                "@steve",
                "old_expired_token",
                "refresh_steve",
                now + 10_000,
                now,
                now,
                "LINKED"
        );
        storage.saveOrUpdate(record);
        assertTrue(storage.findByUuid(uuid).orElseThrow().isExpired());

        // Refresh token update
        storage.updateTokens(uuid, "new_fresh_token", now + 3600_000);
        AccountRecord refreshed = storage.findByUuid(uuid).orElseThrow();
        assertEquals("new_fresh_token", refreshed.accessToken());
        assertFalse(refreshed.isExpired());
    }

    @Test
    @DisplayName("7. Unlink removes account record from disk")
    void testUnlinkRemoval() {
        UUID uuid = UUID.randomUUID();
        AccountRecord record = new AccountRecord(
                uuid, "Alex", "UC_ALEX", "Alex Channel", null,
                "token", "refresh", System.currentTimeMillis() + 100000,
                System.currentTimeMillis(), System.currentTimeMillis(), "LINKED"
        );
        storage.saveOrUpdate(record);
        assertTrue(storage.findByUuid(uuid).isPresent());

        boolean deleted = storage.deleteByUuid(uuid);
        assertTrue(deleted);
        assertFalse(storage.findByUuid(uuid).isPresent());

        // Verify it remains gone after reload
        storage.load();
        assertFalse(storage.findByUuid(uuid).isPresent());
    }
}

