package com.example.livestatus;

import com.example.livestatus.chat.AnnouncementFormatter;
import com.example.livestatus.chat.AnnouncementScheduler;
import com.example.livestatus.chat.LiveAnnouncementManager;
import com.example.livestatus.command.LinkYoutubeCommand;
import com.example.livestatus.command.YoutubeCommand;
import com.example.livestatus.compatibility.PlaceholderApiHook;
import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.mcast.MCastApiClient;
import com.example.livestatus.mcast.MCastRegistrationService;
import com.example.livestatus.mcast.MCastServerIdentity;
import com.example.livestatus.nametag.*;
import com.example.livestatus.oauth.GoogleOAuthClient;
import com.example.livestatus.oauth.OAuthCallbackServer;
import com.example.livestatus.oauth.OAuthService;
import com.example.livestatus.oauth.OAuthStateManager;
import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.scheduler.LiveStatusScheduler;
import com.example.livestatus.stats.PluginStatistics;
import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.storage.JsonAccountStorage;
import com.example.livestatus.storage.StreamerConfigStore;
import com.example.livestatus.youtube.*;
import com.example.livestatus.history.LiveHistoryStorage;
import com.example.livestatus.history.LiveHistoryTracker;
import com.example.livestatus.premium.PaymentWebhookHandler;
import com.example.livestatus.premium.PremiumService;
import com.example.livestatus.premium.PremiumStorage;
import com.example.livestatus.tracker.ServerLinkStorage;
import com.example.livestatus.tracker.ServerLinkTracker;
import com.example.livestatus.web.AuditLogger;
import com.example.livestatus.web.WebSecurityManager;
import com.example.livestatus.web.WebServerManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Main self-contained Paper plugin class for LiveStatus.
 * Handles embedded OAuth 2.0 callbacks, token storage, YouTube API queries,
 * live nametag indicators, and server-hosted Web Control Panel.
 */
public class LiveStatusPlugin extends JavaPlugin implements Listener {

    private PluginConfig pluginConfig;
    private AllowedPlayersStorage allowedStorage;
    private AccountStorage accountStorage;
    private StreamerConfigStore streamerConfigStore;
    private PluginStatistics statistics;
    private PlayerLinkService linkService;

    // Web Panel & Extended Systems
    private LiveHistoryStorage liveHistoryStorage;
    private LiveHistoryTracker liveHistoryTracker;
    private ServerLinkStorage serverLinkStorage;
    private ServerLinkTracker serverLinkTracker;
    private PremiumStorage premiumStorage;
    private PremiumService premiumService;
    private PaymentWebhookHandler paymentWebhookHandler;
    private WebSecurityManager webSecurityManager;
    private AuditLogger auditLogger;
    private WebServerManager webServerManager;

    // MCast Centralized Architecture
    private MCastServerIdentity serverIdentity;
    private MCastApiClient mcastApiClient;
    private MCastRegistrationService registrationService;

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
    private AnnouncementFormatter announcementFormatter;
    private LiveAnnouncementManager announcementManager;
    private AnnouncementScheduler announcementScheduler;
    private PlaceholderApiHook placeholderApiHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(getConfig());

        // 1. Initialize Persistent Storages & Player Link Service
        this.allowedStorage = new AllowedPlayersStorage(getDataFolder(), getLogger());
        JsonAccountStorage jsonStorage = new JsonAccountStorage(getDataFolder(), getLogger());
        jsonStorage.sanitizeLegacyTokens();
        this.accountStorage = jsonStorage;
        this.streamerConfigStore = new StreamerConfigStore(getDataFolder(), getLogger());
        this.statistics = new PluginStatistics();
        this.linkService = new PlayerLinkService(allowedStorage, accountStorage);

        // Extended Systems: History, Server Links, Premium, Web Security & Audit
        this.liveHistoryStorage = new LiveHistoryStorage(getDataFolder(), getLogger());
        this.liveHistoryTracker = new LiveHistoryTracker(liveHistoryStorage, getLogger());
        this.serverLinkStorage = new ServerLinkStorage(getDataFolder(), getLogger());
        this.serverLinkTracker = new ServerLinkTracker(serverLinkStorage, getLogger());
        this.premiumStorage = new PremiumStorage(getDataFolder(), getLogger());
        this.premiumService = new PremiumService(premiumStorage, getLogger());
        this.paymentWebhookHandler = new PaymentWebhookHandler(premiumService, pluginConfig.getPaymentWebhookSecret(), getLogger());
        this.webSecurityManager = new WebSecurityManager(getLogger());
        this.auditLogger = new AuditLogger(getDataFolder(), getLogger());

