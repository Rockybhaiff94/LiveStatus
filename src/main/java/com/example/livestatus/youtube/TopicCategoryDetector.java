package com.example.livestatus.youtube;

import java.util.List;

/**
 * Standard implementation of StreamEligibilityDetector.
 * Uses official YouTube Data API categoryId ("20" = Gaming) and Freebase/Wikipedia
 * topicCategories ("https://en.wikipedia.org/wiki/Minecraft") to verify Minecraft broadcasts safely.
 */
public class TopicCategoryDetector implements StreamEligibilityDetector {

    public static final String GAMING_CATEGORY_ID = "20";
    public static final String MINECRAFT_TOPIC_WIKI = "https://en.wikipedia.org/wiki/Minecraft";

    private final boolean requireGamingCategory;
    private final boolean requireMinecraftTopic;
    private final boolean allowTitleFallback;

    public TopicCategoryDetector(boolean requireGamingCategory, boolean requireMinecraftTopic, boolean allowTitleFallback) {
        this.requireGamingCategory = requireGamingCategory;
        this.requireMinecraftTopic = requireMinecraftTopic;
        this.allowTitleFallback = allowTitleFallback;
    }

    public TopicCategoryDetector() {
        this(true, true, false);
    }

    @Override
    public Eligibility detectEligibility(BroadcastMetadata broadcast, VideoMetadata video) {
        if (broadcast == null || !broadcast.isActualLive()) {
            return Eligibility.NOT_LIVE;
        }

        if (video == null) {
            // Cannot verify game metadata safely; fail closed
            return Eligibility.UNKNOWN;
        }

        // 1. Verify Gaming Category
        if (requireGamingCategory) {
            if (!GAMING_CATEGORY_ID.equals(video.categoryId())) {
                return Eligibility.NOT_MINECRAFT;
            }
        }

        // 2. Verify Minecraft Topic via official YouTube Topic Details
        if (requireMinecraftTopic) {
            boolean hasMinecraftTopic = false;
            List<String> topics = video.topicCategories();
            if (topics != null) {
                for (String topic : topics) {
                    if (topic != null && (topic.equalsIgnoreCase(MINECRAFT_TOPIC_WIKI) || topic.toLowerCase().endsWith("/minecraft"))) {
                        hasMinecraftTopic = true;
                        break;
                    }
                }
            }

            if (hasMinecraftTopic) {
                return Eligibility.ELIGIBLE_MINECRAFT_LIVE;
            }

            // 3. Fallback rule: only if explicitly enabled in configuration
            if (allowTitleFallback) {
                String title = video.title();
                if (title != null && title.toLowerCase().contains("minecraft")) {
                    return Eligibility.ELIGIBLE_MINECRAFT_LIVE;
                }
            }

            return Eligibility.NOT_MINECRAFT;
        }

        return Eligibility.ELIGIBLE_MINECRAFT_LIVE;
    }
}

