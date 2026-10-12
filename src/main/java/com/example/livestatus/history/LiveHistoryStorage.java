package com.example.livestatus.history;

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
 * Thread-safe JSON file-based storage for live stream event history.
 * Saves to plugins/LiveStatus/live-history.json with atomic file replacement.
 */
public class LiveHistoryStorage {

    private final File file;
    private final Logger logger;
    private final Gson gson = GsonFactory.createGson();
    private final Map<UUID, LiveEventRecord> events = new ConcurrentHashMap<>();

    public LiveHistoryStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "live-history.json");
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        events.clear();
        if (!file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<LiveEventRecord>>() {}.getType();
            List<LiveEventRecord> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                for (LiveEventRecord record : loaded) {
                    if (record.eventId() != null) {
                        events.put(record.eventId(), record);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read live-history.json", e);
        }
    }

    public synchronized void save() {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
        List<LiveEventRecord> exportList = new ArrayList<>(events.values());
        // Sort descending by startedAt
        exportList.sort((a, b) -> {
            if (a.startedAt() == null || b.startedAt() == null) return 0;
            return b.startedAt().compareTo(a.startedAt());
        });

        // Cap to latest 1000 events to avoid unbounded file growth
        if (exportList.size() > 1000) {
            exportList = exportList.subList(0, 1000);
        }

        try {
            try (FileWriter writer = new FileWriter(tempFile)) {
                gson.toJson(exportList, writer);
            }
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save live-history.json atomically", e);
        }
    }

    public void put(LiveEventRecord record) {
        if (record != null && record.eventId() != null) {
            events.put(record.eventId(), record);
        }
    }

    public Optional<LiveEventRecord> get(UUID eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    public List<LiveEventRecord> getAll() {
        List<LiveEventRecord> list = new ArrayList<>(events.values());
        list.sort((a, b) -> {
            if (a.startedAt() == null || b.startedAt() == null) return 0;
            return b.startedAt().compareTo(a.startedAt());
        });
        return Collections.unmodifiableList(list);
    }

    public List<LiveEventRecord> getForPlayer(UUID minecraftUuid) {
        List<LiveEventRecord> list = new ArrayList<>();
        for (LiveEventRecord record : events.values()) {
            if (record.minecraftUuid() != null && record.minecraftUuid().equals(minecraftUuid)) {
                list.add(record);
            }
        }
        list.sort((a, b) -> {
            if (a.startedAt() == null || b.startedAt() == null) return 0;
            return b.startedAt().compareTo(a.startedAt());
        });
        return Collections.unmodifiableList(list);
    }
}
