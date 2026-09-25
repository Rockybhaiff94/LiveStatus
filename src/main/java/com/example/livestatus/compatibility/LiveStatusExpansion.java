package com.example.livestatus.compatibility;

import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.youtube.YouTubeStatus;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * PlaceholderAPI expansion providing YouTube live status tags.
 */
public class LiveStatusExpansion extends PlaceholderExpansion {

    private final PlayerLinkService linkService;
    private final String liveText;

    public LiveStatusExpansion(PlayerLinkService linkService, String liveText) {
        this.linkService = linkService;
        this.liveText = liveText != null ? liveText : "LIVE";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "livestatus";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LiveStatus";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        Optional<LinkedPlayer> opt = linkService.get(player.getUniqueId());
        YouTubeStatus status = opt.map(LinkedPlayer::getStatus).orElse(YouTubeStatus.UNKNOWN);
        boolean isLiveMinecraft = status.isLiveIndicatorEligible();

        return switch (params.toLowerCase()) {
            case "live" -> isLiveMinecraft ? "true" : "false";
            case "status" -> status.name();
            case "status_display" -> status.getDisplayName();
            case "game" -> isLiveMinecraft ? "Minecraft" : "";
            case "suffix" -> isLiveMinecraft ? " " + liveText : "";
            case "channel" -> opt.map(LinkedPlayer::getAccount).map(acc -> acc.channelTitle() != null ? acc.channelTitle() : acc.channelId()).orElse("");
            case "channel_id" -> opt.map(LinkedPlayer::getAccount).map(acc -> acc.channelId()).orElse("");
            default -> null;
        };
    }
}

