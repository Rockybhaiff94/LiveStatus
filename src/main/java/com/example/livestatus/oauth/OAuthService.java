package com.example.livestatus.oauth;

import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.mcast.MCastApiClient;
import com.example.livestatus.nametag.NametagProvider;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.awt.*;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Orchestrates OAuth authorization requests and unlinking via the centralized MCast API.
 * Eliminates local Google credentials from Minecraft servers.
 */
public class OAuthService {

    private final Plugin plugin;
    private final PluginConfig config;
    private final MCastApiClient mcastApiClient;
    private final OAuthStateManager stateManager;
    private final GoogleOAuthClient oAuthClient;
    private final AccountStorage accountStorage;
    private final PlayerLinkService linkService;
    private final YouTubeService youTubeService;
    private final NametagProvider nametagProvider;
    private final Logger logger;

    public OAuthService(
            Plugin plugin,
            PluginConfig config,
            MCastApiClient mcastApiClient,
            AccountStorage accountStorage,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            NametagProvider nametagProvider,
            Logger logger
    ) {
        this.plugin = plugin;
        this.config = Objects.requireNonNull(config, "config");
        this.mcastApiClient = mcastApiClient;
        this.stateManager = null;
        this.oAuthClient = null;
        this.accountStorage = Objects.requireNonNull(accountStorage, "accountStorage");
        this.linkService = Objects.requireNonNull(linkService, "linkService");
        this.youTubeService = youTubeService;
        this.nametagProvider = Objects.requireNonNull(nametagProvider, "nametagProvider");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Backward-compatible constructor for testing.
     */
    public OAuthService(
            PluginConfig config,
            OAuthStateManager stateManager,
            GoogleOAuthClient oAuthClient,
            AccountStorage accountStorage,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            NametagProvider nametagProvider,
            Logger logger
    ) {
        this.plugin = null;
        this.config = Objects.requireNonNull(config, "config");
        this.mcastApiClient = null;
        this.stateManager = stateManager;
        this.oAuthClient = oAuthClient;
        this.accountStorage = Objects.requireNonNull(accountStorage, "accountStorage");
        this.linkService = Objects.requireNonNull(linkService, "linkService");
        this.youTubeService = youTubeService;
        this.nametagProvider = Objects.requireNonNull(nametagProvider, "nametagProvider");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Initiates the YouTube linking process for a player.
     * Enforces explicit admin authorization and creates a single-use 5-minute linking session via MCast API.
     */
    public CompletableFuture<Void> startLinkFlow(Player player) {
        if (config.isRequireAdminApproval() && !linkService.isAuthorizedToLink(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not authorized to link a YouTube account. Please contact a server administrator.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(null);
        }

        player.sendMessage(Component.text("Generating secure YouTube authorization link...", NamedTextColor.GRAY));
        logger.info("Generating linking session for player: " + player.getName() + " (" + player.getUniqueId() + ")");

        // Centralized MCast linking flow
        if (mcastApiClient != null) {
            return mcastApiClient.createLinkSession(player.getUniqueId(), player.getName())
                    .thenAccept(response -> {
                        if (response == null) {
                            // Fallback to direct OAuth if MCast central API is unreachable
                            if (stateManager != null && oAuthClient != null) {
                                String stateToken = stateManager.createState(player.getUniqueId(), player.getName());
                                String authUrl = oAuthClient.buildAuthorizationUrl(stateToken);
                                sendLinkChatPrompt(player, authUrl);
                                return;
                            }
                            player.sendMessage(Component.text("Failed to create linking session with MCast API. Please try again shortly.", NamedTextColor.RED));
                            return;
                        }

                        sendLinkChatPrompt(player, response.linkUrl());
                        startSessionCompletionPoller(player.getUniqueId(), player.getName(), response.sessionId());
                    });
        }

        // Fallback for standalone/legacy mode if mcastApiClient is null
        if (stateManager != null && oAuthClient != null) {
            return CompletableFuture.runAsync(() -> {
                String stateToken = stateManager.createState(player.getUniqueId(), player.getName());
                String authUrl = oAuthClient.buildAuthorizationUrl(stateToken);
                sendLinkChatPrompt(player, authUrl);
            });
        }

        player.sendMessage(Component.text("Linking service is currently unavailable.", NamedTextColor.RED));
        return CompletableFuture.completedFuture(null);
    }

    private void sendLinkChatPrompt(Player player, String authUrl) {
        Component header = Component.text("[MCast LiveStatus]", NamedTextColor.RED, TextDecoration.BOLD);
        Component line1 = Component.text("Click here to connect your YouTube account:", NamedTextColor.GOLD);
        Component button = Component.text("[ CONNECT YOUTUBE ]", NamedTextColor.RED, TextDecoration.BOLD, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(authUrl))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open MCast Google authorization in your browser", NamedTextColor.YELLOW)));
        Component footer = Component.text("This link expires in 5 minutes.", NamedTextColor.DARK_GRAY);

        player.sendMessage(Component.empty());
        player.sendMessage(header);
        player.sendMessage(line1);
        player.sendMessage(button);
        player.sendMessage(footer);
        player.sendMessage(Component.empty());

        // Attempt system browser launch if running locally on desktop
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                String host = player.getAddress() != null ? player.getAddress().getHostString() : "";
                if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "0:0:0:0:0:0:0:1".equals(host)) {
                    Desktop.getDesktop().browse(URI.create(authUrl));
                }
            }
        } catch (Throwable t) {
            logger.fine("Could not launch system browser for linking flow: " + t.getMessage());
        }
    }

