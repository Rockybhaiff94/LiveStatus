package com.example.livestatus.youtube;

import java.util.List;

/**
 * Strategy interface for determining whether an active broadcast qualifies as an eligible Minecraft stream.
 */
public interface StreamEligibilityDetector {

    enum Eligibility {
        ELIGIBLE_MINECRAFT_LIVE,
        NOT_LIVE,
        NOT_MINECRAFT,
        UNKNOWN
    }

    record BroadcastMetadata(
            String broadcastId,
            String title,
            String lifeCycleStatus,
            String privacyStatus
    ) {
        public boolean isActualLive() {
            return "live".equalsIgnoreCase(lifeCycleStatus) || "liveStarting".equalsIgnoreCase(lifeCycleStatus);
        }
    }

    record VideoMetadata(
            String videoId,
            String categoryId,
            List<String> topicCategories,
            String title,
            String description
    ) {}

    /**
     * Evaluates the broadcast and video metadata to determine Minecraft stream eligibility.
     */
    Eligibility detectEligibility(BroadcastMetadata broadcast, VideoMetadata video);
}

