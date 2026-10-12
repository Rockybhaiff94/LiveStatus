package com.example.livestatus;

import com.example.livestatus.stats.PluginStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PluginStatisticsTest {

    @Test
    @DisplayName("15. Statistics records session and daily announcement counts accurately")
    void testStatisticsTracking() {
        PluginStatistics stats = new PluginStatistics();

        assertEquals(0, stats.getSessionAnnouncements());
        assertEquals(0, stats.getTodayAnnouncements());
        assertNull(stats.getLastAnnouncementTime());
        assertNull(stats.getLastAnnouncedStreamer());

        stats.recordAnnouncement("PGLUOG");
        assertEquals(1, stats.getSessionAnnouncements());
        assertEquals(1, stats.getTodayAnnouncements());
        assertNotNull(stats.getLastAnnouncementTime());
        assertEquals("PGLUOG", stats.getLastAnnouncedStreamer());

        stats.recordAnnouncement("Player2");
        assertEquals(2, stats.getSessionAnnouncements());
        assertEquals(2, stats.getTodayAnnouncements());
        assertEquals("Player2", stats.getLastAnnouncedStreamer());

        stats.resetSession();
        assertEquals(0, stats.getSessionAnnouncements());
        assertEquals(2, stats.getTodayAnnouncements()); // Daily count preserved across session reset
    }
}
