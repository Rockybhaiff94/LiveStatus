package com.example.livestatus.player;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistently stores player UUIDs that an administrator has explicitly authorized to link YouTube.
 * Uses YAML storage keyed by Minecraft UUID.
 */
public class AllowedPlayersStorage {

    private final File file;
    private final Logger logger;
    private final Map<UUID, String> allowedPlayers = new ConcurrentHashMap<>();

    public AllowedPlayersStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "allowed-players.yml");
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        allowedPlayers.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String lastKnownName = config.getString(key + ".name", "Unknown");
                allowedPlayers.put(uuid, lastKnownName);
            } catch (IllegalArgumentException e) {
                logger.warning("Ignoring invalid UUID in allowed-players.yml: " + key);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : allowedPlayers.entrySet()) {
            String key = entry.getKey().toString();
            config.set(key + ".name", entry.getValue());
            config.set(key + ".allowedAt", System.currentTimeMillis());
        }
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save allowed-players.yml", e);
        }
    }

    public boolean isAllowed(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return allowedPlayers.containsKey(uuid);
    }

    public synchronized void allowPlayer(UUID uuid, String playerName) {
        if (uuid == null) {
            return;
        }
        allowedPlayers.put(uuid, playerName != null ? playerName : "Unknown");
        save();
    }

    public synchronized boolean revokePlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        boolean removed = allowedPlayers.remove(uuid) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Set<UUID> getAllowedUuids() {
        return Collections.unmodifiableSet(allowedPlayers.keySet());
    }

    public Map<UUID, String> getAllowedPlayersMap() {
        return Collections.unmodifiableMap(allowedPlayers);
    }
}

