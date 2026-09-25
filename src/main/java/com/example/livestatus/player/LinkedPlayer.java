package com.example.livestatus.player;

import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Model representing a player's YouTube link status and cached broadcast state.
 */
public class LinkedPlayer {

    private final UUID uuid;
    private String playerName;
    private boolean linkingAllowed;
    private boolean linked;
    private YouTubeAccount account;
    private YouTubeStatus status = YouTubeStatus.UNKNOWN;
    private YouTubeStatus previousStatus = YouTubeStatus.UNKNOWN;
    private Instant lastChecked;
    private String lastError;

    public LinkedPlayer(UUID uuid, String playerName) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.playerName = playerName;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public boolean isLinkingAllowed() {
        return linkingAllowed;
    }

    public void setLinkingAllowed(boolean linkingAllowed) {
        this.linkingAllowed = linkingAllowed;
    }

    public boolean isLinked() {
        return linked;
    }

    public void setLinked(boolean linked) {
        this.linked = linked;
        if (!linked) {
            this.account = null;
            this.status = YouTubeStatus.NOT_LINKED;
        }
    }

    public YouTubeAccount getAccount() {
        return account;
    }

    public void setAccount(YouTubeAccount account) {
        this.account = account;
    }

    public YouTubeStatus getStatus() {
        return status;
    }

    public void setStatus(YouTubeStatus newStatus) {
        this.previousStatus = this.status;
        this.status = newStatus != null ? newStatus : YouTubeStatus.UNKNOWN;
        this.lastChecked = Instant.now();
    }

    public YouTubeStatus getPreviousStatus() {
        return previousStatus;
    }

    public Instant getLastChecked() {
        return lastChecked;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * Determines whether the nametag requires an update based on the status change.
     */
    public boolean hasVisualIndicatorChanged() {
        boolean prevEligible = previousStatus != null && previousStatus.isLiveIndicatorEligible();
        boolean currEligible = status != null && status.isLiveIndicatorEligible();
        return prevEligible != currEligible;
    }
}

