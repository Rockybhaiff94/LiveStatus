package com.example.livestatus;

import com.example.livestatus.youtube.StreamEligibilityDetector;
import com.example.livestatus.youtube.TopicCategoryDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftLiveDetectionTest {

    private TopicCategoryDetector detector;

    @BeforeEach
    void setUp() {
        this.detector = new TopicCategoryDetector(true, true, false);
    }

    @Test
    @DisplayName("8. Active live Minecraft broadcast qualifies as ELIGIBLE_MINECRAFT_LIVE")
    void testActiveMinecraftStream() {
        StreamEligibilityDetector.BroadcastMetadata live = new StreamEligibilityDetector.BroadcastMetadata(
                "bc_1", "Minecraft 1.21 Hardcore Survival", "live", "public"
        );
        StreamEligibilityDetector.VideoMetadata video = new StreamEligibilityDetector.VideoMetadata(
                "bc_1", "20", List.of(TopicCategoryDetector.MINECRAFT_TOPIC_WIKI),
                "Minecraft 1.21 Hardcore Survival", "Playing Minecraft!"
        );

        assertEquals(StreamEligibilityDetector.Eligibility.ELIGIBLE_MINECRAFT_LIVE, detector.detectEligibility(live, video));
    }

    @Test
    @DisplayName("9. Active live GTA V stream is detected as NOT_MINECRAFT (suppresses LIVE tag)")
    void testActiveGtaStream() {
        StreamEligibilityDetector.BroadcastMetadata live = new StreamEligibilityDetector.BroadcastMetadata(
                "bc_2", "GTA V Online Heists", "live", "public"
        );
        StreamEligibilityDetector.VideoMetadata video = new StreamEligibilityDetector.VideoMetadata(
                "bc_2", "20", List.of("https://en.wikipedia.org/wiki/Grand_Theft_Auto_V"),
                "GTA V Online Heists", "Playing GTA V!"
        );

        assertEquals(StreamEligibilityDetector.Eligibility.NOT_MINECRAFT, detector.detectEligibility(live, video));
    }

    @Test
    @DisplayName("10. Active live non-gaming stream is detected as NOT_MINECRAFT")
    void testActiveNonGamingStream() {
        StreamEligibilityDetector.BroadcastMetadata live = new StreamEligibilityDetector.BroadcastMetadata(
                "bc_3", "Just Chatting & Tech Talk", "live", "public"
        );
        // Category 24 = Entertainment
        StreamEligibilityDetector.VideoMetadata video = new StreamEligibilityDetector.VideoMetadata(
                "bc_3", "24", List.of(), "Just Chatting", "Casual talk"
        );

        assertEquals(StreamEligibilityDetector.Eligibility.NOT_MINECRAFT, detector.detectEligibility(live, video));
    }

    @Test
    @DisplayName("11. Scheduled upcoming stream is detected as NOT_LIVE")
    void testScheduledStreamNotLive() {
        StreamEligibilityDetector.BroadcastMetadata upcoming = new StreamEligibilityDetector.BroadcastMetadata(
                "bc_4", "Minecraft Stream Tomorrow", "ready", "public"
        );
        StreamEligibilityDetector.VideoMetadata video = new StreamEligibilityDetector.VideoMetadata(
                "bc_4", "20", List.of(TopicCategoryDetector.MINECRAFT_TOPIC_WIKI),
                "Minecraft Stream Tomorrow", "Scheduled"
        );

        assertEquals(StreamEligibilityDetector.Eligibility.NOT_LIVE, detector.detectEligibility(upcoming, video));
    }

    @Test
    @DisplayName("12. Completed stream is detected as NOT_LIVE")
    void testCompletedStreamNotLive() {
        StreamEligibilityDetector.BroadcastMetadata completed = new StreamEligibilityDetector.BroadcastMetadata(
                "bc_5", "Minecraft Stream Ended", "complete", "public"
        );
        StreamEligibilityDetector.VideoMetadata video = new StreamEligibilityDetector.VideoMetadata(
                "bc_5", "20", List.of(TopicCategoryDetector.MINECRAFT_TOPIC_WIKI),
                "Minecraft Stream Ended", "VOD"
        );

        assertEquals(StreamEligibilityDetector.Eligibility.NOT_LIVE, detector.detectEligibility(completed, video));
    }

    @Test
    @DisplayName("13. Missing video metadata fails closed to UNKNOWN")
    void testMissingVideoFailsClosed() {
        StreamEligibilityDetector.BroadcastMetadata live = new StreamEligibilityDetector.BroadcastMetadata(
                "bc_6", "Stream", "live", "public"
        );

        assertEquals(StreamEligibilityDetector.Eligibility.UNKNOWN, detector.detectEligibility(live, null));
    }
}

