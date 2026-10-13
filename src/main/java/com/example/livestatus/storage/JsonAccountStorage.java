package com.example.livestatus.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * Thread-safe JSON file-based implementation of AccountStorage.
 * Stores linked accounts in plugins/LiveStatus/linked-accounts.json with atomic writes.
 */
public class JsonAccountStorage implements AccountStorage {

    private final File file;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, AccountRecord> accounts = new ConcurrentHashMap<>();

    public JsonAccountStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "linked-accounts.json");
        this.logger = logger;
        load();
    }

    @Override
    public synchronized void load() {
        accounts.clear();
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, AccountRecord>>() {}.getType();
            Map<String, AccountRecord> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                for (Map.Entry<String, AccountRecord> entry : loaded.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        accounts.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        logger.warning("Ignoring invalid UUID in linked-accounts.json: " + entry.getKey());
                    }
                }
            }
            logger.info("Loaded " + accounts.size() + " linked YouTube accounts from storage.");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read linked-accounts.json", e);
        }
    }

    /**
     * Sanitizes any legacy plaintext Google tokens from local storage.
     * Invoked during plugin startup migration to ensure no Google secrets remain on disk.
     */
    public synchronized boolean sanitizeLegacyTokens() {
        boolean hadTokens = false;
        for (Map.Entry<UUID, AccountRecord> entry : accounts.entrySet()) {
            AccountRecord raw = entry.getValue();
            if ((raw.accessToken() != null && !raw.accessToken().isBlank()) ||
                (raw.refreshToken() != null && !raw.refreshToken().isBlank())) {
                hadTokens = true;
                AccountRecord sanitized = new AccountRecord(
                        raw.minecraftUuid(),
                        raw.playerName(),
                        raw.youtubeChannelId(),
                        raw.channelTitle(),
                        raw.customUrl(),
                        "",
                        "",
                        raw.tokenExpiresAt(),
                        raw.linkedAt(),
                        raw.lastVerifiedAt(),
                        raw.linkStatus()
                );
                accounts.put(entry.getKey(), sanitized);
            }
        }
        if (hadTokens) {
            save();
            logger.info("Sanitized local linked-accounts.json: removed legacy Google tokens from disk.");
        }
        return hadTokens;
    }

    @Override
    public synchronized void save() {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
        Map<String, AccountRecord> exportMap = new HashMap<>();
        for (Map.Entry<UUID, AccountRecord> entry : accounts.entrySet()) {
            exportMap.put(entry.getKey().toString(), entry.getValue());
        }

        try {
            try (FileWriter writer = new FileWriter(tempFile)) {
                gson.toJson(exportMap, writer);
            }
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save linked-accounts.json atomically", e);
            try {
                // Fallback direct write
                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(exportMap, writer);
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Direct write fallback also failed", ex);
            }
        }
    }

    @Override
    public void saveOrUpdate(AccountRecord record) {
        if (record == null || record.minecraftUuid() == null) {
            return;
        }
        accounts.put(record.minecraftUuid(), record);
        save();
    }

    @Override
    public Optional<AccountRecord> findByUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(accounts.get(uuid));
    }

    @Override
    public void updateTokens(UUID uuid, String accessToken, long expiresAt) {
        AccountRecord existing = accounts.get(uuid);
        if (existing != null) {
            AccountRecord updated = new AccountRecord(
                    existing.minecraftUuid(),
                    existing.playerName(),
                    existing.youtubeChannelId(),
                    existing.channelTitle(),
                    existing.customUrl(),
                    accessToken,
                    existing.refreshToken(),
                    expiresAt,
                    existing.linkedAt(),
                    System.currentTimeMillis(),
                    existing.linkStatus()
            );
            accounts.put(uuid, updated);
            save();
        }
    }

    @Override
    public boolean deleteByUuid(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        boolean removed = accounts.remove(uuid) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    @Override
    public Collection<AccountRecord> getAllAccounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }
}

