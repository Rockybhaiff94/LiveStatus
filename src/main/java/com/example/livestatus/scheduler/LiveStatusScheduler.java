package com.example.livestatus.scheduler;

import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.nametag.LiveIndicatorRenderer;
import com.example.livestatus.nametag.NametagProvider;
import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.youtube.YouTubeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Asynchronous polling engine that checks active YouTube broadcast status
 * and triggers nametag visual updates on the main thread ONLY when status transitions occur.
 * Avoids overlapping polling cycles.
 */
public class LiveStatusScheduler implements Runnable {

    private final Plugin plugin;
    private final PluginConfig config;
    private final YouTubeService youTubeService;
    private final PlayerLinkService linkService;
    private final com.example.livestatus.player.AllowedPlayersStorage allowedStorage;
    private final com.example.livestatus.storage.AccountStorage accountStorage;
    private final NametagProvider nametagProvider;
    private final LiveIndicatorRenderer indicatorRenderer;
    private final Logger logger;
    private final AtomicBoolean isPolling = new AtomicBoolean(false);
    private BukkitTask task;

    public LiveStatusScheduler(
            Plugin plugin,
            PluginConfig config,
            YouTubeService youTubeService,
            PlayerLinkService linkService,
            NametagProvider nametagProvider,
            LiveIndicatorRenderer indicatorRenderer,
            Logger logger
    ) {
        this(plugin, config, youTubeService, linkService, null, null, nametagProvider, indicatorRenderer, logger);
    }

    public LiveStatusScheduler(
            Plugin plugin,
            PluginConfig config,
            YouTubeService youTubeService,
            PlayerLinkService linkService,
            com.example.livestatus.player.AllowedPlayersStorage allowedStorage,
            com.example.livestatus.storage.AccountStorage accountStorage,
            NametagProvider nametagProvider,
            LiveIndicatorRenderer indicatorRenderer,
            Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.youTubeService = youTubeService;
        this.linkService = linkService;
        this.allowedStorage = allowedStorage;
        this.accountStorage = accountStorage;
        this.nametagProvider = nametagProvider;
        this.indicatorRenderer = indicatorRenderer;
        this.logger = logger;
    }

    public synchronized void start() {
        stop();
        long intervalTicks = config.getPollingIntervalSeconds() * 20L;
        // Run asynchronously so network requests NEVER block the server main thread
        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, 40L, intervalTicks);
    }

    public synchronized void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        isPolling.set(false);
    }

    @Override
    public void run() {
        // Prevent overlapping polling executions
        if (!isPolling.compareAndSet(false, true)) {
            if (config.isDebugEnabled()) {
                logger.info("Skipping YouTube polling cycle: previous cycle is still in progress.");
            }
            return;
        }

        try {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            Set<UUID> targetUuids = new LinkedHashSet<>();
            for (Player player : onlinePlayers) {
                UUID uuid = player.getUniqueId();
                linkService.getOrCreate(uuid, player.getName());
                targetUuids.add(uuid);
            }

            if (config.isAnnounceOfflineStreamers() && allowedStorage != null && accountStorage != null) {
                for (UUID uuid : allowedStorage.getAllowedUuids()) {
                    if (accountStorage.findByUuid(uuid).isPresent()) {
                        targetUuids.add(uuid);
                    }
                }
            }

            if (targetUuids.isEmpty()) {
                isPolling.set(false);
                return;
            }

            // Query YouTube asynchronously in batch
            youTubeService.checkBatchStatus(targetUuids)
                    .thenAccept(results -> {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            UUID uuid = player.getUniqueId();
                            Optional<LinkedPlayer> optLinked = linkService.get(uuid);
                            if (optLinked.isEmpty()) {
                                continue;
                            }

                            LinkedPlayer linked = optLinked.get();
                            // Only perform nametag operations if the LIVE eligibility state actually changed!
                            if (linked.hasVisualIndicatorChanged()) {
                                boolean nowEligible = linked.getStatus().isLiveIndicatorEligible();
                                if (config.isDebugEnabled()) {
                                    logger.info("Player " + player.getName() + " visual state transition: " +
                                            linked.getPreviousStatus() + " -> " + linked.getStatus() +
                                            " (eligible=" + nowEligible + ")");
                                }

                                // Scoreboard modifications must execute on the Minecraft main server thread
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (!player.isOnline()) {
                                        return;
                                    }
                                    if (nowEligible) {
                                        nametagProvider.applyLiveIndicator(player, indicatorRenderer.render(), config.getSeparator());
                                    } else {
                                        nametagProvider.removeLiveIndicator(player);
                                    }
                                });
                            }
                        }
                    })
                    .exceptionally(ex -> {
                        if (config.isDebugEnabled()) {
                            logger.warning("Error during live status polling cycle: " + ex.getMessage());
                        }
                        return null;
                    })
                    .whenComplete((res, ex) -> isPolling.set(false));
        } catch (Throwable t) {
            isPolling.set(false);
            if (config.isDebugEnabled()) {
                logger.warning("Unexpected error during polling cycle: " + t.getMessage());
            }
        }
    }

    /**
     * Immediately checks status for a single player (e.g. on player join).
     */
    public void checkPlayerImmediate(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        linkService.getOrCreate(uuid, player.getName());

        youTubeService.checkStatus(uuid).thenAccept(status -> {
            Optional<LinkedPlayer> opt = linkService.get(uuid);
            if (opt.isPresent() && opt.get().getStatus().isLiveIndicatorEligible()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        nametagProvider.applyLiveIndicator(player, indicatorRenderer.render(), config.getSeparator());
                    }
                });
            }
        });
    }
}

