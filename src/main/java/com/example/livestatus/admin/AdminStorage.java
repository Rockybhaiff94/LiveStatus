package com.example.livestatus.admin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistently stores explicitly granted MCast administrators in YAML format.
 * Survives server restarts and plugin reloads.
 */
public class AdminStorage {

    private final File file;
    private final Logger logger;
    private final Map<UUID, AdminRecord> adminsByUuid = new ConcurrentHashMap<>();
    private final Map<String, AdminRecord> adminsByName = new ConcurrentHashMap<>();

    public AdminStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "admins.yml");
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        adminsByUuid.clear();
        adminsByName.clear();
        if (!file.exists()) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = config.getConfigurationSection("admins");
            if (section == null) {
                return;
            }

            for (String key : section.getKeys(false)) {
                String name = section.getString(key + ".name", key);
                long grantedAt = section.getLong(key + ".grantedAt", System.currentTimeMillis());
                String grantedBy = section.getString(key + ".grantedBy", "CONSOLE");
                UUID uuid = null;

                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    String uuidStr = section.getString(key + ".uuid");
                    if (uuidStr != null) {
                        try {
                            uuid = UUID.fromString(uuidStr);
                        } catch (IllegalArgumentException ignored2) {
                        }
                    }
                }

                AdminRecord record = new AdminRecord(uuid, name, grantedAt, grantedBy);
                if (uuid != null) {
                    adminsByUuid.put(uuid, record);
                }
                if (name != null && !name.isBlank()) {
                    adminsByName.put(BedrockIdentityHelper.normalizeName(name), record);
                }
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Failed to load admins.yml", e);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection("admins");

        // Merge all records
        Map<String, AdminRecord> allRecords = new LinkedHashMap<>();
        for (AdminRecord r : adminsByUuid.values()) {
            allRecords.put(r.uuid().toString(), r);
        }
        for (AdminRecord r : adminsByName.values()) {
            String key = r.uuid() != null ? r.uuid().toString() : r.name();
            allRecords.putIfAbsent(key, r);
        }

        for (Map.Entry<String, AdminRecord> entry : allRecords.entrySet()) {
            String key = entry.getKey();
            AdminRecord record = entry.getValue();
            section.set(key + ".name", record.name());
            if (record.uuid() != null) {
                section.set(key + ".uuid", record.uuid().toString());
            }
            section.set(key + ".grantedAt", record.grantedAt());
            section.set(key + ".grantedBy", record.grantedBy());
        }

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Failed to save admins.yml", e);
            }
        }
    }

    /**
     * Checks if the player is explicitly granted administrator privileges in this storage.
     */
    public boolean isGranted(UUID uuid, String name) {
        if (uuid != null && adminsByUuid.containsKey(uuid)) {
            return true;
        }
        if (name != null) {
            String normalized = BedrockIdentityHelper.normalizeName(name);
            if (adminsByName.containsKey(normalized)) {
                return true;
            }
            // Check Bedrock / fuzzy matching against all granted records
            for (AdminRecord r : adminsByName.values()) {
                if (BedrockIdentityHelper.matches(r.name(), uuid, name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Grants administrator privileges and persists to disk.
     */
    public synchronized boolean grantAdmin(UUID uuid, String name, String grantedBy) {
        if (uuid == null && (name == null || name.isBlank())) {
            return false;
        }
        AdminRecord record = new AdminRecord(uuid, name, System.currentTimeMillis(), grantedBy != null ? grantedBy : "CONSOLE");
        if (uuid != null) {
            adminsByUuid.put(uuid, record);
        }
        if (name != null && !name.isBlank()) {
            adminsByName.put(BedrockIdentityHelper.normalizeName(name), record);
        }
        save();
        return true;
    }

    /**
     * Revokes administrator privileges and persists to disk.
     * Returns true if an entry was removed.
     */
    public synchronized boolean revokeAdmin(UUID uuid, String name) {
        boolean removed = false;
        if (uuid != null) {
            if (adminsByUuid.remove(uuid) != null) {
                removed = true;
            }
        }
        if (name != null && !name.isBlank()) {
            String normalized = BedrockIdentityHelper.normalizeName(name);
            if (adminsByName.remove(normalized) != null) {
                removed = true;
            }
            // Also check for prefix matching entries
            Iterator<Map.Entry<String, AdminRecord>> it = adminsByName.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, AdminRecord> entry = it.next();
                if (BedrockIdentityHelper.matches(entry.getValue().name(), uuid, name)) {
                    it.remove();
                    removed = true;
                }
            }
        }

        if (removed) {
            save();
        }
        return removed;
    }

    public Collection<AdminRecord> getGrantedAdmins() {
        Set<AdminRecord> all = new LinkedHashSet<>(adminsByUuid.values());
        all.addAll(adminsByName.values());
        return Collections.unmodifiableCollection(all);
    }

    public Set<String> getAdminNames() {
        Set<String> names = new LinkedHashSet<>();
        for (AdminRecord r : getGrantedAdmins()) {
            if (r.name() != null) {
                names.add(r.name());
            }
        }
        return names;
    }
}