        // 1b. Initialize MCast Central Identity & API Client
        this.serverIdentity = new MCastServerIdentity(getDataFolder(), getLogger());
        this.mcastApiClient = new MCastApiClient(pluginConfig.getMcastApiUrl(), serverIdentity, getLogger());
        this.registrationService = new MCastRegistrationService(this, mcastApiClient, getLogger());
        this.mcastApiClient.setOnUnregistered(() -> this.registrationService.registerNow());
        this.registrationService.start();

        // Preload stored accounts into in-memory link service
        for (AccountRecord record : accountStorage.getAllAccounts()) {
            if (record.youtubeChannelId() != null && !record.youtubeChannelId().isBlank()) {
                LinkedPlayer lp = linkService.getOrCreate(record.minecraftUuid(), record.playerName());
                YouTubeAccount ytAccount = new YouTubeAccount(record.youtubeChannelId(), record.channelTitle(), record.customUrl());
                lp.setLinked(true);
                lp.setAccount(ytAccount);
            }
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

        // 4. Initialize OAuth In-Game Service (Centralized MCast Architecture)
        this.oAuthService = new OAuthService(
                this,
                pluginConfig,
                mcastApiClient,
                accountStorage,
                linkService,
                youTubeService,
                nametagProvider,
                getLogger()
        );

        // Embedded callback server is deprecated and only started if legacy client-id is explicitly configured
        if (pluginConfig.getClientId() != null && !pluginConfig.getClientId().isBlank() && pluginConfig.getCallbackPort() > 0) {
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
        }

        // 5. Initialize Live Chat Announcement System
        this.announcementFormatter = new AnnouncementFormatter(pluginConfig, getLogger());
        this.announcementManager = new LiveAnnouncementManager(
                this,
                pluginConfig,
                youTubeService,
                allowedStorage,
                accountStorage,
                streamerConfigStore,
                announcementFormatter,
                statistics,
                getLogger()
        );
        this.announcementManager.setHistoryTracker(this.liveHistoryTracker);
        this.announcementScheduler = new AnnouncementScheduler(this, announcementManager, getLogger());

        // 6. Initialize Server-Hosted Web Control Panel
        this.webServerManager = new WebServerManager(
                this,
                pluginConfig,
                webSecurityManager,
                liveHistoryTracker,
                serverLinkTracker,
                premiumService,
                paymentWebhookHandler,
                auditLogger,
                getLogger()
        );
        this.webServerManager.start();

        // 7. Initialize Optional PlaceholderAPI Integration
        if (pluginConfig.isPlaceholderApiEnabled()) {
            this.placeholderApiHook = new PlaceholderApiHook(linkService, pluginConfig.getLiveText(), getLogger());
            this.placeholderApiHook.register();
        }

        // 7. Register Commands
        registerCommands();

        // 8. Register Event Listeners
        getServer().getPluginManager().registerEvents(this, this);

        // 9. Start Asynchronous Polling Engines (Batch MCast Querying)
        this.scheduler = new LiveStatusScheduler(
                this,
                pluginConfig,
                mcastApiClient,
                youTubeService,
                linkService,
                allowedStorage,
                accountStorage,
                nametagProvider,
                indicatorRenderer,
                getLogger()
        );
        this.scheduler.start();
        this.announcementScheduler.start();

        // 10. Startup summary
        logStartupSummary();
    }

