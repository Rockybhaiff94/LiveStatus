package com.example.livestatus.oauth;

import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.nametag.NametagProvider;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.youtube.YouTubeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.awt.*;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Orchestrates OAuth authorization requests and unlinking directly inside the Paper plugin.
 */
public class OAuthService {

    private final PluginConfig config;
    private final OAuthStateManager stateManager;
    private final GoogleOAuthClient oAuthClient;
    private final AccountStorage accountStorage;
    private final PlayerLinkService linkService;
    private final YouTubeService youTubeService;
    private final NametagProvider nametagProvider;
    private final Logger logger;

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
        this.config = Objects.requireNonNull(config, "config");
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager");
        this.oAuthClient = Objects.requireNonNull(oAuthClient, "oAuthClient");
        this.accountStorage = Objects.requireNonNull(accountStorage, "accountStorage");
        this.linkService = Objects.requireNonNull(linkService, "linkService");
        this.youTubeService = Objects.requireNonNull(youTubeService, "youTubeService");
        this.nametagProvider = Objects.requireNonNull(nametagProvider, "nametagProvider");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Initiates the YouTube linking process for a player.
     * Enforces explicit admin authorization and generates a secure one-time OAuth link.
     */
    public CompletableFuture<Void> startLinkFlow(Player player) {
        if (!linkService.isAuthorizedToLink(player.getUniqueId())) {
            player.sendMessage(Component.text("You are not authorized to link a YouTube account. Please contact a server administrator.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(null);
        }

        if (!config.isOAuthConfigured()) {
            player.sendMessage(Component.text("YouTube integration is not yet configured with Google API credentials. Please contact an administrator.", NamedTextColor.RED));
            return CompletableFuture.completedFuture(null);
        }

        player.sendMessage(Component.text("Generating secure YouTube authorization link...", NamedTextColor.GRAY));
        logger.info("Generating OAuth authorization link for player: " + player.getName() + " (" + player.getUniqueId() + ")");

        return CompletableFuture.runAsync(() -> {
            String stateToken = stateManager.createState(player.getUniqueId(), player.getName());
            String authUrl = oAuthClient.buildAuthorizationUrl(stateToken);

            Component header = Component.text("[LiveStatus]", NamedTextColor.RED, TextDecoration.BOLD);
            Component line1 = Component.text("Click here to connect your YouTube account:", NamedTextColor.GOLD);
            Component button = Component.text("[ CONNECT YOUTUBE ]", NamedTextColor.RED, TextDecoration.BOLD, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(authUrl))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to open Google OAuth authorization in your web browser", NamedTextColor.YELLOW)));
            Component footer = Component.text("This link expires in 5 minutes.", NamedTextColor.DARK_GRAY);

            player.sendMessage(Component.empty());
            player.sendMessage(header);
            player.sendMessage(line1);
            player.sendMessage(button);
            player.sendMessage(footer);
            player.sendMessage(Component.empty());

            // Attempt local browser launch ONLY if running in local desktop environment and player is localhost
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    String host = player.getAddress() != null ? player.getAddress().getHostString() : "";
                    if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "0:0:0:0:0:0:0:1".equals(host)) {
                        Desktop.getDesktop().browse(URI.create(authUrl));
                    }
                }
            } catch (Throwable t) {
                logger.fine("Could not launch system browser for OAuth flow: " + t.getMessage());
            }
        });
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
            boolean deleted = accountStorage.deleteByUuid(player.getUniqueId());
            youTubeService.invalidateCache(player.getUniqueId());
            linkService.unlink(player.getUniqueId());
            logger.info("Unlinked YouTube account for player: " + player.getName() + " (" + player.getUniqueId() + ")");

            player.sendMessage(Component.text("Your YouTube account has been unlinked successfully.", NamedTextColor.GREEN));
            return deleted;
        });
    }

    /**
     * Unlinks an account by UUID directly (e.g. from an admin command).
     */
    public boolean unlinkByUuid(java.util.UUID uuid) {
        boolean deleted = accountStorage.deleteByUuid(uuid);
        youTubeService.invalidateCache(uuid);
        linkService.unlink(uuid);
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            nametagProvider.removeLiveIndicator(online);
        }
        logger.info("Unlinked YouTube account for UUID: " + uuid);
        return deleted;
    }
}

