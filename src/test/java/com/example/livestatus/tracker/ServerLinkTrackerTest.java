package com.example.livestatus.tracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ServerLinkTrackerTest {

    private ServerLinkTracker tracker;
    private ServerLinkStorage storage;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        storage = new ServerLinkStorage(tempDir.toFile(), Logger.getLogger("TestLogger"));
        tracker = new ServerLinkTracker(storage, Logger.getLogger("TestLogger"));
    }

    @Test
    @DisplayName("Track join records join event and updates time-based aggregations")
    void testTrackJoin() {
        tracker.createOrUpdateLink("youtube_promo", "YT Promo", "mc.myserver.net");
        tracker.recordClick("youtube_promo");

        UUID player1 = UUID.randomUUID();
        tracker.recordJoin(player1, "NewPlayer1", "youtube_promo", "youtube");

        UUID player2 = UUID.randomUUID();
        tracker.recordJoin(player2, "NewPlayer2", "discord_invite", "discord");

        assertEquals(2, tracker.getTotalJoins());
        assertEquals(2, tracker.getJoinsToday());
        assertEquals(2, tracker.getJoinsThisWeek());
        assertEquals(2, tracker.getJoinsThisMonth());

        assertEquals(2, tracker.getRecentJoins(10).size());
        assertEquals("NewPlayer2", tracker.getRecentJoins(10).get(0).playerName());
    }

    @Test
    @DisplayName("Link creation and click tracking correctly updates link metrics")
    void testLinkMetrics() {
        tracker.createOrUpdateLink("twitch_campaign", "Twitch Campaign", "mc.myserver.net");

        var linkOpt = tracker.getLink("twitch_campaign");
        assertTrue(linkOpt.isPresent());
        assertEquals(0, linkOpt.get().clicksCount());

        tracker.recordClick("twitch_campaign");
        tracker.recordClick("twitch_campaign");

        var updatedOpt = tracker.getLink("twitch_campaign");
        assertTrue(updatedOpt.isPresent());
        assertEquals(2, updatedOpt.get().clicksCount());
    }
}
