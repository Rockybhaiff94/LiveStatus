package com.example.livestatus;

import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.youtube.YouTubeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTransitionTest {

    @Test
    @DisplayName("16. Visual indicator change detection verifies state transitions")
    void testStateTransitions() {
        LinkedPlayer player = new LinkedPlayer(UUID.randomUUID(), "PokeHowlet");

        // Initial state UNKNOWN
        assertFalse(player.getStatus().isLiveIndicatorEligible());

        // UNKNOWN -> OFFLINE: neither is eligible -> no visual change
        player.setStatus(YouTubeStatus.OFFLINE);
        assertFalse(player.hasVisualIndicatorChanged());

        // OFFLINE -> LIVE_NOT_MINECRAFT: neither is eligible -> no visual change
        player.setStatus(YouTubeStatus.LIVE_NOT_MINECRAFT);
        assertFalse(player.hasVisualIndicatorChanged());

        // LIVE_NOT_MINECRAFT -> LIVE_MINECRAFT: false -> true -> VISUAL CHANGE!
        player.setStatus(YouTubeStatus.LIVE_MINECRAFT);
        assertTrue(player.hasVisualIndicatorChanged());

        // LIVE_MINECRAFT -> LIVE_MINECRAFT: true -> true -> NO change (deduplication)
        player.setStatus(YouTubeStatus.LIVE_MINECRAFT);
        assertFalse(player.hasVisualIndicatorChanged());

        // LIVE_MINECRAFT -> OFFLINE: true -> false -> VISUAL CHANGE! (removes LIVE)
        player.setStatus(YouTubeStatus.OFFLINE);
        assertTrue(player.hasVisualIndicatorChanged());

        // OFFLINE -> OFFLINE: false -> false -> NO change
        player.setStatus(YouTubeStatus.OFFLINE);
        assertFalse(player.hasVisualIndicatorChanged());
    }

    @Test
    @DisplayName("17. Only LIVE_MINECRAFT is eligible for LIVE indicator")
    void testEligibility() {
        assertFalse(YouTubeStatus.NOT_LINKED.isLiveIndicatorEligible());
        assertFalse(YouTubeStatus.OFFLINE.isLiveIndicatorEligible());
        assertFalse(YouTubeStatus.LIVE_NOT_MINECRAFT.isLiveIndicatorEligible());
        assertFalse(YouTubeStatus.UNKNOWN.isLiveIndicatorEligible());
        assertTrue(YouTubeStatus.LIVE_MINECRAFT.isLiveIndicatorEligible());
    }
}

