package com.example.livestatus.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class LiveHistoryTrackerTest {

    private LiveHistoryTracker tracker;
    private LiveHistoryStorage storage;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        storage = new LiveHistoryStorage(tempDir.toFile(), Logger.getLogger("TestLogger"));
        tracker = new LiveHistoryTracker(storage, Logger.getLogger("TestLogger"));
    }

    @Test
    @DisplayName("Stream start adds active session and deduplicates repeat notifications")
    void testStreamStartAndDeduplication() {
        UUID playerUuid = UUID.randomUUID();
        String playerName = "ProGamer";
        String channelId = "UC123456789";
        String channelTitle = "ProGamerOfficial";
        String videoId = "vid-abc-123";
        String streamTitle = "Minecraft Survival Ep 1";

        // First notification
        tracker.onStreamStart(playerUuid, playerName, channelId, channelTitle, videoId, streamTitle);

        assertEquals(1, tracker.getCurrentlyLiveCount());
        assertEquals(1, tracker.getTotalEventsCount());
        assertEquals(1, tracker.getSessionCount(playerUuid));

        // Duplicate polling cycle with same videoId
        tracker.onStreamStart(playerUuid, playerName, channelId, channelTitle, videoId, streamTitle);

        // Count should not duplicate
        assertEquals(1, tracker.getCurrentlyLiveCount());
        assertEquals(1, tracker.getTotalEventsCount(), "Duplicate notifications must not increment total sessions");
    }

    @Test
    @DisplayName("Stream end calculates duration and records to history")
    void testStreamEnd() {
        UUID playerUuid = UUID.randomUUID();
        String playerName = "ProGamer";
        String channelId = "UC123456789";
        String channelTitle = "ProGamerOfficial";
        String videoId = "vid-xyz-789";
        String streamTitle = "Speedrunning Minecraft";

        tracker.onStreamStart(playerUuid, playerName, channelId, channelTitle, videoId, streamTitle);
        assertEquals(1, tracker.getCurrentlyLiveCount());

        // Stream ends
        var endedOpt = tracker.onStreamEnd(playerUuid);
        assertTrue(endedOpt.isPresent());

        assertEquals(0, tracker.getCurrentlyLiveCount(), "Active sessions should be 0 after stream ends");
        assertFalse(tracker.getRecentEvents(10).isEmpty(), "History must contain completed stream record");

        LiveEventRecord record = tracker.getRecentEvents(10).get(0);
        assertEquals("ENDED", record.status());
        assertEquals(videoId, record.videoId());
        assertNotNull(record.endedAt());
        assertTrue(record.durationSeconds() >= 0);
    }
}
