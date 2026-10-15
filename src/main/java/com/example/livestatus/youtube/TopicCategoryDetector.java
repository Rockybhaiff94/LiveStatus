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
    public static final String MINECRAFT_FREEBASE_MID = "/m/09v6kpg";

    private final boolean requireGamingCategory;
    private final boolean requireMinecraftTopic;
    private final boolean allowTitleFallback;

    public TopicCategoryDetector(boolean requireGamingCategory, boolean requireMinecraftTopic, boolean allowTitleFallback) {
        this.requireGamingCategory = requireGamingCategory;
        this.requireMinecraftTopic = requireMinecraftTopic;
        this.allowTitleFallback = allowTitleFallback;
    }

    public TopicCategoryDetector() {
        this(true, true, true);
    }

    @Override
    public Eligibility detectEligibility(BroadcastMetadata broadcast, VideoMetadata video) {
        if (broadcast == null || !broadcast.isActualLive()) {
            return Eligibility.NOT_LIVE;
        }

        // Condition 2: Title contains Minecraft as recognizable game name (case-insensitive)
        if (allowTitleFallback) {
            String title = (video != null && video.title() != null && !video.title().isBlank())
                    ? video.title()
                    : (broadcast != null ? broadcast.title() : null);
            if (title != null && title.toLowerCase().contains("minecraft")) {
                return Eligibility.ELIGIBLE_MINECRAFT_LIVE;
            }
        }

        if (video == null) {
            return Eligibility.UNKNOWN;
        }

        // 1. Verify Gaming Category if required
        if (requireGamingCategory && !GAMING_CATEGORY_ID.equals(video.categoryId())) {
            return Eligibility.NOT_MINECRAFT;
        }

        // 2. Condition 1: YouTube game/category metadata identifies Minecraft
        if (requireMinecraftTopic) {
            boolean hasMinecraftTopic = false;
            List<String> topics = video.topicCategories();
            if (topics != null) {
                for (String topic : topics) {
                    if (topic != null && (topic.equalsIgnoreCase(MINECRAFT_TOPIC_WIKI) || topic.toLowerCase().contains("minecraft"))) {
                        hasMinecraftTopic = true;
                        break;
                    }
                }
            }

            if (hasMinecraftTopic) {
                return Eligibility.ELIGIBLE_MINECRAFT_LIVE;
            }

            return Eligibility.NOT_MINECRAFT;
        }

        return Eligibility.ELIGIBLE_MINECRAFT_LIVE;
    }

    public boolean isRequireGamingCategory() {
        return requireGamingCategory;
    }

    public boolean isRequireMinecraftTopic() {
        return requireMinecraftTopic;
    }

    public boolean isAllowTitleFallback() {
        return allowTitleFallback;
    }
}

