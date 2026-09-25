package com.example.livestatus;

import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PlayerLinkServiceTest {

    @TempDir
    Path tempDir;

    private AllowedPlayersStorage storage;
    private PlayerLinkService linkService;

    @BeforeEach
    void setUp() {
        this.storage = new AllowedPlayersStorage(tempDir.toFile(), Logger.getLogger("TestLogger"));
        this.linkService = new PlayerLinkService(storage);
    }

    @Test
    @DisplayName("11. Unauthorized player is denied linking")
    void testUnauthorizedPlayerDenied() {
        UUID uuid = UUID.randomUUID();
        assertFalse(linkService.isAuthorizedToLink(uuid));

        LinkedPlayer player = linkService.getOrCreate(uuid, "PokeHowlet");
        assertFalse(player.isLinkingAllowed());
    }

    @Test
    @DisplayName("12. Admin allow-link grants linking authorization")
    void testAdminAllowLink() {
        UUID uuid = UUID.randomUUID();
        linkService.allowLink(uuid, "PokeHowlet");

        assertTrue(linkService.isAuthorizedToLink(uuid));
        LinkedPlayer player = linkService.getOrCreate(uuid, "PokeHowlet");
        assertTrue(player.isLinkingAllowed());
    }

    @Test
    @DisplayName("13. Admin revoke-link revokes authorization")
    void testAdminRevokeLink() {
        UUID uuid = UUID.randomUUID();
        linkService.allowLink(uuid, "PokeHowlet");
        assertTrue(linkService.isAuthorizedToLink(uuid));

        boolean revoked = linkService.revokeLink(uuid);
        assertTrue(revoked);
        assertFalse(linkService.isAuthorizedToLink(uuid));

        LinkedPlayer player = linkService.get(uuid).orElseThrow();
        assertFalse(player.isLinkingAllowed());
    }

    @Test
    @DisplayName("14. Player unlink removes account and resets status to NOT_LINKED")
    void testPlayerUnlink() {
        UUID uuid = UUID.randomUUID();
        linkService.allowLink(uuid, "PokeHowlet");
        linkService.updateAccount(uuid, true, new YouTubeAccount("UC12345", "PokeHowlet Channel", "@poke"));
        linkService.updateStatus(uuid, YouTubeStatus.LIVE_MINECRAFT, null);

        LinkedPlayer player = linkService.get(uuid).orElseThrow();
        assertTrue(player.isLinked());
        assertEquals(YouTubeStatus.LIVE_MINECRAFT, player.getStatus());

        // Perform unlink
        linkService.unlink(uuid);

        assertFalse(player.isLinked());
        assertNull(player.getAccount());
        assertEquals(YouTubeStatus.NOT_LINKED, player.getStatus());
        assertFalse(player.getStatus().isLiveIndicatorEligible());
    }

    @Test
    @DisplayName("15. Multiple linked players maintain completely independent status")
    void testMultiplePlayersIndependence() {
        UUID pokeUuid = UUID.randomUUID();
        UUID alexUuid = UUID.randomUUID();
        UUID steveUuid = UUID.randomUUID();

        linkService.allowLink(pokeUuid, "PokeHowlet");
        linkService.allowLink(alexUuid, "Alex");

        linkService.updateAccount(pokeUuid, true, new YouTubeAccount("UC1", "PokeChannel", null));
        linkService.updateAccount(alexUuid, true, new YouTubeAccount("UC2", "AlexChannel", null));

        // PokeHowlet is Live Minecraft, Alex is Live GTA (not Minecraft), Steve is unlinked
        linkService.updateStatus(pokeUuid, YouTubeStatus.LIVE_MINECRAFT, null);
        linkService.updateStatus(alexUuid, YouTubeStatus.LIVE_NOT_MINECRAFT, null);

        LinkedPlayer poke = linkService.get(pokeUuid).orElseThrow();
        LinkedPlayer alex = linkService.get(alexUuid).orElseThrow();
        LinkedPlayer steve = linkService.getOrCreate(steveUuid, "Steve");

        assertTrue(poke.getStatus().isLiveIndicatorEligible());
        assertFalse(alex.getStatus().isLiveIndicatorEligible());
        assertFalse(steve.getStatus().isLiveIndicatorEligible());

        // When PokeHowlet stops streaming, Alex remains unaffected
        linkService.updateStatus(pokeUuid, YouTubeStatus.OFFLINE, null);
        assertFalse(poke.getStatus().isLiveIndicatorEligible());
        assertFalse(alex.getStatus().isLiveIndicatorEligible());
    }
}

