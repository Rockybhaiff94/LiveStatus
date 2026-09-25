package com.example.livestatus.youtube;

/**
 * Details of a linked YouTube channel.
 */
public record YouTubeAccount(
        String channelId,
        String channelTitle,
        String customUrl
) {
}

