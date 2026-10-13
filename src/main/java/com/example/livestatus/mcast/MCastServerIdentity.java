package com.example.livestatus.mcast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the persistent, cryptographically secure installation identity of this Minecraft server.
 * Stored on disk in plugins/LiveStatus/server-identity.json.
 */
public class MCastServerIdentity {

    private static final String IDENTITY_FILE_NAME = "server-identity.json";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Logger logger;

    private String serverId;
    private String serverSecret;
    private long registeredAt;

    public MCastServerIdentity(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, IDENTITY_FILE_NAME);
        this.logger = Objects.requireNonNull(logger, "logger");
        loadOrCreate();
    }

    /**
     * Loads the existing identity from disk, or generates a new cryptographically secure identity.
     */
    public synchronized void loadOrCreate() {
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null && json.has("server_id") && json.has("server_secret")) {
                    this.serverId = json.get("server_id").getAsString();
                    this.serverSecret = json.get("server_secret").getAsString();
                    this.registeredAt = json.has("registered_at") ? json.get("registered_at").getAsLong() : System.currentTimeMillis();
                    return;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to read existing server-identity.json. Generating fresh identity.", e);
            }
        }

        // Generate fresh cryptographically secure identity
        this.serverId = "srv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        this.serverSecret = generateSecureSecret(48); // 48 bytes = 96 hex characters
        this.registeredAt = System.currentTimeMillis();
        save();
        logger.info("Generated new MCast server identity: " + serverId);
    }

    /**
     * Persists the identity atomically to disk.
     */
    public synchronized void save() {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        JsonObject json = new JsonObject();
        json.addProperty("server_id", this.serverId);
        json.addProperty("server_secret", this.serverSecret);
        json.addProperty("registered_at", this.registeredAt);

        File tempFile = new File(file.getParentFile(), IDENTITY_FILE_NAME + ".tmp");
        try {
            try (FileWriter writer = new FileWriter(tempFile)) {
                GSON.toJson(json, writer);
            }
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save server-identity.json", e);
        }
    }

    private String generateSecureSecret(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String getServerId() {
        return serverId;
    }

    public String getServerSecret() {
        return serverSecret;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }
}