    @Override
    public void onDisable() {
        if (registrationService != null) {
            registrationService.stop();
        }
        if (webServerManager != null) {
            webServerManager.stop();
        }
        if (liveHistoryStorage != null) {
            liveHistoryStorage.save();
        }
        if (serverLinkStorage != null) {
            serverLinkStorage.save();
        }
        if (premiumStorage != null) {
            premiumStorage.save();
        }
        if (callbackServer != null) {
            callbackServer.stop();
        }
        if (announcementScheduler != null) {
            announcementScheduler.stop();
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
        if (streamerConfigStore != null) {
            streamerConfigStore.save();
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
            YoutubeCommand handler = new YoutubeCommand(this, linkService, youTubeService, announcementManager, streamerConfigStore);
            ytCmd.setExecutor(handler);
            ytCmd.setTabCompleter(handler);
        }

        PluginCommand ytliveCmd = getCommand("ytlive");
        if (ytliveCmd != null) {
            YoutubeCommand handler = new YoutubeCommand(this, linkService, youTubeService, announcementManager, streamerConfigStore);
            ytliveCmd.setExecutor(handler);
            ytliveCmd.setTabCompleter(handler);
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
        getLogger().info("MCast LiveStatus v" + getPluginMeta().getVersion());
        getLogger().info("Server Installation ID: " + (serverIdentity != null ? serverIdentity.getServerId() : "Unassigned"));
        getLogger().info("MCast Central API: " + (mcastApiClient != null ? mcastApiClient.getBaseUrl() : "https://api.mcast.fun"));
        getLogger().info("Centralized Google OAuth: ACTIVE (Managed via mcast.fun)");
        getLogger().info("Nametag Provider: " + pluginConfig.getNametagProvider() + " (enabled=" + pluginConfig.isNametagEnabled() + ")");
        getLogger().info("Live Polling Interval: " + pluginConfig.getPollingIntervalSeconds() + "s");
        getLogger().info("Admin Authorized Players: " + allowedStorage.getAllowedUuids().size());
        getLogger().info("Linked Accounts in Storage: " + accountStorage.getAllAccounts().size());
        getLogger().info("Chat Announcements: " + (pluginConfig.isChatEnabled() && pluginConfig.isAdminChatEnabled() ? "ENABLED" : "DISABLED") +
                " (min-gap=" + pluginConfig.getGlobalMinimumGapSeconds() + "s, default=" + pluginConfig.getDefaultIntervalMinutes() + "m)");
        getLogger().info("PlaceholderAPI: " + (placeholderApiHook != null && placeholderApiHook.isHooked() ? "Hooked" : "Not hooked"));
        getLogger().info("==================================================");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        this.pluginConfig = new PluginConfig(getConfig());
        this.allowedStorage.load();
        this.accountStorage.load();
        if (streamerConfigStore != null) {
            this.streamerConfigStore.load();
        }

        if (callbackServer != null) {
            callbackServer.stop();
            callbackServer.start();
        }

        if (scheduler != null) {
            scheduler.start();
        }

        if (announcementScheduler != null) {
            announcementScheduler.start();
        }

        if (registrationService != null) {
            registrationService.registerNow();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (nametagProvider != null) {
            nametagProvider.onViewerJoin(player);
        }
        if (scheduler != null) {
            scheduler.checkPlayerImmediate(player);
            scheduler.syncVisualIndicators();
        }
        if (serverLinkTracker != null) {
            serverLinkTracker.recordJoin(player.getUniqueId(), player.getName(), "default", "direct_join");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (nametagProvider != null) {
            nametagProvider.cleanUp(event.getPlayer());
        }
    }

    public LiveStatusScheduler getLiveStatusScheduler() {
        return scheduler;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public PlayerLinkService getLinkService() {
        return linkService;
    }

    public AllowedPlayersStorage getAllowedStorage() {
        return allowedStorage;
    }

    public AccountStorage getAccountStorage() {
        return accountStorage;
    }

    public StreamerConfigStore getStreamerConfigStore() {
        return streamerConfigStore;
    }

    public LiveAnnouncementManager getAnnouncementManager() {
        return announcementManager;
    }

    public AnnouncementScheduler getAnnouncementScheduler() {
        return announcementScheduler;
    }

    public PluginStatistics getStatistics() {
        return statistics;
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

    public WebSecurityManager getWebSecurityManager() {
        return webSecurityManager;
    }

    public LiveHistoryTracker getLiveHistoryTracker() {
        return liveHistoryTracker;
    }

    public ServerLinkTracker getServerLinkTracker() {
        return serverLinkTracker;
    }

    public PremiumService getPremiumService() {
        return premiumService;
    }

    public PaymentWebhookHandler getPaymentWebhookHandler() {
        return paymentWebhookHandler;
    }

    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    public WebServerManager getWebServerManager() {
        return webServerManager;
    }

    public MCastServerIdentity getServerIdentity() {
        return serverIdentity;
    }

    public MCastApiClient getMcastApiClient() {
        return mcastApiClient;
    }

    public MCastRegistrationService getRegistrationService() {
        return registrationService;
    }
}


