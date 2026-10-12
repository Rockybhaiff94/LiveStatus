package com.example.livestatus.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strongly-typed configuration for the self-contained LiveStatus plugin.
 * Supports environment variable substitution in string properties using ${ENV_VAR} syntax.
 */
public class PluginConfig {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final int callbackPort;
    private final String callbackHost;
    private final int requestTimeoutMs;
    private final String stateSecret;

    private final int pollingIntervalSeconds;

    private final boolean nametagEnabled;
    private final String nametagProvider;
    private final String liveText;
    private final String liveFormat;
    private final String separator;

    private final boolean requireGamingCategory;
    private final boolean requireMinecraft;
    private final boolean allowTitleFallback;

    private final boolean failClosed;
    private final boolean placeholderApiEnabled;
    private final boolean debugEnabled;

    // Chat Announcement Configuration
    private final boolean chatEnabled;
    private final int globalMinimumGapSeconds;
    private final int defaultIntervalMinutes;
    private final int minimumIntervalMinutes;
    private final int maximumIntervalMinutes;
    private final boolean announceOfflineStreamers;
    private final boolean randomIntervalEnabled;
    private final int randomMinimumMinutes;
    private final int randomMaximumMinutes;
    private final java.util.List<String> announcementMessageLines;
    private volatile boolean adminChatEnabled;
    private final boolean streamerCanDisable;

    // Web Control Panel Configuration
    private final boolean webEnabled;
    private final String webHost;
    private final int webPort;
    private final int webLinkExpirationHours;
    private final boolean webSslEnabled;
    private final String webSslKeystorePath;
    private final String webSslKeystorePassword;

    // Premium & Payment Configuration
    private final boolean premiumEnabled;
    private final String paymentWebhookSecret;

    // Dynamically updatable settings
    private volatile String liveTextMutable;
    private volatile String liveFormatMutable;
    private volatile int pollingIntervalSecondsMutable;
    private volatile int globalMinimumGapSecondsMutable;
    private volatile int defaultIntervalMinutesMutable;

    public PluginConfig(FileConfiguration config) {
        this.clientId = resolveEnv(config.getString("youtube.client-id", ""));
        this.clientSecret = resolveEnv(config.getString("youtube.client-secret", ""));
        this.redirectUri = resolveEnv(config.getString("youtube.redirect-uri", "http://localhost:8080/oauth/callback"));
        this.callbackHost = config.getString("youtube.callback-host", "0.0.0.0");
        this.requestTimeoutMs = Math.max(1000, config.getInt("youtube.request-timeout-ms", 5000));
        this.stateSecret = resolveEnv(config.getString("youtube.state-secret", "livestatus_secure_random_seed"));

        // Determine callback port: explicit config or parse from redirectUri
        int port = config.getInt("youtube.callback-port", -1);
        if (port <= 0) {
            try {
                URI uri = URI.create(this.redirectUri);
                port = uri.getPort() > 0 ? uri.getPort() : (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80);
            } catch (Exception e) {
                port = 8080;
            }
        }
        this.callbackPort = port;

        this.pollingIntervalSeconds = Math.max(5, config.getInt("polling.interval-seconds", 30));
        this.pollingIntervalSecondsMutable = this.pollingIntervalSeconds;

        this.nametagEnabled = config.getBoolean("nametag.enabled", true);
        this.nametagProvider = config.getString("nametag.provider", "team").toLowerCase();
        this.liveText = config.getString("nametag.live-text", "LIVE");
        this.liveTextMutable = this.liveText;
        this.liveFormat = config.getString("nametag.live-format", "<red><bold>LIVE</bold></red>");
        this.liveFormatMutable = this.liveFormat;
        this.separator = config.getString("nametag.separator", " ");

        this.requireGamingCategory = config.getBoolean("detection.require-gaming-category", true);
        this.requireMinecraft = config.getBoolean("detection.require-minecraft", true);
        this.allowTitleFallback = config.getBoolean("detection.allow-title-fallback", false);

        this.failClosed = config.getBoolean("security.fail-closed", true);

        this.placeholderApiEnabled = config.getBoolean("compatibility.placeholderapi", true);
        this.debugEnabled = config.getBoolean("debug.enabled", false);

        // Chat announcement parsing
        this.chatEnabled = config.getBoolean("chat.enabled", true);
        this.globalMinimumGapSeconds = Math.max(5, config.getInt("chat.global-minimum-gap-seconds", 60));
        this.globalMinimumGapSecondsMutable = this.globalMinimumGapSeconds;
        this.minimumIntervalMinutes = Math.max(1, config.getInt("chat.minimum-interval-minutes", 5));
        this.maximumIntervalMinutes = Math.max(this.minimumIntervalMinutes, config.getInt("chat.maximum-interval-minutes", 1440));
        int defInt = config.getInt("chat.default-interval-minutes", 15);
        this.defaultIntervalMinutes = Math.max(this.minimumIntervalMinutes, Math.min(this.maximumIntervalMinutes, defInt));
        this.defaultIntervalMinutesMutable = this.defaultIntervalMinutes;
        this.announceOfflineStreamers = config.getBoolean("chat.announce-offline-streamers", true);

        this.randomIntervalEnabled = config.getBoolean("chat.random.enabled", true);
        this.randomMinimumMinutes = Math.max(1, config.getInt("chat.random.minimum-minutes", 5));
        this.randomMaximumMinutes = Math.max(this.randomMinimumMinutes, config.getInt("chat.random.maximum-minutes", 60));

        java.util.List<String> msgLines = config.getStringList("chat.message");
        if (msgLines.isEmpty()) {
            this.announcementMessageLines = java.util.List.of(
                    "<red>●</red> <white><streamer> is <red>LIVE!</red>",
                    "<gray><title></gray>",
                    "<click:open_url:'<url>'><hover:show_text:'<gray>Click to watch on YouTube</gray>'><aqua><bold>[WATCH STREAM]</bold></aqua></hover></click>"
            );
        } else {
            this.announcementMessageLines = java.util.List.copyOf(msgLines);
        }

        this.adminChatEnabled = config.getBoolean("chat.admin.chat.enabled", true);
        this.streamerCanDisable = config.getBoolean("chat.admin.streamer-can-disable", true);

        // Web Panel configuration
        this.webEnabled = config.getBoolean("web.enabled", true);
        this.webHost = config.getString("web.host", "0.0.0.0");
        this.webPort = config.getInt("web.port", 8765);
        this.webLinkExpirationHours = Math.max(1, config.getInt("web.link-expiration-hours", 5));
        this.webSslEnabled = config.getBoolean("web.ssl.enabled", false);
        this.webSslKeystorePath = config.getString("web.ssl.keystore-path", "");
        this.webSslKeystorePassword = config.getString("web.ssl.keystore-password", "");

        // Premium and Payment
        this.premiumEnabled = config.getBoolean("premium.enabled", true);
        this.paymentWebhookSecret = resolveEnv(config.getString("payment.webhook-secret", ""));
    }

