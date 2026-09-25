package com.example.livestatus;

import com.example.livestatus.command.LinkYoutubeCommand;
import com.example.livestatus.command.YoutubeCommand;
import com.example.livestatus.compatibility.PlaceholderApiHook;
import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.nametag.*;
import com.example.livestatus.oauth.GoogleOAuthClient;
import com.example.livestatus.oauth.OAuthCallbackServer;
import com.example.livestatus.oauth.OAuthService;
import com.example.livestatus.oauth.OAuthStateManager;
import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.scheduler.LiveStatusScheduler;
import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.storage.JsonAccountStorage;
import com.example.livestatus.youtube.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Main self-contained Paper plugin class for LiveStatus.
 * Handles embedded OAuth 2.0 callbacks, token storage, YouTube API queries, and live nametag indicators.
 */
public class LiveStatusPlugin extends JavaPlugin implements Listener {

    private PluginConfig pluginConfig;
    private AllowedPlayersStorage allowedStorage;
    private AccountStorage accountStorage;
    private PlayerLinkService linkService;
    private OAuthStateManager stateManager;
    private GoogleOAuthClient oAuthClient;
    private YouTubeApiClient youTubeApiClient;
    private TopicCategoryDetector eligibilityDetector;
    private YouTubeService youTubeService;
    private NametagProvider nametagProvider;
    private LiveIndicatorRenderer indicatorRenderer;
    private OAuthService oAuthService;
    private OAuthCallbackServer callbackServer;
    private LiveStatusScheduler scheduler;
    private PlaceholderApiHook placeholderApiHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(getConfig());

        // 1. Initialize Persistent Storages & Player Link Service
        this.allowedStorage = new AllowedPlayersStorage(getDataFolder(), getLogger());
        this.accountStorage = new JsonAccountStorage(getDataFolder(), getLogger());
        this.linkService = new PlayerLinkService(allowedStorage);

        // Preload stored accounts into in-memory link service
        for (AccountRecord record : accountStorage.getAllAccounts()) {
            YouTubeAccount ytAccount = new YouTubeAccount(record.youtubeChannelId(), record.channelTitle(), record.customUrl());
            linkService.updateAccount(record.minecraftUuid(), true, ytAccount);
        }

        // 2. Initialize OAuth & YouTube Detection Services
        this.stateManager = new OAuthStateManager(pluginConfig.getStateSecret());
        this.oAuthClient = new GoogleOAuthClient(pluginConfig, getLogger());
        this.youTubeApiClient = new YouTubeApiClient(pluginConfig, getLogger());
        this.eligibilityDetector = new TopicCategoryDetector(
                pluginConfig.isRequireGamingCategory(),
                pluginConfig.isRequireMinecraft(),
                pluginConfig.isAllowTitleFallback()
        );
        this.youTubeService = new YouTubeService(
                accountStorage,
                linkService,
                oAuthClient,
                youTubeApiClient,
                eligibilityDetector,
                pluginConfig,
                getLogger()
        );

        // 3. Initialize Nametag Provider and Indicator Renderer
        this.indicatorRenderer = new StaticLiveIndicatorRenderer(pluginConfig.getLiveFormat(), pluginConfig.getLiveText());
        this.nametagProvider = createNametagProvider();

        // 4. Initialize OAuth In-Game Service & Embedded Callback Server
        this.oAuthService = new OAuthService(
                pluginConfig,
                stateManager,
                oAuthClient,
                accountStorage,
                linkService,
                youTubeService,
                nametagProvider,
                getLogger()
        );

        this.callbackServer = new OAuthCallbackServer(
                this,
                pluginConfig,
                stateManager,
                oAuthClient,
                youTubeApiClient,
                accountStorage,
                linkService,
                youTubeService,
                getLogger()
        );
        this.callbackServer.start();

        // 5. Initialize Optional PlaceholderAPI Integration
        if (pluginConfig.isPlaceholderApiEnabled()) {
            this.placeholderApiHook = new PlaceholderApiHook(linkService, pluginConfig.getLiveText(), getLogger());
            this.placeholderApiHook.register();
        }

