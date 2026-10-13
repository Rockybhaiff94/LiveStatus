package com.example.livestatus.mcast;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Handles automatic registration and periodic heartbeats between this Paper server
 * and the MCast centralized backend.
 */
public class MCastRegistrationService {

    private final Plugin plugin;
    private final MCastApiClient apiClient;
    private final Logger logger;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    private BukkitTask heartbeatTask;
    private BukkitTask retryTask;

    public MCastRegistrationService(Plugin plugin, MCastApiClient apiClient, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Starts the registration sequence asynchronously with retry logic.
     */
    public void start() {
        attemptRegistration(1);
    }

    private void attemptRegistration(int attempt) {
        String serverName = Bukkit.getServer().getName();
        String pluginVersion = plugin.getPluginMeta().getVersion();
        String paperVersion = Bukkit.getVersion();
        List<String> capabilities = List.of("nametag", "chat_announcements", "papi", "link_sessions", "batch_status");

        apiClient.registerServer(serverName, pluginVersion, paperVersion, capabilities)
                .thenAccept(success -> {
                    if (success) {
                        registered.set(true);
                        startHeartbeat();
                    } else {
                        scheduleRetry(attempt);
                    }
                })
                .exceptionally(ex -> {
                    scheduleRetry(attempt);
                    return null;
                });
    }

    private void scheduleRetry(int attempt) {
        // Exponential backoff: 15s, 30s, 60s, up to 300s
        long delaySeconds = Math.min(300L, 15L * (1L << Math.min(attempt, 4)));
        logger.info("MCast backend registration attempt " + attempt + " failed. Retrying in " + delaySeconds + " seconds...");

        if (retryTask != null && !retryTask.isCancelled()) {
            retryTask.cancel();
        }

        retryTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (!registered.get()) {
                attemptRegistration(attempt + 1);
            }
        }, delaySeconds * 20L);
    }

    private void startHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel();
        }

        // Heartbeat every 5 minutes (6000 ticks)
        heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (registered.get()) {
                apiClient.sendHeartbeat();
            }
        }, 6000L, 6000L);
    }

    public void stop() {
        if (retryTask != null && !retryTask.isCancelled()) {
            retryTask.cancel();
            retryTask = null;
        }
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    public boolean isRegistered() {
        return registered.get();
    }
}
