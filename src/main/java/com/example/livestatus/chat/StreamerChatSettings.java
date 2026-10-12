package com.example.livestatus.chat;

import java.util.Objects;
import java.util.UUID;

/**
 * Model representing persistent chat announcement settings for an authorized streamer.
 * Keyed permanently by Minecraft UUID.
 */
public class StreamerChatSettings {

    private final UUID uuid;
    private String playerName;
    private boolean enabled;
    private IntervalMode intervalMode;
    private int customIntervalMinutes;
    private String customTitle;
    private boolean adminDisabled;

    public StreamerChatSettings(UUID uuid, String playerName) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.playerName = playerName != null ? playerName : "Unknown";
        this.enabled = true;
        this.intervalMode = IntervalMode.DEFAULT;
        this.customIntervalMinutes = 15;
        this.customTitle = null;
        this.adminDisabled = false;
    }

    public StreamerChatSettings(
            UUID uuid,
            String playerName,
            boolean enabled,
            IntervalMode intervalMode,
            int customIntervalMinutes,
            String customTitle,
            boolean adminDisabled
    ) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.playerName = playerName != null ? playerName : "Unknown";
        this.enabled = enabled;
        this.intervalMode = intervalMode != null ? intervalMode : IntervalMode.DEFAULT;
        this.customIntervalMinutes = Math.max(1, customIntervalMinutes);
        this.customTitle = customTitle != null && !customTitle.isBlank() ? customTitle.trim() : null;
        this.adminDisabled = adminDisabled;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            this.playerName = playerName;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public IntervalMode getIntervalMode() {
        return intervalMode;
    }

    public void setIntervalMode(IntervalMode intervalMode) {
        this.intervalMode = intervalMode != null ? intervalMode : IntervalMode.DEFAULT;
    }

    public int getCustomIntervalMinutes() {
        return customIntervalMinutes;
    }

    public void setCustomIntervalMinutes(int customIntervalMinutes) {
        this.customIntervalMinutes = customIntervalMinutes;
    }

    public String getCustomTitle() {
        return customTitle;
    }

    public void setCustomTitle(String customTitle) {
        this.customTitle = customTitle != null && !customTitle.isBlank() ? customTitle.trim() : null;
    }

    public boolean isAdminDisabled() {
        return adminDisabled;
    }

    public void setAdminDisabled(boolean adminDisabled) {
        this.adminDisabled = adminDisabled;
    }

    /**
     * Effective enabled calculation taking into account streamer setting and admin per-player toggle.
     */
    public boolean isEffectivelyEnabled() {
        return enabled && !adminDisabled;
    }
}
