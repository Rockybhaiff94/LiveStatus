package com.example.livestatus;

import com.example.livestatus.chat.IntervalMode;
import com.example.livestatus.chat.StreamerChatSettings;
import com.example.livestatus.command.YoutubeCommand;
import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.storage.StreamerConfigStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class YoutubeCommandTest {

    @TempDir
    File tempDir;

    private AllowedPlayersStorage allowedStorage;
    private StreamerConfigStore streamerConfigStore;
    private PlayerLinkService linkService;
    private YoutubeCommand commandHandler;

    private UUID playerUuid;
    private String playerName;
    private Set<String> grantedPermissions;
    private List<String> receivedMessages;

    @BeforeEach
    void setUp() {
        this.playerUuid = UUID.randomUUID();
        this.playerName = "TestStreamer";
        this.grantedPermissions = new HashSet<>();
        this.receivedMessages = new ArrayList<>();

        Logger logger = Logger.getLogger("TestLogger");
        this.allowedStorage = new AllowedPlayersStorage(tempDir, logger);
        this.streamerConfigStore = new StreamerConfigStore(tempDir, logger);
        this.linkService = new PlayerLinkService(allowedStorage);

        // We can create a test-friendly YoutubeCommand passing dependencies
        this.commandHandler = new YoutubeCommand(
                null,
                linkService,
                null,
                null,
                streamerConfigStore
        );
    }

    private Player createTestPlayer() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getUniqueId")) return playerUuid;
                    if (name.equals("getName")) return playerName;
                    if (name.equals("hasPermission")) {
                        String perm = (String) args[0];
                        return grantedPermissions.contains(perm);
                    }
                    if (name.equals("sendMessage") && args.length == 1 && args[0] instanceof Component c) {
                        receivedMessages.add(PlainTextComponentSerializer.plainText().serialize(c));
                        return null;
                    }
                    if (name.equals("isOnline")) return true;
                    if (name.equals("toString")) return "TestPlayer[" + playerName + "]";
                    return null;
                }
        );
    }

    private Command createTestCommand() {
        return new Command("youtube") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };
    }

    @Test
    @DisplayName("16. Streamer chat settings validation in store")
    void testStoreValidation() {
        StreamerChatSettings settings = streamerConfigStore.getOrCreate(playerUuid, playerName);
        assertTrue(settings.isEnabled());
        assertEquals(IntervalMode.DEFAULT, settings.getIntervalMode());

        settings.setIntervalMode(IntervalMode.CUSTOM);
        settings.setCustomIntervalMinutes(10);
        settings.setCustomTitle("SMP Domination");
        streamerConfigStore.saveOrUpdate(settings);

        StreamerChatSettings loaded = streamerConfigStore.get(playerUuid).orElseThrow();
        assertEquals(10, loaded.getCustomIntervalMinutes());
        assertEquals(IntervalMode.CUSTOM, loaded.getIntervalMode());
        assertEquals("SMP Domination", loaded.getCustomTitle());
    }

    @Test
    @DisplayName("17. Streamer chat settings interval modes and clamp logic")
    void testIntervalModeHandling() {
        StreamerChatSettings settings = streamerConfigStore.getOrCreate(playerUuid, playerName);

        settings.setIntervalMode(IntervalMode.RANDOM);
        assertEquals(IntervalMode.RANDOM, settings.getIntervalMode());

        settings.setIntervalMode(IntervalMode.DEFAULT);
        assertEquals(IntervalMode.DEFAULT, settings.getIntervalMode());

        settings.setIntervalMode(IntervalMode.CUSTOM);
        settings.setCustomIntervalMinutes(5);
        assertEquals(5, settings.getCustomIntervalMinutes());
    }

    @Test
    @DisplayName("18. Tab completion returns subcommands correctly")
    void testTabCompletions() {
        Player player = createTestPlayer();
        Command cmd = createTestCommand();
        grantedPermissions.add("youtubelivetag.chat");
        grantedPermissions.add("youtubelivetag.chat.admin");

        List<String> rootTabs = commandHandler.onTabComplete(player, cmd, "youtube", new String[]{""});
        assertNotNull(rootTabs);
        assertTrue(rootTabs.contains("chat"));

        List<String> chatTabs = commandHandler.onTabComplete(player, cmd, "youtube", new String[]{"chat", ""});
        assertNotNull(chatTabs);
        assertTrue(chatTabs.contains("status"));
        assertTrue(chatTabs.contains("on"));
        assertTrue(chatTabs.contains("off"));
        assertTrue(chatTabs.contains("title"));
        assertTrue(chatTabs.contains("interval"));
        assertTrue(chatTabs.contains("admin"));

        List<String> intervalTabs = commandHandler.onTabComplete(player, cmd, "youtube", new String[]{"chat", "interval", ""});
        assertNotNull(intervalTabs);
        assertTrue(intervalTabs.contains("default"));
        assertTrue(intervalTabs.contains("random"));
        assertTrue(intervalTabs.contains("15"));
    }
}
