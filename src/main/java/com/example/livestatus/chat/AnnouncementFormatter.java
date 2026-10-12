package com.example.livestatus.chat;

import com.example.livestatus.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Renders Adventure chat announcement components using MiniMessage.
 * Strictly sanitizes all data inputs to prevent MiniMessage injection, command injection, and URL tampering.
 */
public class AnnouncementFormatter {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final PluginConfig config;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AnnouncementFormatter(PluginConfig config, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.logger = logger;
    }

    /**
     * Formats an announcement for the given live stream state into an ordered list of Adventure Components.
     */
    public List<Component> format(LiveStreamState state) {
        Objects.requireNonNull(state, "state");

        String safeVideoId = sanitizeVideoId(state.videoId());
        String safeUrl = "https://www.youtube.com/watch?v=" + safeVideoId;
        String streamerName = state.playerName() != null ? state.playerName() : "Streamer";
        String effectiveTitle = state.getEffectiveTitle();
        String customTitle = state.customTitle() != null ? state.customTitle() : "";

        // TagResolver inserting user-controlled text strictly as components so MiniMessage NEVER parses them as markup
        TagResolver resolver = TagResolver.resolver(
                Placeholder.component("streamer", Component.text(streamerName)),
                Placeholder.component("player", Component.text(streamerName)),
                Placeholder.component("title", Component.text(effectiveTitle)),
                Placeholder.component("custom_title", Component.text(customTitle)),
                Placeholder.unparsed("url", safeUrl),
                Placeholder.unparsed("video_id", safeVideoId),
                Placeholder.component("game", Component.text("Minecraft"))
        );

        List<String> templateLines = config.getAnnouncementMessageLines();
        List<Component> output = new ArrayList<>(templateLines.size());

        for (String line : templateLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                // Strictly pre-substitute verified URL and videoId tokens
                String preparedLine = line.replace("<url>", safeUrl).replace("<video_id>", safeVideoId);
                Component deserialized = miniMessage.deserialize(preparedLine, resolver);
                output.add(deserialized);
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("Failed to parse announcement template line: '" + line + "': " + e.getMessage());
                }
                // Fallback clean representation
                output.add(Component.text(streamerName + " is LIVE on YouTube: " + safeUrl));
            }
        }

        return output;
    }

    private String sanitizeVideoId(String videoId) {
        if (videoId != null && VIDEO_ID_PATTERN.matcher(videoId.trim()).matches()) {
            return videoId.trim();
        }
        return "live";
    }
}
