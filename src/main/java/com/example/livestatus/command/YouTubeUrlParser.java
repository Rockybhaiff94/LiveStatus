package com.example.livestatus.command;

import com.example.livestatus.youtube.YouTubeAccount;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and parses YouTube channel URLs, handles, and channel IDs for administrative assignment.
 */
public final class YouTubeUrlParser {

    private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile(
            "^(?:https?:\\/\\/(?:www\\.)?youtube\\.com\\/channel\\/)?(UC[a-zA-Z0-9_-]{22})(?:[\\/?].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HANDLE_PATTERN = Pattern.compile(
            "^(?:https?:\\/\\/(?:www\\.)?youtube\\.com\\/)?@([a-zA-Z0-9_.-]{3,30})(?:[\\/?].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CUSTOM_URL_PATTERN = Pattern.compile(
            "^https?:\\/\\/(?:www\\.)?youtube\\.com\\/(?:c|user)\\/([a-zA-Z0-9_.-]+)(?:[\\/?].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private YouTubeUrlParser() {}

    /**
     * Parses a raw URL, handle, or channel ID string into a YouTubeAccount instance, or returns null if invalid.
     */
    public static YouTubeAccount parse(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        Matcher channelMatcher = CHANNEL_ID_PATTERN.matcher(trimmed);
        if (channelMatcher.matches()) {
            String channelId = channelMatcher.group(1);
            return new YouTubeAccount(channelId, channelId, null);
        }

        Matcher handleMatcher = HANDLE_PATTERN.matcher(trimmed);
        if (handleMatcher.matches()) {
            String handle = "@" + handleMatcher.group(1);
            return new YouTubeAccount(handle, handle, handle);
        }

        Matcher customMatcher = CUSTOM_URL_PATTERN.matcher(trimmed);
        if (customMatcher.matches()) {
            String name = customMatcher.group(1);
            return new YouTubeAccount(name, name, "/c/" + name);
        }

        return null;
    }
}
