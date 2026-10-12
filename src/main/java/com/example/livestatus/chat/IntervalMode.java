package com.example.livestatus.chat;

/**
 * Defines the interval modes for streamer YouTube live chat announcements.
 */
public enum IntervalMode {
    CUSTOM,
    DEFAULT,
    RANDOM;

    public static IntervalMode fromString(String text) {
        if (text == null) {
            return DEFAULT;
        }
        try {
            return valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}
