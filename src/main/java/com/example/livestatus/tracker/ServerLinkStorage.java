package com.example.livestatus.tracker;

import com.example.livestatus.storage.GsonFactory;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe JSON file-based storage for server links and tracked join events.
 * Saves to plugins/LiveStatus/server-links.json.
 */
public class ServerLinkStorage {

    private final File file;
    private final Logger logger;
    private final Gson gson = GsonFactory.createGson();

    private final Map<String, ServerLinkRecord> links = new ConcurrentHashMap<>();
    private final List<LinkJoinRecord> joins = Collections.synchronizedList(new ArrayList<>());

    public static class StorageContainer {
        public Map<String, ServerLinkRecord> links = new HashMap<>();
        public List<LinkJoinRecord> joins = new ArrayList<>();
    }

    public ServerLinkStorage(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "server-links.json");
        this.logger = logger;
        load();
    }

    public synchronized void load() {
        links.clear();
        joins.clear();
        if (!file.exists()) {
            // Seed a default server join link
            links.put("default", new ServerLinkRecord("default", "Main Server Join", "play.example.com", 0, 0, Instant.now()));
            save();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            StorageContainer container = gson.fromJson(reader, StorageContainer.class);
            if (container != null) {
                if (container.links != null) {
                    links.putAll(container.links);
                }
                if (container.joins != null) {
                    joins.addAll(container.joins);
                }
            }
            if (links.isEmpty()) {
                links.put("default", new ServerLinkRecord("default", "Main Server Join", "play.example.com", 0, 0, Instant.now()));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read server-links.json", e);
        }
    }

    public synchronized void save() {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
        StorageContainer container = new StorageContainer();
        container.links = new HashMap<>(links);
        // Retain last 2000 joins to cap file size
        synchronized (joins) {
            if (joins.size() > 2000) {
                container.joins = new ArrayList<>(joins.subList(joins.size() - 2000, joins.size()));
            } else {
                container.joins = new ArrayList<>(joins);
            }
        }

        try {
            try (FileWriter writer = new FileWriter(tempFile)) {
                gson.toJson(container, writer);
            }
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save server-links.json atomically", e);
        }
    }

    public Map<String, ServerLinkRecord> getLinks() {
        return links;
    }

    public List<LinkJoinRecord> getJoins() {
        return joins;
    }
}
