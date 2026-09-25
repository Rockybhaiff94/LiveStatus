package com.example.livestatus.youtube;

/**
 * Represents the distinct states of a player's YouTube Live connection.
 */
public enum YouTubeStatus {

    /**
     * Player has not linked a YouTube account.
     */
    NOT_LINKED("Not Linked", false),

    /**
     * Player account is linked, but not currently broadcasting.
     */
    OFFLINE("Offline", false),

    /**
     * Player account is live, but the broadcast is not a Minecraft stream.
     */
    LIVE_NOT_MINECRAFT("Live (Non-Minecraft)", false),

    /**
     * Player is actively broadcasting a verified Minecraft stream.
     * This is the ONLY state that causes the LIVE indicator to be displayed.
     */
    LIVE_MINECRAFT("Live Minecraft", true),

    /**
     * Status is currently unknown (e.g. backend unreachable, pending initial fetch, or API error).
     * In fail-closed mode, this state will not display the LIVE indicator.
     */
    UNKNOWN("Unknown", false);

    private final String displayName;
    private final boolean liveIndicatorEligible;

    YouTubeStatus(String displayName, boolean liveIndicatorEligible) {
        this.displayName = displayName;
        this.liveIndicatorEligible = liveIndicatorEligible;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if and only if all conditions for displaying the LIVE indicator are satisfied.
     */
    public boolean isLiveIndicatorEligible() {
        return liveIndicatorEligible;
    }
}