    private static String resolveEnv(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = ENV_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String value = System.getenv(envVar);
            if (value == null) {
                value = System.getProperty(envVar, "");
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public int getCallbackPort() {
        return callbackPort;
    }

    public String getCallbackHost() {
        return callbackHost;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public String getStateSecret() {
        return stateSecret;
    }

    public int getPollingIntervalSeconds() {
        return pollingIntervalSecondsMutable > 0 ? pollingIntervalSecondsMutable : pollingIntervalSeconds;
    }

    public boolean isNametagEnabled() {
        return nametagEnabled;
    }

    public String getNametagProvider() {
        return nametagProvider;
    }

    public String getLiveText() {
        return liveTextMutable != null ? liveTextMutable : liveText;
    }

    public String getLiveFormat() {
        return liveFormatMutable != null ? liveFormatMutable : liveFormat;
    }

    public String getSeparator() {
        return separator;
    }

    public boolean isRequireGamingCategory() {
        return requireGamingCategory;
    }

    public boolean isRequireMinecraft() {
        return requireMinecraft;
    }

    public boolean isAllowTitleFallback() {
        return allowTitleFallback;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public boolean isPlaceholderApiEnabled() {
        return placeholderApiEnabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isOAuthConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public int getGlobalMinimumGapSeconds() {
        return globalMinimumGapSecondsMutable > 0 ? globalMinimumGapSecondsMutable : globalMinimumGapSeconds;
    }

    public int getDefaultIntervalMinutes() {
        return defaultIntervalMinutesMutable > 0 ? defaultIntervalMinutesMutable : defaultIntervalMinutes;
    }

    public int getMinimumIntervalMinutes() {
        return minimumIntervalMinutes;
    }

    public int getMaximumIntervalMinutes() {
        return maximumIntervalMinutes;
    }

    public boolean isAnnounceOfflineStreamers() {
        return announceOfflineStreamers;
    }

    public boolean isRandomIntervalEnabled() {
        return randomIntervalEnabled;
    }

    public int getRandomMinimumMinutes() {
        return randomMinimumMinutes;
    }

    public int getRandomMaximumMinutes() {
        return randomMaximumMinutes;
    }

    public java.util.List<String> getAnnouncementMessageLines() {
        return announcementMessageLines;
    }

    public boolean isAdminChatEnabled() {
        return adminChatEnabled;
    }

    public void setAdminChatEnabled(boolean adminChatEnabled) {
        this.adminChatEnabled = adminChatEnabled;
    }

    public boolean isStreamerCanDisable() {
        return streamerCanDisable;
    }

    public boolean isWebEnabled() {
        return webEnabled;
    }

    public String getWebHost() {
        return webHost;
    }

    public int getWebPort() {
        return webPort;
    }

    public int getWebLinkExpirationHours() {
        return webLinkExpirationHours;
    }

    public boolean isWebSslEnabled() {
        return webSslEnabled;
    }

    public String getWebSslKeystorePath() {
        return webSslKeystorePath;
    }

    public String getWebSslKeystorePassword() {
        return webSslKeystorePassword;
    }

    public boolean isPremiumEnabled() {
        return premiumEnabled;
    }

    public String getPaymentWebhookSecret() {
        return paymentWebhookSecret;
    }

    public void setLiveText(String liveText) {
        if (liveText != null && !liveText.isBlank()) {
            this.liveTextMutable = liveText;
        }
    }

    public void setLiveFormat(String liveFormat) {
        if (liveFormat != null && !liveFormat.isBlank()) {
            this.liveFormatMutable = liveFormat;
        }
    }

    public void setPollingIntervalSeconds(int seconds) {
        if (seconds >= 5) {
            this.pollingIntervalSecondsMutable = seconds;
        }
    }

    public void setGlobalMinimumGapSeconds(int seconds) {
        if (seconds >= 5) {
            this.globalMinimumGapSecondsMutable = seconds;
        }
    }

    public void setDefaultIntervalMinutes(int minutes) {
        if (minutes >= 1) {
            this.defaultIntervalMinutesMutable = minutes;
        }
    }
}


