package com.example.livestatus.storage;

import com.example.livestatus.chat.IntervalMode;
import com.example.livestatus.chat.StreamerChatSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe YAML persistence store for streamer chat announcement settings.
 * Saves to plugins/LiveStatus/streamer-settings.yml.
 * Permanently keys all records by Minecraft player UUID.
 */
public class StreamerConfigStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, StreamerChatSettings> settingsMap = new ConcurrentHashMap<>();

    public StreamerConfigStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "streamer-settings.yml");
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        settingsMap.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String key : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "players." + key;
                String playerName = config.getString(path + ".player-name", "Unknown");
                boolean enabled = config.getBoolean(path + ".chat.enabled", true);
                String modeStr = config.getString(path + ".chat.interval-mode", "DEFAULT");
                IntervalMode mode = IntervalMode.fromString(modeStr);
                int intervalMinutes = config.getInt(path + ".chat.interval-minutes", 15);
                String customTitle = config.getString(path + ".chat.custom-title", null);
                boolean adminDisabled = config.getBoolean(path + ".chat.admin-disabled", false);

                StreamerChatSettings settings = new StreamerChatSettings(
                        uuid,
                        playerName,
                        enabled,
                        mode,
                        intervalMinutes,
                        customTitle,
                        adminDisabled
                );
                settingsMap.put(uuid, settings);
            } catch (IllegalArgumentException e) {
                logger.warning("Ignoring invalid UUID in streamer-settings.yml: " + key);
            }
        }
        logger.info("Loaded " + settingsMap.size() + " streamer chat settings from streamer-settings.yml.");
    }

    public synchronized void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, StreamerChatSettings> entry : settingsMap.entrySet()) {
            String key = entry.getKey().toString();
            String path = "players." + key;
            StreamerChatSettings s = entry.getValue();

            config.set(path + ".player-name", s.getPlayerName());
            config.set(path + ".chat.enabled", s.isEnabled());
            config.set(path + ".chat.interval-mode", s.getIntervalMode().name());
            config.set(path + ".chat.interval-minutes", s.getCustomIntervalMinutes());
            config.set(path + ".chat.custom-title", s.getCustomTitle());
            config.set(path + ".chat.admin-disabled", s.isAdminDisabled());
        }

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save streamer-settings.yml", e);
        }
    }

    public StreamerChatSettings getOrCreate(UUID uuid, String playerName) {
        return settingsMap.computeIfAbsent(uuid, id -> {
            StreamerChatSettings created = new StreamerChatSettings(id, playerName);
            save();
            return created;
        });
    }

    public Optional<StreamerChatSettings> get(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(settingsMap.get(uuid));
    }

    public synchronized void saveOrUpdate(StreamerChatSettings settings) {
        if (settings == null || settings.getUuid() == null) {
            return;
        }
        settingsMap.put(settings.getUuid(), settings);
        save();
    }

    public synchronized boolean delete(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        boolean removed = settingsMap.remove(uuid) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Collection<StreamerChatSettings> getAllSettings() {
        return Collections.unmodifiableCollection(settingsMap.values());
    }
}
