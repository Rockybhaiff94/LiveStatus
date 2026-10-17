package com.example.livestatus.command;

import com.example.livestatus.LiveStatusPlugin;
import com.example.livestatus.chat.LiveAnnouncementManager;
import com.example.livestatus.oauth.OAuthService;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.storage.StreamerConfigStore;
import com.example.livestatus.youtube.YouTubeService;

/**
 * Backwards-compatibility command executor delegating to the unified MCastCommand handler.
 */
public class YoutubeCommand extends MCastCommand {

    public YoutubeCommand(LiveStatusPlugin plugin, PlayerLinkService linkService, YouTubeService youTubeService) {
        super(
                plugin,
                linkService,
                youTubeService,
                plugin != null ? plugin.getOAuthService() : null,
                plugin != null ? plugin.getAnnouncementManager() : null,
                plugin != null ? plugin.getStreamerConfigStore() : null
        );
    }

    public YoutubeCommand(
            LiveStatusPlugin plugin,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            LiveAnnouncementManager announcementManager,
            StreamerConfigStore streamerConfigStore
    ) {
        super(
                plugin,
                linkService,
                youTubeService,
                plugin != null ? plugin.getOAuthService() : null,
                announcementManager,
                streamerConfigStore
        );
    }

    public YoutubeCommand(
            LiveStatusPlugin plugin,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            OAuthService oAuthService,
            LiveAnnouncementManager announcementManager,
            StreamerConfigStore streamerConfigStore
    ) {
        super(plugin, linkService, youTubeService, oAuthService, announcementManager, streamerConfigStore);
    }
}
