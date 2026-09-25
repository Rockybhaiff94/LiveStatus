package com.example.livestatus;

import com.example.livestatus.config.PluginConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigurationTest {

    @Test
    @DisplayName("14. Default configuration adheres to 30-second interval and safety defaults")
    void testDefaultConfiguration() {
        YamlConfiguration yaml = new YamlConfiguration();
        PluginConfig config = new PluginConfig(yaml);

        assertEquals(30, config.getPollingIntervalSeconds());
        assertTrue(config.isNametagEnabled());
        assertEquals("team", config.getNametagProvider());
        assertEquals("LIVE", config.getLiveText());
        assertEquals("<red><bold>LIVE</bold></red>", config.getLiveFormat());
        assertEquals(" ", config.getSeparator());
        assertTrue(config.isRequireGamingCategory());
        assertTrue(config.isRequireMinecraft());
        assertFalse(config.isAllowTitleFallback());
        assertTrue(config.isFailClosed());
        assertTrue(config.isPlaceholderApiEnabled());
        assertFalse(config.isDebugEnabled());
    }

    @Test
    @DisplayName("15. Custom configuration parsing and minimum interval enforcement")
    void testCustomConfiguration() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("youtube.client-id", "test-client-id");
        yaml.set("youtube.client-secret", "test-secret");
        yaml.set("youtube.redirect-uri", "http://localhost:9090/oauth/callback");
        yaml.set("youtube.callback-port", 9090);
        yaml.set("polling.interval-seconds", 45);
        yaml.set("nametag.live-text", "STREAMING");
        yaml.set("security.fail-closed", true);

        PluginConfig config = new PluginConfig(yaml);

        assertEquals("test-client-id", config.getClientId());
        assertEquals("test-secret", config.getClientSecret());
        assertEquals("http://localhost:9090/oauth/callback", config.getRedirectUri());
        assertEquals(9090, config.getCallbackPort());
        assertEquals(45, config.getPollingIntervalSeconds());
        assertEquals("STREAMING", config.getLiveText());
        assertTrue(config.isOAuthConfigured());
    }

    @Test
    @DisplayName("16. Polling interval enforces safe minimum of 5 seconds")
    void testMinimumPollingInterval() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("polling.interval-seconds", 1); // Attempt unsafe 1 second

        PluginConfig config = new PluginConfig(yaml);
        assertEquals(5, config.getPollingIntervalSeconds()); // Clamped to 5 seconds
    }
}

