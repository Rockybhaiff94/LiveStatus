package com.example.livestatus.scheduler;

import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.mcast.MCastApiClient;
import com.example.livestatus.nametag.LiveIndicatorRenderer;
import com.example.livestatus.nametag.NametagProvider;
import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeService;
import com.example.livestatus.youtube.YouTubeStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Asynchronous polling engine that checks active live broadcast status
 * via the centralized MCast API and triggers nametag visual updates on the main thread
 * ONLY when status transitions occur. Avoids overlapping polling cycles.
 */
public class LiveStatusScheduler implements Runnable {

    private final Plugin plugin;
    private final PluginConfig config;
    private final MCastApiClient mcastApiClient;
    private final YouTubeService youTubeService;
    private final PlayerLinkService linkService;
    private final NametagProvider nametagProvider;
    private final LiveIndicatorRenderer indicatorRenderer;
    private final Logger logger;
    private final AtomicBoolean isPolling = new AtomicBoolean(false);
    private BukkitTask task;

    public LiveStatusScheduler(
            Plugin plugin,
            PluginConfig config,
            MCastApiClient mcastApiClient,
            YouTubeService youTubeService,
            PlayerLinkService linkService,
            AllowedPlayersStorage allowedStorage,
            AccountStorage accountStorage,
            NametagProvider nametagProvider,
            LiveIndicatorRenderer indicatorRenderer,
            Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.mcastApiClient = mcastApiClient;
        this.youTubeService = youTubeService;
        this.linkService = linkService;
        this.nametagProvider = nametagProvider;
        this.indicatorRenderer = indicatorRenderer;
        this.logger = logger;
    }

    /**
     * Backward-compatible constructor for testing.
     */
    public LiveStatusScheduler(
            Plugin plugin,
            PluginConfig config,
            YouTubeService youTubeService,
            PlayerLinkService linkService,
            AllowedPlayersStorage allowedStorage,
            AccountStorage accountStorage,
            NametagProvider nametagProvider,
            LiveIndicatorRenderer indicatorRenderer,
            Logger logger
    ) {
        this(plugin, config, null, youTubeService, linkService, allowedStorage, accountStorage, nametagProvider, indicatorRenderer, logger);
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
                logger.info("Skipping polling cycle: previous cycle is still in progress.");
            }
            return;
        }

        try {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            Set<UUID> targetUuids = new LinkedHashSet<>();
            for (Player player : onlinePlayers) {
                UUID uuid = player.getUniqueId();
                if (config.isRequireAdminApproval() && !linkService.isAuthorizedToLink(uuid)) {
                    continue; // Skip players not authorized by administrator
                }
                linkService.getOrCreate(uuid, player.getName());
                targetUuids.add(uuid);
            }

            // Strictly on-demand: Zero network requests when no authorized players are online
            if (targetUuids.isEmpty()) {
                isPolling.set(false);
                return;
            }

            // 1. Centralized MCast batch status check
            if (mcastApiClient != null) {
                mcastApiClient.fetchBatchStatus(targetUuids)
                        .thenAccept(results -> {
                            for (Map.Entry<UUID, MCastApiClient.PlayerLiveStatus> entry : results.entrySet()) {
                                UUID uuid = entry.getKey();
                                MCastApiClient.PlayerLiveStatus status = entry.getValue();

                                if (status.linked()) {
                                    if (status.channelId() != null && !status.channelId().isBlank()) {
                                        YouTubeAccount ytAccount = new YouTubeAccount(status.channelId(), status.channelTitle(), null);
                                        linkService.updateAccount(uuid, true, ytAccount);
                                    }
                                } else if (status.status() == YouTubeStatus.NOT_LINKED && (status.error() == null || status.error().isBlank())) {
                                    linkService.updateAccount(uuid, false, null);
                                }
                                linkService.updateStatus(uuid, status.status(), status.error());
                            }

                            syncVisualIndicators();
                        })
                        .exceptionally(ex -> {
                            if (config.isDebugEnabled()) {
                                logger.warning("Error during MCast live status polling: " + ex.getMessage());
                            }
                            return null;
                        })
                        .whenComplete((res, ex) -> isPolling.set(false));
                return;
            }

            // 2. Legacy fallback if MCast client not configured
            if (youTubeService != null) {
                youTubeService.checkBatchStatus(targetUuids)
                        .thenAccept(results -> syncVisualIndicators())
                        .exceptionally(ex -> {
                            if (config.isDebugEnabled()) {
                                logger.warning("Error during live status polling cycle: " + ex.getMessage());
                            }
                            return null;
                        })
                        .whenComplete((res, ex) -> isPolling.set(false));
                return;
            }

            isPolling.set(false);
        } catch (Throwable t) {
            isPolling.set(false);
            if (config.isDebugEnabled()) {
                logger.warning("Unexpected error during polling cycle: " + t.getMessage());
            }
        }
    }

    private void syncVisualIndicators() {
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

        if (mcastApiClient != null) {
            mcastApiClient.fetchBatchStatus(List.of(uuid)).thenAccept(results -> {
                MCastApiClient.PlayerLiveStatus s = results.get(uuid);
                if (s != null) {
                    if (s.linked()) {
                        if (s.channelId() != null && !s.channelId().isBlank()) {
                            linkService.updateAccount(uuid, true, new YouTubeAccount(s.channelId(), s.channelTitle(), null));
                        }
                    } else if (s.status() == YouTubeStatus.NOT_LINKED && (s.error() == null || s.error().isBlank())) {
                        linkService.updateAccount(uuid, false, null);
                    }
                    linkService.updateStatus(uuid, s.status(), s.error());
                    if (s.status().isLiveIndicatorEligible()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                nametagProvider.applyLiveIndicator(player, indicatorRenderer.render(), config.getSeparator());
                            }
                        });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                nametagProvider.removeLiveIndicator(player);
                            }
                        });
                    }
                }
            });
            return;
        }

        if (youTubeService != null) {
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
}
