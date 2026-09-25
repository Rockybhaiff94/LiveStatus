package com.example.livestatus;

import com.example.livestatus.compatibility.LiveStatusExpansion;
import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeStatus;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class PlaceholderApiExpansionTest {

    @TempDir
    Path tempDir;

    private PlayerLinkService linkService;
    private LiveStatusExpansion expansion;
    private OfflinePlayer mockPlayer;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        AllowedPlayersStorage storage = new AllowedPlayersStorage(tempDir.toFile(), Logger.getLogger("Test"));
        this.linkService = new PlayerLinkService(storage);
        this.expansion = new LiveStatusExpansion(linkService, "LIVE");

        this.uuid = UUID.randomUUID();
        this.mockPlayer = Mockito.mock(OfflinePlayer.class);
        when(mockPlayer.getUniqueId()).thenReturn(uuid);
    }

    @Test
    @DisplayName("21. Placeholder expansion returns correct identifier and author")
    void testExpansionMetadata() {
        assertEquals("livestatus", expansion.getIdentifier());
        assertEquals("LiveStatus", expansion.getAuthor());
    }

    @Test
    @DisplayName("22. Placeholder expansion returns correct values when LIVE Minecraft")
    void testExpansionLiveMinecraft() {
        linkService.allowLink(uuid, "PokeHowlet");
        linkService.updateAccount(uuid, true, new YouTubeAccount("UC123", "PokeHowlet Official", "@poke"));
        linkService.updateStatus(uuid, YouTubeStatus.LIVE_MINECRAFT, null);

        assertEquals("true", expansion.onRequest(mockPlayer, "live"));
        assertEquals("LIVE_MINECRAFT", expansion.onRequest(mockPlayer, "status"));
        assertEquals("Live Minecraft", expansion.onRequest(mockPlayer, "status_display"));
        assertEquals("Minecraft", expansion.onRequest(mockPlayer, "game"));
        assertEquals(" LIVE", expansion.onRequest(mockPlayer, "suffix"));
        assertEquals("PokeHowlet Official", expansion.onRequest(mockPlayer, "channel"));
        assertEquals("UC123", expansion.onRequest(mockPlayer, "channel_id"));
    }

    @Test
    @DisplayName("23. Placeholder expansion returns correct values when Offline")
    void testExpansionOffline() {
        linkService.allowLink(uuid, "PokeHowlet");
        linkService.updateAccount(uuid, true, new YouTubeAccount("UC123", "PokeHowlet Official", "@poke"));
        linkService.updateStatus(uuid, YouTubeStatus.OFFLINE, null);

        assertEquals("false", expansion.onRequest(mockPlayer, "live"));
        assertEquals("OFFLINE", expansion.onRequest(mockPlayer, "status"));
        assertEquals("", expansion.onRequest(mockPlayer, "game"));
        assertEquals("", expansion.onRequest(mockPlayer, "suffix"));
    }

    @Test
    @DisplayName("24. Placeholder expansion returns correct values when Live GTA V")
    void testExpansionLiveOtherGame() {
        linkService.allowLink(uuid, "PokeHowlet");
        linkService.updateAccount(uuid, true, new YouTubeAccount("UC123", "PokeHowlet Official", "@poke"));
        linkService.updateStatus(uuid, YouTubeStatus.LIVE_NOT_MINECRAFT, null);

        assertEquals("false", expansion.onRequest(mockPlayer, "live"));
        assertEquals("LIVE_NOT_MINECRAFT", expansion.onRequest(mockPlayer, "status"));
        assertEquals("", expansion.onRequest(mockPlayer, "game"));
        assertEquals("", expansion.onRequest(mockPlayer, "suffix"));
    }
}

