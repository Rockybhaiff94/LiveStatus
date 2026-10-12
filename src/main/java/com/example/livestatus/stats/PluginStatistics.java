package com.example.livestatus.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe statistics tracking for the YouTube live announcement system.
 */
public class PluginStatistics {

    private final AtomicInteger sessionAnnouncements = new AtomicInteger(0);
    private final AtomicInteger todayAnnouncements = new AtomicInteger(0);
    private volatile LocalDate todayDate = LocalDate.now();
    private volatile Instant lastAnnouncementTime;
    private volatile String lastAnnouncedStreamer;

    public void recordAnnouncement(String streamerName) {
        sessionAnnouncements.incrementAndGet();

        LocalDate now = LocalDate.now();
        synchronized (this) {
            if (!now.equals(todayDate)) {
                todayDate = now;
                todayAnnouncements.set(0);
            }
            todayAnnouncements.incrementAndGet();
            this.lastAnnouncementTime = Instant.now();
            this.lastAnnouncedStreamer = streamerName;
        }
    }

    public int getSessionAnnouncements() {
        return sessionAnnouncements.get();
    }

    public int getTodayAnnouncements() {
        LocalDate now = LocalDate.now();
        synchronized (this) {
            if (!now.equals(todayDate)) {
                todayDate = now;
                todayAnnouncements.set(0);
            }
            return todayAnnouncements.get();
        }
    }

    public Instant getLastAnnouncementTime() {
        return lastAnnouncementTime;
    }

    public String getLastAnnouncedStreamer() {
        return lastAnnouncedStreamer;
    }

    public void resetSession() {
        sessionAnnouncements.set(0);
    }
}
