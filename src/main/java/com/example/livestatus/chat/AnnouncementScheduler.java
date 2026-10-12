package com.example.livestatus.chat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Single centralized repeating scheduler for YouTube Live stream chat announcements.
 * Periodically triggers evaluation across all eligible streamers.
 * Strictly avoids creating one repeating task per streamer.
 */
public class AnnouncementScheduler implements Runnable {

    private final Plugin plugin;
    private final LiveAnnouncementManager announcementManager;
    private final Logger logger;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private BukkitTask task;

    public AnnouncementScheduler(Plugin plugin, LiveAnnouncementManager announcementManager, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.announcementManager = Objects.requireNonNull(announcementManager, "announcementManager");
        this.logger = logger;
    }

    public synchronized void start() {
        stop();
        // Evaluate every 40 ticks (2 seconds)
        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, 60L, 40L);
        if (logger != null) {
            logger.fine("YouTube live chat announcement scheduler started.");
        }
    }

    public synchronized void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        isRunning.set(false);
    }

    @Override
    public void run() {
        if (!isRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            announcementManager.evaluateAndAnnounceNext();
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("Error in announcement scheduler execution: " + t.getMessage());
            }
        } finally {
            isRunning.set(false);
        }
    }

    public boolean isActive() {
        return task != null && !task.isCancelled();
    }
}
