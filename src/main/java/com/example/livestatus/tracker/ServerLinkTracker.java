package com.example.livestatus.tracker;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;

/**
 * Service managing server referral link generation, click handling, and join metrics
 * (total, today, this week, this month, recent joins).
 */
public class ServerLinkTracker {

    private final ServerLinkStorage storage;
    private final Logger logger;

    public ServerLinkTracker(ServerLinkStorage storage, Logger logger) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.logger = logger;
    }

    public synchronized void recordClick(String linkId) {
        ServerLinkRecord record = storage.getLinks().get(linkId);
        if (record != null) {
            storage.getLinks().put(linkId, record.withIncrementedClicks());
            storage.save();
        }
    }

    public synchronized LinkJoinRecord recordJoin(UUID playerUuid, String playerName, String linkId, String referralSource) {
        String effectiveId = (linkId != null && storage.getLinks().containsKey(linkId)) ? linkId : "default";
        LinkJoinRecord join = new LinkJoinRecord(
                UUID.randomUUID(),
                playerUuid,
                playerName != null ? playerName : "Unknown",
                Instant.now(),
                effectiveId,
                referralSource != null ? referralSource : "direct"
        );

        storage.getJoins().add(join);
        ServerLinkRecord link = storage.getLinks().get(effectiveId);
        if (link != null) {
            storage.getLinks().put(effectiveId, link.withIncrementedJoins());
        }
        storage.save();
        if (logger != null) {
            logger.fine("[ServerLink] Recorded join for " + playerName + " via link " + effectiveId);
        }
        return join;
    }

    public int getTotalJoins() {
        return storage.getJoins().size();
    }

    public int getJoinsToday() {
        return countJoinsSince(Instant.now().minus(24, ChronoUnit.HOURS));
    }

    public int getJoinsThisWeek() {
        return countJoinsSince(Instant.now().minus(7, ChronoUnit.DAYS));
    }

    public int getJoinsThisMonth() {
        return countJoinsSince(Instant.now().minus(30, ChronoUnit.DAYS));
    }

    private int countJoinsSince(Instant cutoff) {
        synchronized (storage.getJoins()) {
            return (int) storage.getJoins().stream()
                    .filter(r -> r.joinTime() != null && r.joinTime().isAfter(cutoff))
                    .count();
        }
    }

    public List<LinkJoinRecord> getRecentJoins(int limit) {
        synchronized (storage.getJoins()) {
            return storage.getJoins().stream()
                    .filter(r -> r.joinTime() != null)
                    .sorted(Comparator.comparing(LinkJoinRecord::joinTime).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    public List<ServerLinkRecord> getAllLinks() {
        return new ArrayList<>(storage.getLinks().values());
    }

    public Optional<ServerLinkRecord> getLink(String linkId) {
        return Optional.ofNullable(storage.getLinks().get(linkId));
    }

    public synchronized void createOrUpdateLink(String linkId, String label, String targetAddress) {
        ServerLinkRecord existing = storage.getLinks().get(linkId);
        long clicks = existing != null ? existing.clicksCount() : 0;
        long joins = existing != null ? existing.joinsCount() : 0;
        Instant created = existing != null ? existing.createdAt() : Instant.now();
        storage.getLinks().put(linkId, new ServerLinkRecord(linkId, label, targetAddress, clicks, joins, created));
        storage.save();
    }
}
