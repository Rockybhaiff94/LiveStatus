package com.example.livestatus;

import com.example.livestatus.chat.IntervalMode;
import com.example.livestatus.chat.StreamerChatSettings;
import com.example.livestatus.storage.StreamerConfigStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class StreamerConfigStoreTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("12. Streamer settings persist by UUID across reload and restart")
    void testPersistenceAcrossRestart() {
        Logger logger = Logger.getLogger("Test");
        StreamerConfigStore store1 = new StreamerConfigStore(tempDir, logger);

        UUID uuid = UUID.randomUUID();
        StreamerChatSettings settings = store1.getOrCreate(uuid, "PGLUOG");
        settings.setEnabled(true);
        settings.setIntervalMode(IntervalMode.CUSTOM);
        settings.setCustomIntervalMinutes(7);
        settings.setCustomTitle("We are going to dominate this SMP!");
        settings.setAdminDisabled(false);
        store1.saveOrUpdate(settings);

        // Simulate server restart by creating a new store instance reading the same directory
        StreamerConfigStore store2 = new StreamerConfigStore(tempDir, logger);
        Optional<StreamerChatSettings> loaded = store2.get(uuid);

        assertTrue(loaded.isPresent());
        StreamerChatSettings s = loaded.get();
        assertEquals(uuid, s.getUuid());
        assertEquals("PGLUOG", s.getPlayerName());
        assertTrue(s.isEnabled());
        assertEquals(IntervalMode.CUSTOM, s.getIntervalMode());
        assertEquals(7, s.getCustomIntervalMinutes());
        assertEquals("We are going to dominate this SMP!", s.getCustomTitle());
        assertFalse(s.isAdminDisabled());
        assertTrue(s.isEffectivelyEnabled());
    }

    @Test
    @DisplayName("13. Settings remain attached to UUID when player changes username")
    void testUsernameChangePreservesSettings() {
        Logger logger = Logger.getLogger("Test");
        StreamerConfigStore store = new StreamerConfigStore(tempDir, logger);

        UUID uuid = UUID.randomUUID();
        StreamerChatSettings settings = store.getOrCreate(uuid, "OldName");
        settings.setCustomTitle("Old SMP Series");
        settings.setIntervalMode(IntervalMode.RANDOM);
        store.saveOrUpdate(settings);

        // Player rejoins with new username
        StreamerChatSettings retrieved = store.getOrCreate(uuid, "NewName");
        retrieved.setPlayerName("NewName");
        store.saveOrUpdate(retrieved);

        // Verify settings are intact with new name
        assertEquals("NewName", retrieved.getPlayerName());
        assertEquals("Old SMP Series", retrieved.getCustomTitle());
        assertEquals(IntervalMode.RANDOM, retrieved.getIntervalMode());
    }

    @Test
    @DisplayName("14. Admin disabled override takes precedence over streamer setting")
    void testAdminDisabledOverride() {
        Logger logger = Logger.getLogger("Test");
        StreamerConfigStore store = new StreamerConfigStore(tempDir, logger);

        UUID uuid = UUID.randomUUID();
        StreamerChatSettings settings = store.getOrCreate(uuid, "Player1");
        settings.setEnabled(true);
        settings.setAdminDisabled(true); // Admin overrides
        store.saveOrUpdate(settings);

        assertFalse(settings.isEffectivelyEnabled());

        // Admin re-enables
        settings.setAdminDisabled(false);
        store.saveOrUpdate(settings);
        assertTrue(settings.isEffectivelyEnabled());
    }
}
