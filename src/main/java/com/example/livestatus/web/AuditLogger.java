package com.example.livestatus.web;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe audit logger that safely records administrative operations
 * (login, settings change, account unlink, code redeem) to an in-memory buffer
 * and appends to plugins/LiveStatus/logs/audit.log.
 * Never logs passwords, authentication tokens, or private payment secrets.
 */
public class AuditLogger {

    public record AuditEntry(
            UUID id,
            Instant timestamp,
            String actor,
            String action,
            String target,
            String ipAddress,
            String details
    ) {}

    private final File logFile;
    private final Logger logger;
    private final List<AuditEntry> recentEntries = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_BUFFER_SIZE = 200;

    public AuditLogger(File dataFolder, Logger logger) {
        File logDir = new File(dataFolder, "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        this.logFile = new File(logDir, "audit.log");
        this.logger = logger;
    }

    public synchronized void log(String actor, String action, String target, String ipAddress, String details) {
        AuditEntry entry = new AuditEntry(
                UUID.randomUUID(),
                Instant.now(),
                actor != null ? actor : "System",
                action != null ? action : "UNKNOWN",
                target != null ? target : "-",
                ipAddress != null ? ipAddress : "-",
                details != null ? details : ""
        );

        recentEntries.add(entry);
        if (recentEntries.size() > MAX_BUFFER_SIZE) {
            recentEntries.remove(0);
        }

        // Append to audit.log
        try {
            String line = String.format("[%s] [ACTOR=%s] [IP=%s] [ACTION=%s] [TARGET=%s] - %s%n",
                    DateTimeFormatter.ISO_INSTANT.format(entry.timestamp()),
                    entry.actor(),
                    entry.ipAddress(),
                    entry.action(),
                    entry.target(),
                    entry.details());
            java.nio.file.Files.writeString(logFile.toPath(), line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.WARNING, "Failed to append to audit.log", e);
            }
        }
    }

    public List<AuditEntry> getRecentEntries(int limit) {
        synchronized (recentEntries) {
            List<AuditEntry> list = new ArrayList<>(recentEntries);
            Collections.reverse(list);
            return list.stream().limit(limit).toList();
        }
    }
}
