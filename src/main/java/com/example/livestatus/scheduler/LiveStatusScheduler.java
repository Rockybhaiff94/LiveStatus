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
 * via the centralized MCast API and reliably reconciles nametag visual indicators
 * on the main server thread.
 */
public class LiveStatusScheduler implements Runnable {

    private final Plugin plugin;
    private final PluginConfig config;
    private final MCastApiClient mcastApiClient;
    private final YouTubeService youTubeService;
    private final PlayerLinkService linkService;
    private final AllowedPlayersStorage allowedStorage;
    private final AccountStorage accountStorage;
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
        this.allowedStorage = allowedStorage;
        this.accountStorage = accountStorage;
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
                logger.info("[LiveStatus] Skipping polling cycle: previous cycle is still in progress.");
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
                syncVisualIndicators();
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
                                YouTubeStatus effectiveStatus = status.status();
                                if (!config.isRequireMinecraft() && status.isLive()) {
                                    effectiveStatus = YouTubeStatus.LIVE_MINECRAFT;
                                }
                                linkService.updateStatus(uuid, effectiveStatus, status.error());
                            }

                            syncVisualIndicators();
                        })
                        .exceptionally(ex -> {
                            if (config.isDebugEnabled()) {
                                logger.warning("[LiveStatus] Error during MCast live status polling: " + ex.getMessage());
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
                                logger.warning("[LiveStatus] Error during live status polling cycle: " + ex.getMessage());
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
                logger.warning("[LiveStatus] Unexpected error during polling cycle: " + t.getMessage());
            }
        }
    }

    /**
     * Determines whether a player should currently display the LIVE indicator nametag.
     */
    public boolean isEligibleForLiveIndicator(Player player, LinkedPlayer linked) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!config.isNametagEnabled()) {
            return false;
        }
        if (linked == null || !linked.isLinked()) {
            if (accountStorage == null || accountStorage.findByUuid(player.getUniqueId()).isEmpty()) {
                return false;
            }
        }
        if (config.isRequireAdminApproval()) {
            if (allowedStorage != null && !allowedStorage.isAllowed(player.getUniqueId())) {
                return false;
            }
            if (linkService != null && !linkService.isAuthorizedToLink(player.getUniqueId())) {
                return false;
            }
        }
        YouTubeStatus status = linked != null ? linked.getStatus() : null;
        return status != null && status.isLiveIndicatorEligible();
    }

    /**
     * Reconciles visual indicators across all online players on the Minecraft server main thread.
     * Safely checks desired vs actual indicator state without relying exclusively on status transitions.
     */
    public void syncVisualIndicators() {
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                Optional<LinkedPlayer> optLinked = linkService.get(uuid);
                LinkedPlayer linked = optLinked.orElse(null);

                boolean desired = isEligibleForLiveIndicator(player, linked);
                boolean actual = nametagProvider.hasLiveIndicator(player);

                if (desired) {
                    nametagProvider.applyLiveIndicator(player, indicatorRenderer.render(), config.getSeparator());
                    if (config.isDebugEnabled() && !actual) {
                        logger.info(String.format(
                                "[LiveStatus] Applied LIVE indicator to %s (%s) [status=%s, authorized=%s, linked=%s]",
                                player.getName(), uuid, linked != null ? linked.getStatus() : "null",
                                (!config.isRequireAdminApproval() || linkService.isAuthorizedToLink(uuid)),
                                (linked != null && linked.isLinked())
                        ));
                    }
                } else if (actual) {
                    nametagProvider.removeLiveIndicator(player);
                    if (config.isDebugEnabled()) {
                        logger.info(String.format(
                                "[LiveStatus] Removed LIVE indicator from %s (%s) [status=%s, authorized=%s, linked=%s]",
                                player.getName(), uuid, linked != null ? linked.getStatus() : "null",
                                (!config.isRequireAdminApproval() || linkService.isAuthorizedToLink(uuid)),
                                (linked != null && linked.isLinked())
                        ));
                    }
                }
            }
        });
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
                    YouTubeStatus effectiveStatus = s.status();
                    if (!config.isRequireMinecraft() && s.isLive()) {
                        effectiveStatus = YouTubeStatus.LIVE_MINECRAFT;
                    }
                    linkService.updateStatus(uuid, effectiveStatus, s.error());
                    syncVisualIndicators();
                }
            }).exceptionally(ex -> {
                if (config.isDebugEnabled()) {
                    logger.warning("[LiveStatus] Error during immediate status check for " + player.getName() + ": " + ex.getMessage());
                }
                return null;
            });
            return;
        }

        if (youTubeService != null) {
            youTubeService.checkStatus(uuid).thenAccept(status -> {
                syncVisualIndicators();
            }).exceptionally(ex -> {
                if (config.isDebugEnabled()) {
                    logger.warning("[LiveStatus] Error checking status for " + player.getName() + ": " + ex.getMessage());
                }
                return null;
            });
        }
    }
}
