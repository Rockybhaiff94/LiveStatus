package com.example.livestatus.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks ephemeral runtime announcement state for a streamer.
 * Handles new stream detection, cooldown countdowns, and eligibility evaluation.
 */
public class AnnouncementState {

    private final UUID uuid;
    private Instant lastAnnouncedTime;
    private Instant nextEligibleTime;
    private String lastAnnouncedVideoId;
    private Duration currentCooldown;

    public AnnouncementState(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.nextEligibleTime = Instant.now();
        this.currentCooldown = Duration.ZERO;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Instant getLastAnnouncedTime() {
        return lastAnnouncedTime;
    }

    public Instant getNextEligibleTime() {
        return nextEligibleTime;
    }

    public void setNextEligibleTime(Instant nextEligibleTime) {
        this.nextEligibleTime = nextEligibleTime;
    }

    public String getLastAnnouncedVideoId() {
        return lastAnnouncedVideoId;
    }

    public Duration getCurrentCooldown() {
        return currentCooldown;
    }

    /**
     * Checks if this streamer is eligible for announcement at the given time.
     */
    public boolean isEligible(Instant now) {
        return nextEligibleTime == null || !nextEligibleTime.isAfter(now);
    }

    /**
     * Calculates how long this streamer has been waiting since they became eligible.
     * Returns 0 if not yet eligible.
     */
    public long getWaitTimeSeconds(Instant now) {
        if (nextEligibleTime == null || nextEligibleTime.isBefore(now)) {
            Instant base = nextEligibleTime != null ? nextEligibleTime : (lastAnnouncedTime != null ? lastAnnouncedTime : now);
            return Math.max(0L, Duration.between(base, now).toSeconds());
        }
        return 0L;
    }

    /**
     * Resets runtime cooldown when a new stream ID is detected.
     */
    public void resetForNewStream(String newVideoId) {
        this.lastAnnouncedVideoId = newVideoId;
        this.nextEligibleTime = Instant.now();
        this.lastAnnouncedTime = null;
        this.currentCooldown = Duration.ZERO;
    }

    /**
     * Records that an announcement was sent and sets the next eligible time based on cooldown.
     */
    public void recordAnnouncement(Instant now, Duration cooldownDuration, String videoId) {
        this.lastAnnouncedTime = now;
        this.currentCooldown = cooldownDuration != null ? cooldownDuration : Duration.ZERO;
        this.nextEligibleTime = now.plus(this.currentCooldown);
        this.lastAnnouncedVideoId = videoId;
    }
}
