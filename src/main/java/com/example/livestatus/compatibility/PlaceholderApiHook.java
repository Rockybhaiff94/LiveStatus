package com.example.livestatus.compatibility;

import com.example.livestatus.player.PlayerLinkService;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * Handles graceful optional integration with PlaceholderAPI.
 */
public class PlaceholderApiHook {

    private final PlayerLinkService linkService;
    private final String liveText;
    private final Logger logger;
    private LiveStatusExpansion expansion;
    private boolean hooked = false;

    public PlaceholderApiHook(PlayerLinkService linkService, String liveText, Logger logger) {
        this.linkService = linkService;
        this.liveText = liveText;
        this.logger = logger;
    }

    public boolean register() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            logger.info("PlaceholderAPI not detected. Running in standalone mode.");
            return false;
        }

        try {
            this.expansion = new LiveStatusExpansion(linkService, liveText);
            this.hooked = this.expansion.register();
            if (this.hooked) {
                logger.info("PlaceholderAPI expansion successfully registered (%livestatus_*%).");
            }
            return this.hooked;
        } catch (Throwable t) {
            logger.warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            this.hooked = false;
            return false;
        }
    }

    public void unregister() {
        if (hooked && expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {
            }
            hooked = false;
        }
    }

    public boolean isHooked() {
        return hooked;
    }
}

