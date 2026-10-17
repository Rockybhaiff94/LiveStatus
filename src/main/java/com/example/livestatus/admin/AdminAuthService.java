package com.example.livestatus.admin;

import com.example.livestatus.config.PluginConfig;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;

/**
 * Centralized authorization service for MCast administrators.
 * Supports:
 * - Default OP mode for small private SMPs (operators automatically qualify as MCast administrators).
 * - Restricted mode for large public/modded servers (operators alone do NOT have access; requires explicit authorization).
 * - Dual explicit authorization sources: configuration file (config.yml) and persistent storage (admins.yml).
 * - Java and Bedrock player identities (UUID and username matching with Bedrock/Floodgate prefix support).
 */
public class AdminAuthService {

    private volatile PluginConfig pluginConfig;
    private final AdminStorage adminStorage;
    private final Logger logger;

    public AdminAuthService(PluginConfig pluginConfig, AdminStorage adminStorage, Logger logger) {
        this.pluginConfig = pluginConfig;
        this.adminStorage = adminStorage;
        this.logger = logger;
    }

    public void updateConfig(PluginConfig newConfig) {
        this.pluginConfig = newConfig;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public AdminStorage getAdminStorage() {
        return adminStorage;
    }

    /**
     * Determines whether the given command sender is authorized as an MCast administrator.
     * The trusted server console always retains administrative access.
     */
    public boolean isAdministrator(CommandSender sender) {
        if (sender == null) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            // Console, RCON, and command blocks retain admin authority
            return true;
        }
        return isAdministrator(player);
    }

    /**
     * Evaluates administrator status for an online player according to the authorization matrix.
     */
    public boolean isAdministrator(Player player) {
        if (player == null) {
            return false;
        }

        // 1. Check default OP mode
        if (pluginConfig == null || pluginConfig.isOpDefaultAdmin()) {
            if (player.isOp()) {
                return true;
            }
            if (player.hasPermission("mcast.admin")
                    || player.hasPermission("livestatus.admin")
                    || player.hasPermission("youtubelivetag.admin")
                    || player.hasPermission("ytlive.admin")) {
                return true;
            }
        }

        // 2. Check explicit administrator authorizations (config.yml or admins.yml)
        return isExplicitAdmin(player.getUniqueId(), player.getName());
    }

    /**
     * Evaluates administrator status for an offline player.
     */
    public boolean isAdministrator(OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) {
            return false;
        }
        if ((pluginConfig == null || pluginConfig.isOpDefaultAdmin()) && offlinePlayer.isOp()) {
            return true;
        }
        return isExplicitAdmin(offlinePlayer.getUniqueId(), offlinePlayer.getName());
    }

    /**
     * Checks if a player identity is explicitly authorized in either config.yml or admins.yml.
     */
    public boolean isExplicitAdmin(UUID uuid, String name) {
        return isConfigAdmin(uuid, name) || isGrantedAdmin(uuid, name);
    }

    /**
     * Checks if a player identity is listed in config.yml under admin.admins.
     */
    public boolean isConfigAdmin(UUID uuid, String name) {
        if (pluginConfig == null) {
            return false;
        }
        List<String> configAdmins = pluginConfig.getConfigAdmins();
        if (configAdmins == null || configAdmins.isEmpty()) {
            return false;
        }
        for (String entry : configAdmins) {
            if (BedrockIdentityHelper.matches(entry, uuid, name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a player identity is persistently granted in admins.yml.
     */
    public boolean isGrantedAdmin(UUID uuid, String name) {
        if (adminStorage == null) {
            return false;
        }
        return adminStorage.isGranted(uuid, name);
    }

    /**
     * Grants administrator privileges persistently.
     */
    public boolean grantAdmin(UUID uuid, String name, String grantedBy) {
        if (adminStorage == null) {
            return false;
        }
        boolean granted = adminStorage.grantAdmin(uuid, name, grantedBy);
        if (granted && logger != null) {
            logger.info("MCast Administrator granted: " + name + " (UUID: " + uuid + ") by " + grantedBy);
        }
        return granted;
    }

    /**
     * Revokes administrator privileges from persistent storage.
     */
    public boolean revokeAdmin(UUID uuid, String name) {
        if (adminStorage == null) {
            return false;
        }
        boolean revoked = adminStorage.revokeAdmin(uuid, name);
        if (revoked && logger != null) {
            logger.info("MCast Administrator revoked: " + name + " (UUID: " + uuid + ")");
        }
        return revoked;
    }

    /**
     * Returns all known administrator names from both configuration and persistent grants.
     */
    public Set<String> getAllAdminNames() {
        Set<String> set = new LinkedHashSet<>();
        if (adminStorage != null) {
            set.addAll(adminStorage.getAdminNames());
        }
        if (pluginConfig != null) {
            set.addAll(pluginConfig.getConfigAdmins());
        }
        return set;
    }

    public Logger getLogger() {
        return logger;
    }
}
