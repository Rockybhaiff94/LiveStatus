package com.example.livestatus.youtube;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates metadata about an active YouTube broadcast.
 */
public record YouTubeBroadcast(
        String broadcastId,
        String title,
        String lifeCycleStatus,
        String privacyStatus,
        String categoryId,
        List<String> topicCategories,
        boolean isMinecraft
) {
    public YouTubeBroadcast {
        topicCategories = topicCategories != null ? List.copyOf(topicCategories) : Collections.emptyList();
    }
}

