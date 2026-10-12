package com.example.livestatus.premium;

import com.example.livestatus.storage.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe JSON file-based persistence for premium subscriptions and redemption codes.
 * Premium state is completely independent of temporary web panel sessions.
 */
public class PremiumStorage {

    private final File dataFile;
    private final File codesFile;
    private final Logger logger;
    private final Gson gson = GsonFactory.createGson();

    private final Map<UUID, PremiumRecord> premiumUsers = new ConcurrentHashMap<>();
    private final Map<String, PremiumCodeRecord> codes = new ConcurrentHashMap<>();

    public PremiumStorage(File dataFolder, Logger logger) {
        this.dataFile = new File(dataFolder, "premium-data.json");
        this.codesFile = new File(dataFolder, "premium-codes.json");
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        premiumUsers.clear();
        codes.clear();

        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile)) {
                Type type = new TypeToken<Map<String, PremiumRecord>>() {}.getType();
                Map<String, PremiumRecord> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    for (Map.Entry<String, PremiumRecord> entry : loaded.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            premiumUsers.put(uuid, entry.getValue());
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to read premium-data.json", e);
            }
        }

        if (codesFile.exists()) {
            try (FileReader reader = new FileReader(codesFile)) {
                Type type = new TypeToken<Map<String, PremiumCodeRecord>>() {}.getType();
                Map<String, PremiumCodeRecord> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    codes.putAll(loaded);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to read premium-codes.json", e);
            }
        }
    }

    public synchronized void save() {
        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        // 1. Save premium users
        File tempUsers = new File(dataFile.getParentFile(), dataFile.getName() + ".tmp");
        Map<String, PremiumRecord> exportUsers = new HashMap<>();
        for (Map.Entry<UUID, PremiumRecord> entry : premiumUsers.entrySet()) {
            exportUsers.put(entry.getKey().toString(), entry.getValue());
        }

        try {
            try (FileWriter writer = new FileWriter(tempUsers)) {
                gson.toJson(exportUsers, writer);
            }
            Files.move(tempUsers.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save premium-data.json atomically", e);
        }

        // 2. Save premium codes
        File tempCodes = new File(codesFile.getParentFile(), codesFile.getName() + ".tmp");
        try {
            try (FileWriter writer = new FileWriter(tempCodes)) {
                gson.toJson(codes, writer);
            }
            Files.move(tempCodes.toPath(), codesFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save premium-codes.json atomically", e);
        }
    }

    public Map<UUID, PremiumRecord> getPremiumUsers() {
        return premiumUsers;
    }

    public Map<String, PremiumCodeRecord> getCodes() {
        return codes;
    }
}
