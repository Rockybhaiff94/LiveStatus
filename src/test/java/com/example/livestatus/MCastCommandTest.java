package com.example.livestatus;

import com.example.livestatus.admin.AdminAuthService;
import com.example.livestatus.admin.AdminStorage;
import com.example.livestatus.command.MCastCommand;
import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.command.YouTubeUrlParser;
import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.storage.JsonAccountStorage;
import com.example.livestatus.storage.StreamerConfigStore;
import com.example.livestatus.youtube.YouTubeAccount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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

class MCastCommandTest {

    @TempDir
    File tempDir;

    private AllowedPlayersStorage allowedStorage;
    private AccountStorage accountStorage;
    private StreamerConfigStore streamerConfigStore;
    private PlayerLinkService linkService;
    private MCastCommand commandHandler;

    private UUID playerUuid;
    private String playerName;
    private Set<String> grantedPermissions;
    private List<String> receivedMessages;

    @BeforeEach
    void setUp() {
        this.playerUuid = UUID.randomUUID();
        this.playerName = "StreamerSteve";
        this.grantedPermissions = new HashSet<>();
        this.receivedMessages = new ArrayList<>();

        Logger logger = Logger.getLogger("MCastTestLogger");
        this.allowedStorage = new AllowedPlayersStorage(tempDir, logger);
        this.accountStorage = new JsonAccountStorage(tempDir, logger);
        this.streamerConfigStore = new StreamerConfigStore(tempDir, logger);
        this.linkService = new PlayerLinkService(allowedStorage, accountStorage);
        this.linkService.getOrCreate(playerUuid, playerName);

        this.commandHandler = new MCastCommand(
                null,
                linkService,
                null,
                null,
                null,
                streamerConfigStore,
                allowedStorage,
                accountStorage
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

    private CommandSender createTestConsole() {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class[]{CommandSender.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getName")) return "CONSOLE";
                    if (name.equals("hasPermission")) return true; // Console has all perms
                    if (name.equals("sendMessage") && args.length == 1 && args[0] instanceof Component c) {
                        receivedMessages.add(PlainTextComponentSerializer.plainText().serialize(c));
                        return null;
                    }
                    return null;
                }
        );
    }

    private Command createTestCommand(String name) {
        return new Command(name) {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };
    }

    @Test
    @DisplayName("YouTubeUrlParser validates channel URLs, handles, and rejects malformed inputs")
    void testUrlParser() {
        // Full channel URL with 24-char UC id
        YouTubeAccount acc1 = YouTubeUrlParser.parse("https://www.youtube.com/channel/UC1234567890123456789012");
        assertNotNull(acc1);
        assertEquals("UC1234567890123456789012", acc1.channelId());

        // Bare channel ID
        YouTubeAccount acc2 = YouTubeUrlParser.parse("UCabcdefghij123456789012");
        assertNotNull(acc2);
        assertEquals("UCabcdefghij123456789012", acc2.channelId());

        // Handle URL
        YouTubeAccount acc3 = YouTubeUrlParser.parse("https://youtube.com/@MinecraftPro");
        assertNotNull(acc3);
        assertEquals("@MinecraftPro", acc3.channelId());

        // Bare handle
        YouTubeAccount acc4 = YouTubeUrlParser.parse("@MineCrafter_99");
        assertNotNull(acc4);
        assertEquals("@MineCrafter_99", acc4.channelId());

        // Invalid inputs
        assertNull(YouTubeUrlParser.parse(null));
        assertNull(YouTubeUrlParser.parse(""));
        assertNull(YouTubeUrlParser.parse("https://notyoutube.com/evil"));
        assertNull(YouTubeUrlParser.parse("not-a-channel"));
    }

    @Test
    @DisplayName("Player without admin permission is denied administrative commands")
    void testPermissionSeparation() {
        Player player = createTestPlayer();
        Command cmd = createTestCommand("mcast");

        // Try admin commands without permissions
        commandHandler.onCommand(player, cmd, "mcast", new String[]{"reload"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("You do not have permission")));

        receivedMessages.clear();
        commandHandler.onCommand(player, cmd, "mcast", new String[]{"admin", "remove", "OtherPlayer"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("You do not have permission")));

        receivedMessages.clear();
        commandHandler.onCommand(player, cmd, "mcast", new String[]{"authorize", "user", "OtherPlayer"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("You do not have permission")));
    }

    @Test
    @DisplayName("Player with link permission can execute /mcast status")
    void testPlayerStatusExecution() {
        Player player = createTestPlayer();
        Command cmd = createTestCommand("mcast");
        grantedPermissions.add("mcast.link");

        commandHandler.onCommand(player, cmd, "mcast", new String[]{"status"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Your MCast Status")));
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Account Linked: NO")));
    }

    @Test
    @DisplayName("Console running /mcast status receives safe guidance message")
    void testConsoleStatusExecution() {
        CommandSender console = createTestConsole();
        Command cmd = createTestCommand("mcast");

        commandHandler.onCommand(console, cmd, "mcast", new String[]{"status"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Console cannot have a stream status")));
    }

    @Test
    @DisplayName("Admin can authorize and revoke users via /mcast authorize/remove user")
    void testAuthorizeAndRevokeUser() {
        CommandSender console = createTestConsole();
        Command cmd = createTestCommand("mcast");

        // Authorize user
        commandHandler.onCommand(console, cmd, "mcast", new String[]{"authorize", "user", playerName});
        assertTrue(allowedStorage.isAllowed(playerUuid) || allowedStorage.getAllowedPlayersMap().containsValue(playerName));
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Authorized " + playerName)));

        // User list
        receivedMessages.clear();
        commandHandler.onCommand(console, cmd, "mcast", new String[]{"user", "list"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Authorized MCast Users")));

        // Revoke user
        receivedMessages.clear();
        commandHandler.onCommand(console, cmd, "mcast", new String[]{"remove", "user", playerName});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Revoked YouTube linking authorization")));
    }

    @Test
    @DisplayName("Admin can assign and remove a channel via /mcast admin set/remove")
    void testAdminSetAndRemove() {
        CommandSender console = createTestConsole();
        Command cmd = createTestCommand("mcast");

        // Set channel
        commandHandler.onCommand(console, cmd, "mcast", new String[]{"admin", "set", playerName, "https://youtube.com/@ProStreamer"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Successfully assigned YouTube channel")));

        // Verify account storage has been updated
        AccountRecord rec = accountStorage.findByUuid(playerUuid).orElse(null);
        if (rec == null) {
            // Find by name
            rec = accountStorage.getAllAccounts().stream().filter(r -> playerName.equalsIgnoreCase(r.playerName())).findFirst().orElse(null);
        }
        assertNotNull(rec);
        assertEquals("@ProStreamer", rec.youtubeChannelId());

        // YouTuber list
        receivedMessages.clear();
        commandHandler.onCommand(console, cmd, "mcast", new String[]{"youtuber", "list"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Linked MCast YouTubers")));

        // Admin remove channel
        receivedMessages.clear();
        commandHandler.onCommand(console, cmd, "mcast", new String[]{"admin", "remove", playerName});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Successfully removed assigned YouTube channel link")));
    }

    @Test
    @DisplayName("Tab completion exposes only player commands to regular players and admin commands to admins")
    void testTabCompletionPermissions() {
        Player player = createTestPlayer();
        Command cmd = createTestCommand("mcast");

        // Ordinary player
        grantedPermissions.add("mcast.link");
        List<String> playerTabs = commandHandler.onTabComplete(player, cmd, "mcast", new String[]{""});
        assertNotNull(playerTabs);
        assertTrue(playerTabs.contains("link"));
        assertTrue(playerTabs.contains("unlink"));
        assertTrue(playerTabs.contains("status"));
        assertTrue(playerTabs.contains("info"));
        assertFalse(playerTabs.contains("reload"));
        assertFalse(playerTabs.contains("admin"));
        assertFalse(playerTabs.contains("authorize"));

        // Admin
        grantedPermissions.add("mcast.admin");
        List<String> adminTabs = commandHandler.onTabComplete(player, cmd, "mcast", new String[]{""});
        assertNotNull(adminTabs);
        assertTrue(adminTabs.contains("reload"));
        assertTrue(adminTabs.contains("admin"));
        assertTrue(adminTabs.contains("set"));
        assertTrue(adminTabs.contains("authorize"));
        assertTrue(adminTabs.contains("remove"));
        assertTrue(adminTabs.contains("user"));
        assertTrue(adminTabs.contains("youtuber"));

        // Subcommand completions
        List<String> adminSubTabs = commandHandler.onTabComplete(player, cmd, "mcast", new String[]{"admin", ""});
        assertTrue(adminSubTabs.contains("set"));
        assertTrue(adminSubTabs.contains("remove"));
        assertTrue(adminSubTabs.contains("grant"));
        assertTrue(adminSubTabs.contains("revoke"));

        List<String> authSubTabs = commandHandler.onTabComplete(player, cmd, "mcast", new String[]{"authorize", ""});
        assertTrue(authSubTabs.contains("user"));
    }

    @Test
    @DisplayName("Admin grant and revoke manage persistent administrators safely")
    void testAdminGrantAndRevoke() {
        CommandSender console = createTestConsole();
        Command cmd = createTestCommand("mcast");

        // Set up AdminAuthService
        AdminStorage adminStorage = new AdminStorage(tempDir, Logger.getLogger("AdminTest"));
        PluginConfig config = new PluginConfig(new org.bukkit.configuration.file.YamlConfiguration());
        AdminAuthService auth = new AdminAuthService(config, adminStorage, Logger.getLogger("AdminTest"));

        MCastCommand handlerWithAuth = new MCastCommand(
                null,
                linkService,
                null,
                null,
                null,
                streamerConfigStore,
                allowedStorage,
                accountStorage,
                auth
        );

        String target = "OfficerJenny";

        // Grant
        receivedMessages.clear();
        handlerWithAuth.onCommand(console, cmd, "mcast", new String[]{"admin", "grant", target});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Granted MCast administrator privileges to OfficerJenny")));
        assertTrue(auth.isExplicitAdmin(null, target));

        // Revoke
        receivedMessages.clear();
        handlerWithAuth.onCommand(console, cmd, "mcast", new String[]{"admin", "revoke", target});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Revoked MCast administrator privileges for OfficerJenny")));
        assertFalse(auth.isExplicitAdmin(null, target));
    }

    @Test
    @DisplayName("Ordinary players cannot execute admin grant or admin revoke")
    void testUnauthorizedPlayerBlockedFromAdminGrant() {
        Player normalPlayer = createTestPlayer();
        Command cmd = createTestCommand("mcast");

        receivedMessages.clear();
        commandHandler.onCommand(normalPlayer, cmd, "mcast", new String[]{"admin", "grant", "HackerUser"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("You do not have permission")));

        receivedMessages.clear();
        commandHandler.onCommand(normalPlayer, cmd, "mcast", new String[]{"admin", "revoke", "HackerUser"});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("You do not have permission")));
    }

    @Test
    @DisplayName("Legacy aliases /youtube allow-link and /youtube unlink route correctly")
    void testLegacyAliases() {
        CommandSender console = createTestConsole();
        Command cmd = createTestCommand("youtube");

        commandHandler.onCommand(console, cmd, "youtube", new String[]{"allow-link", playerName});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Authorized " + playerName)));

        receivedMessages.clear();
        commandHandler.onCommand(console, cmd, "youtube", new String[]{"revoke-link", playerName});
        assertTrue(receivedMessages.stream().anyMatch(m -> m.contains("Revoked YouTube linking authorization")));
    }
}
