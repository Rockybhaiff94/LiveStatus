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

        this.nametagEnabled = config.getBoolean("nametag.enabled", true);
        this.nametagProvider = config.getString("nametag.provider", "team").toLowerCase();
        this.liveText = config.getString("nametag.live-text", "LIVE");
        this.liveFormat = config.getString("nametag.live-format", "<red><bold>LIVE</bold></red>");
        this.separator = config.getString("nametag.separator", " ");

        this.requireGamingCategory = config.getBoolean("detection.require-gaming-category", true);
        this.requireMinecraft = config.getBoolean("detection.require-minecraft", true);
        this.allowTitleFallback = config.getBoolean("detection.allow-title-fallback", false);

        this.failClosed = config.getBoolean("security.fail-closed", true);

        this.placeholderApiEnabled = config.getBoolean("compatibility.placeholderapi", true);
        this.debugEnabled = config.getBoolean("debug.enabled", false);
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
        return pollingIntervalSeconds;
    }

    public boolean isNametagEnabled() {
        return nametagEnabled;
    }

    public String getNametagProvider() {
        return nametagProvider;
    }

    public String getLiveText() {
        return liveText;
    }

    public String getLiveFormat() {
        return liveFormat;
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
}