    /**
     * Polls the MCast API every 3 seconds for session completion while the player is linking.
     */
    private void startSessionCompletionPoller(UUID playerUuid, String playerName, String sessionId) {
        if (plugin == null || mcastApiClient == null) {
            return;
        }

        AtomicInteger polls = new AtomicInteger(0);
        BukkitTask[] pollerTask = new BukkitTask[1];

        pollerTask[0] = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Stop after 100 polls (5 minutes = 300 seconds)
            if (polls.incrementAndGet() > 100) {
                if (pollerTask[0] != null) pollerTask[0].cancel();
                return;
            }

            mcastApiClient.pollLinkSession(sessionId).thenAccept(status -> {
                if (status == null) return;

                if (status.isCompleted()) {
                    if (pollerTask[0] != null) pollerTask[0].cancel();

                    String channelTitle = status.channelTitle() != null ? status.channelTitle() : "YouTube Channel";
                    YouTubeAccount ytAccount = new YouTubeAccount(status.channelId(), channelTitle, status.customUrl());

                    linkService.updateAccount(playerUuid, true, ytAccount);

                    // Persist public metadata in local storage (sanitized of tokens)
                    AccountRecord record = new AccountRecord(
                            playerUuid,
                            playerName,
                            status.channelId(),
                            channelTitle,
                            status.customUrl(),
                            "", // No tokens stored locally
                            "", // No tokens stored locally
                            0L,
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            "ACTIVE"
                    );
                    accountStorage.saveOrUpdate(record);

                    Player onlinePlayer = Bukkit.getPlayer(playerUuid);
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        onlinePlayer.sendMessage(Component.empty());
                        onlinePlayer.sendMessage(Component.text("✔ YouTube account (", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .append(Component.text(channelTitle, NamedTextColor.GOLD))
                                .append(Component.text(") has been successfully linked!", NamedTextColor.GREEN, TextDecoration.BOLD)));
                        onlinePlayer.sendMessage(Component.text("Your nametag will automatically display LIVE when streaming Minecraft.", NamedTextColor.GRAY));
                        onlinePlayer.sendMessage(Component.empty());
                    }
                    logger.info("Successfully linked YouTube channel '" + channelTitle + "' (" + status.channelId() + ") for player " + playerName);
                } else if (status.isExpired()) {
                    if (pollerTask[0] != null) pollerTask[0].cancel();
                }
            });
        }, 60L, 60L); // 3 seconds = 60 ticks
    }

    /**
     * Unlinks the player's YouTube account and immediately strips the LIVE indicator.
     */
    public CompletableFuture<Boolean> unlink(Player player) {
        player.sendMessage(Component.text("Unlinking your YouTube account...", NamedTextColor.GRAY));
        if (player.isOnline()) {
            nametagProvider.removeLiveIndicator(player);
        }

        return CompletableFuture.supplyAsync(() -> {
            if (mcastApiClient != null) {
                mcastApiClient.unlinkPlayer(player.getUniqueId());
            }
            boolean deleted = accountStorage.deleteByUuid(player.getUniqueId());
            if (youTubeService != null) {
                youTubeService.invalidateCache(player.getUniqueId());
            }
            linkService.unlink(player.getUniqueId());
            logger.info("Unlinked YouTube account for player: " + player.getName() + " (" + player.getUniqueId() + ")");

            player.sendMessage(Component.text("Your YouTube account has been unlinked successfully.", NamedTextColor.GREEN));
            return deleted;
        });
    }

    /**
     * Unlinks an account by UUID directly (e.g. from an admin command).
     */
    public boolean unlinkByUuid(UUID uuid) {
        if (mcastApiClient != null) {
            mcastApiClient.unlinkPlayer(uuid);
        }
        boolean deleted = accountStorage.deleteByUuid(uuid);
        if (youTubeService != null) {
            youTubeService.invalidateCache(uuid);
        }
        linkService.unlink(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            nametagProvider.removeLiveIndicator(online);
        }
        logger.info("Unlinked YouTube account for UUID: " + uuid);
        return deleted;
    }
}