        // 6. Register Commands
        registerCommands();

        // 7. Register Event Listeners
        getServer().getPluginManager().registerEvents(this, this);

        // 8. Start Asynchronous Polling Engine
        this.scheduler = new LiveStatusScheduler(
                this,
                pluginConfig,
                youTubeService,
                linkService,
                nametagProvider,
                indicatorRenderer,
                getLogger()
        );
        this.scheduler.start();

        // 9. Startup summary
        logStartupSummary();
    }

    @Override
    public void onDisable() {
        if (callbackServer != null) {
            callbackServer.stop();
        }
        if (scheduler != null) {
            scheduler.stop();
        }
        if (nametagProvider != null) {
            nametagProvider.cleanUpAll();
        }
        if (placeholderApiHook != null) {
            placeholderApiHook.unregister();
        }
        if (linkService != null) {
            linkService.clear();
        }
        getLogger().info("LiveStatus disabled cleanly. All indicators removed and callback receiver stopped.");
    }

    private NametagProvider createNametagProvider() {
        if (!pluginConfig.isNametagEnabled()) {
            return new PlaceholderProvider();
        }

        return switch (pluginConfig.getNametagProvider()) {
            case "placeholder" -> new PlaceholderProvider();
            case "composite" -> new CompositeNametagProvider(List.of(
                    new PaperTeamNametagProvider(getLogger()),
                    new PlaceholderProvider()
            ));
            default -> new PaperTeamNametagProvider(getLogger());
        };
    }

    private void registerCommands() {
        PluginCommand ytCmd = getCommand("youtube");
        if (ytCmd != null) {
            YoutubeCommand handler = new YoutubeCommand(this, linkService, youTubeService);
            ytCmd.setExecutor(handler);
            ytCmd.setTabCompleter(handler);
        }

        PluginCommand linkCmd = getCommand("link");
        if (linkCmd != null) {
            LinkYoutubeCommand handler = new LinkYoutubeCommand(oAuthService);
            linkCmd.setExecutor(handler);
            linkCmd.setTabCompleter(handler);
        }
    }

    private void logStartupSummary() {
        getLogger().info("==================================================");
        getLogger().info("LiveStatus v" + getPluginMeta().getVersion() + " (Self-Contained)");
        getLogger().info("OAuth Configured: " + (pluginConfig.isOAuthConfigured() ? "YES" : "NO (Set client-id & client-secret in config.yml)"));
        getLogger().info("Redirect URI: " + pluginConfig.getRedirectUri());
        getLogger().info("Callback Port: " + pluginConfig.getCallbackPort());
        getLogger().info("Nametag Provider: " + pluginConfig.getNametagProvider() + " (enabled=" + pluginConfig.isNametagEnabled() + ")");
        getLogger().info("Polling Interval: " + pluginConfig.getPollingIntervalSeconds() + "s");
        getLogger().info("Admin Authorized Players: " + allowedStorage.getAllowedUuids().size());
        getLogger().info("Linked Accounts in Storage: " + accountStorage.getAllAccounts().size());
        getLogger().info("PlaceholderAPI: " + (placeholderApiHook != null && placeholderApiHook.isHooked() ? "Hooked" : "Not hooked"));
        getLogger().info("==================================================");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        this.pluginConfig = new PluginConfig(getConfig());
        this.allowedStorage.load();
        this.accountStorage.load();

        if (callbackServer != null) {
            callbackServer.stop();
            callbackServer.start();
        }

        if (scheduler != null) {
            scheduler.start();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (scheduler != null) {
            scheduler.checkPlayerImmediate(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (nametagProvider != null) {
            nametagProvider.cleanUp(event.getPlayer());
        }
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public PlayerLinkService getLinkService() {
        return linkService;
    }

    public AccountStorage getAccountStorage() {
        return accountStorage;
    }

    public YouTubeService getYouTubeService() {
        return youTubeService;
    }

    public OAuthService getOAuthService() {
        return oAuthService;
    }

    public NametagProvider getNametagProvider() {
        return nametagProvider;
    }
}

