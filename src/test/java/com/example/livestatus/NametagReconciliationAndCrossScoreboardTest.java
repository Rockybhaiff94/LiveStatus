package com.example.livestatus;

import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.nametag.PaperTeamNametagProvider;
import com.example.livestatus.nametag.StaticLiveIndicatorRenderer;
import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.scheduler.LiveStatusScheduler;
import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class NametagReconciliationAndCrossScoreboardTest {

    @TempDir
    File tempDir;

    private Logger logger;
    private AllowedPlayersStorage allowedStorage;
    private PlayerLinkService linkService;
    private PaperTeamNametagProvider nametagProvider;
    private StaticLiveIndicatorRenderer indicatorRenderer;
    private PlainTextComponentSerializer serializer;
    private PluginConfig config;

    @BeforeEach
    void setUp() {
        this.logger = Logger.getLogger("NametagTest");
        this.allowedStorage = new AllowedPlayersStorage(tempDir, logger);
        this.linkService = new PlayerLinkService(allowedStorage);
        this.nametagProvider = new PaperTeamNametagProvider(logger);
        this.indicatorRenderer = new StaticLiveIndicatorRenderer("<red><bold>LIVE</bold></red>", "LIVE");
        this.serializer = PlainTextComponentSerializer.plainText();

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("nametag.enabled", true);
        yaml.set("nametag.separator", " ");
        yaml.set("linking.require-admin-approval", true);
        yaml.set("detection.require-minecraft", true);
        this.config = new PluginConfig(yaml);
    }

    // =========================================================================
    // MOCK SCOREBOARD & PLAYER FACTORY
    // =========================================================================

    private static class MockTeamState {
        String name;
        Component prefix = Component.empty();
        Component suffix = Component.empty();
        NamedTextColor color;
        Map<Team.Option, Team.OptionStatus> options = new HashMap<>();
        Set<String> entries = new HashSet<>();
        boolean unregistered = false;

        MockTeamState(String name) {
            this.name = name;
        }
    }

    private static class MockScoreboardState {
        Map<String, MockTeamState> teams = new HashMap<>();

        MockTeamState getOrCreate(String name) {
            return teams.computeIfAbsent(name, MockTeamState::new);
        }
    }

    private Scoreboard createMockScoreboard(MockScoreboardState state) {
        return (Scoreboard) Proxy.newProxyInstance(
                Scoreboard.class.getClassLoader(),
                new Class[]{Scoreboard.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if (mName.equals("equals") && args != null && args.length == 1) {
                        return proxy == args[0];
                    }
                    if (mName.equals("hashCode")) {
                        return System.identityHashCode(state);
                    }
                    if (mName.equals("toString")) {
                        return "MockScoreboard@" + Integer.toHexString(System.identityHashCode(state));
                    }
                    if (mName.equals("getEntryTeam")) {
                        String entry = (String) args[0];
                        for (MockTeamState t : state.teams.values()) {
                            if (!t.unregistered && t.entries.contains(entry)) {
                                return createMockTeam(t, state);
                            }
                        }
                        return null;
                    }
                    if (mName.equals("getTeam")) {
                        String tName = (String) args[0];
                        MockTeamState t = state.teams.get(tName);
                        if (t != null && !t.unregistered) {
                            return createMockTeam(t, state);
                        }
                        return null;
                    }
                    if (mName.equals("registerNewTeam")) {
                        String tName = (String) args[0];
                        MockTeamState t = state.getOrCreate(tName);
                        t.unregistered = false;
                        return createMockTeam(t, state);
                    }
                    if (mName.equals("getTeams")) {
                        Set<Team> set = new HashSet<>();
                        for (MockTeamState t : state.teams.values()) {
                            if (!t.unregistered) set.add(createMockTeam(t, state));
                        }
                        return Collections.unmodifiableSet(set);
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) return false;
                    if (returnType.equals(int.class)) return 0;
                    if (returnType.equals(long.class)) return 0L;
                    if (returnType.equals(double.class)) return 0.0;
                    if (returnType.equals(float.class)) return 0.0f;
                    return null;
                }
        );
    }

    private Team createMockTeam(MockTeamState t, MockScoreboardState sbState) {
        return (Team) Proxy.newProxyInstance(
                Team.class.getClassLoader(),
                new Class[]{Team.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if (mName.equals("equals") && args != null && args.length == 1) {
                        return proxy == args[0];
                    }
                    if (mName.equals("hashCode")) {
                        return System.identityHashCode(t);
                    }
                    if (mName.equals("toString")) {
                        return "MockTeam[" + t.name + "]";
                    }
                    if (mName.equals("getName")) return t.name;
                    if (mName.equals("prefix") && (args == null || args.length == 0)) return t.prefix;
                    if (mName.equals("prefix") && args != null && args.length == 1) {
                        t.prefix = (Component) args[0];
                        return null;
                    }
                    if (mName.equals("suffix") && (args == null || args.length == 0)) return t.suffix;
                    if (mName.equals("suffix") && args != null && args.length == 1) {
                        t.suffix = (Component) args[0];
                        return null;
                    }
                    if (mName.equals("color") && (args == null || args.length == 0)) return t.color;
                    if (mName.equals("color") && args != null && args.length == 1) {
                        t.color = (NamedTextColor) args[0];
                        return null;
                    }
                    if (mName.equals("hasEntry")) {
                        return t.entries.contains(args[0]);
                    }
                    if (mName.equals("getEntries")) {
                        return Collections.unmodifiableSet(t.entries);
                    }
                    if (mName.equals("getSize")) {
                        return t.entries.size();
                    }
                    if (mName.equals("getOption") && args != null && args.length == 1) {
                        return t.options.getOrDefault((Team.Option) args[0], Team.OptionStatus.ALWAYS);
                    }
                    if (mName.equals("setOption") && args != null && args.length == 2) {
                        t.options.put((Team.Option) args[0], (Team.OptionStatus) args[1]);
                        return null;
                    }
                    if (mName.equals("addEntry")) {
                        String entry = (String) args[0];
                        // Remove from any other team on this scoreboard
                        for (MockTeamState other : sbState.teams.values()) {
                            other.entries.remove(entry);
                        }
                        t.entries.add(entry);
                        return null;
                    }
                    if (mName.equals("removeEntry")) {
                        t.entries.remove(args[0]);
                        return true;
                    }
                    if (mName.equals("unregister")) {
                        t.unregistered = true;
                        t.entries.clear();
                        sbState.teams.remove(t.name);
                        return null;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) return false;
                    if (returnType.equals(int.class)) return 0;
                    if (returnType.equals(long.class)) return 0L;
                    if (returnType.equals(double.class)) return 0.0;
                    if (returnType.equals(float.class)) return 0.0f;
                    return null;
                }
        );
    }

    private Player createMockPlayer(UUID uuid, String name, Scoreboard scoreboard) {
        final Scoreboard[] currentBoard = new Scoreboard[]{scoreboard};
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class[]{Player.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if (mName.equals("equals") && args != null && args.length == 1) {
                        return proxy == args[0];
                    }
                    if (mName.equals("hashCode")) {
                        return uuid.hashCode();
                    }
                    if (mName.equals("toString")) {
                        return "MockPlayer[" + name + "]";
                    }
                    if (mName.equals("getUniqueId")) return uuid;
                    if (mName.equals("getName")) return name;
                    if (mName.equals("isOnline")) return true;
                    if (mName.equals("getScoreboard")) return currentBoard[0];
                    if (mName.equals("setScoreboard")) {
                        currentBoard[0] = (Scoreboard) args[0];
                        return null;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) return false;
                    if (returnType.equals(int.class)) return 0;
                    if (returnType.equals(long.class)) return 0L;
                    if (returnType.equals(double.class)) return 0.0;
                    if (returnType.equals(float.class)) return 0.0f;
                    return null;
                }
        );
    }

    // =========================================================================
    // TESTS FOR THE 12 REQUIRED BEHAVIORS
    // =========================================================================

    @Test
    @DisplayName("1. Transition from UNKNOWN to LIVE_MINECRAFT applies indicator")
    void testUnknownToLiveMinecraftAppliesIndicator() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState sbState = new MockScoreboardState();
        Scoreboard sb = createMockScoreboard(sbState);
        Player streamer = createMockPlayer(streamerUuid, "Streamer1", sb);

        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");
        assertTrue(nametagProvider.hasLiveIndicator(streamer));

        Team team = sb.getEntryTeam(streamer.getName());
        assertNotNull(team);
        assertEquals(" LIVE", serializer.serialize(team.suffix()));
    }

    @Test
    @DisplayName("2. Player already marked LIVE_MINECRAFT receives indicator if missing (reconciliation)")
    void testReconciliationWhenIndicatorMissing() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState sbState = new MockScoreboardState();
        Scoreboard sb = createMockScoreboard(sbState);
        Player streamer = createMockPlayer(streamerUuid, "Streamer2", sb);

        // Simulate status already set in linkService before provider ran
        linkService.allowLink(streamerUuid, streamer.getName());
        LinkedPlayer lp = linkService.getOrCreate(streamerUuid, streamer.getName());
        lp.setLinked(true);
        lp.setAccount(new YouTubeAccount("UC123", "Channel", null));
        lp.setStatus(YouTubeStatus.LIVE_MINECRAFT);

        // Previous status is also LIVE_MINECRAFT so hasVisualIndicatorChanged() would be FALSE
        lp.setStatus(YouTubeStatus.LIVE_MINECRAFT);
        assertFalse(lp.hasVisualIndicatorChanged());

        LiveStatusScheduler scheduler = new LiveStatusScheduler(
                null, config, null, linkService, allowedStorage, null, nametagProvider, indicatorRenderer, logger
        );

        assertTrue(scheduler.isEligibleForLiveIndicator(streamer, lp));
        // Applying indicator works even though status didn't transition
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");
        assertTrue(nametagProvider.hasLiveIndicator(streamer));
    }

    @Test
    @DisplayName("3. Updating status from another command/join handler does not prevent scheduler from applying tag")
    void testExternalUpdateDoesNotBlockScheduler() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState sbState = new MockScoreboardState();
        Scoreboard sb = createMockScoreboard(sbState);
        Player streamer = createMockPlayer(streamerUuid, "Streamer3", sb);

        linkService.allowLink(streamerUuid, streamer.getName());
        LinkedPlayer lp = linkService.getOrCreate(streamerUuid, streamer.getName());
        lp.setLinked(true);

        // External component updates status directly
        linkService.updateStatus(streamerUuid, YouTubeStatus.LIVE_MINECRAFT, null);

        LiveStatusScheduler scheduler = new LiveStatusScheduler(
                null, config, null, linkService, allowedStorage, null, nametagProvider, indicatorRenderer, logger
        );

        // Desired state is eligible
        assertTrue(scheduler.isEligibleForLiveIndicator(streamer, lp));
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), config.getSeparator());

        Team team = sb.getEntryTeam(streamer.getName());
        assertNotNull(team);
        assertEquals(" LIVE", serializer.serialize(team.suffix()));
    }

    @Test
    @DisplayName("4. Repeated live polls do not append duplicate LIVE suffixes")
    void testRepeatedPollsDoNotDuplicateSuffix() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState sbState = new MockScoreboardState();
        Scoreboard sb = createMockScoreboard(sbState);
        Player streamer = createMockPlayer(streamerUuid, "Streamer4", sb);

        // Apply multiple times
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");

        Team team = sb.getEntryTeam(streamer.getName());
        assertNotNull(team);
        // Suffix must remain single " LIVE", not " LIVE LIVE LIVE"
        assertEquals(" LIVE", serializer.serialize(team.suffix()));
    }

    @Test
    @DisplayName("5. Offline transition removes the LIVE suffix and restores original suffix")
    void testOfflineRemovesSuffixAndRestoresOriginal() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState sbState = new MockScoreboardState();
        Scoreboard sb = createMockScoreboard(sbState);

        // Set up existing sole-member team with rank suffix
        Team existingTeam = sb.registerNewTeam("vip_team");
        existingTeam.suffix(Component.text(" [VIP]"));
        existingTeam.addEntry("Streamer5");

        Player streamer = createMockPlayer(streamerUuid, "Streamer5", sb);

        // Apply live indicator
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");
        assertEquals(" [VIP] LIVE", serializer.serialize(existingTeam.suffix()));

        // Stream goes offline: remove indicator
        nametagProvider.removeLiveIndicator(streamer);
        assertEquals(" [VIP]", serializer.serialize(existingTeam.suffix()));
        assertFalse(nametagProvider.hasLiveIndicator(streamer));
    }

    @Test
    @DisplayName("6. Non-Minecraft live stream does not display indicator")
    void testNonMinecraftStreamDoesNotDisplayIndicator() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState sbState = new MockScoreboardState();
        Scoreboard sb = createMockScoreboard(sbState);
        Player streamer = createMockPlayer(streamerUuid, "Streamer6", sb);

        linkService.allowLink(streamerUuid, streamer.getName());
        LinkedPlayer lp = linkService.getOrCreate(streamerUuid, streamer.getName());
        lp.setLinked(true);
        lp.setStatus(YouTubeStatus.LIVE_NOT_MINECRAFT);

        LiveStatusScheduler scheduler = new LiveStatusScheduler(
                null, config, null, linkService, allowedStorage, null, nametagProvider, indicatorRenderer, logger
        );

        assertFalse(scheduler.isEligibleForLiveIndicator(streamer, lp));
    }

    @Test
    @DisplayName("7. Unauthorized or unlinked player does not receive indicator")
    void testUnauthorizedOrUnlinkedDoesNotReceiveIndicator() {
        UUID unlinkedUuid = UUID.randomUUID();
        Player unlinkedPlayer = createMockPlayer(unlinkedUuid, "UnlinkedPlayer", createMockScoreboard(new MockScoreboardState()));
        LinkedPlayer lpUnlinked = linkService.getOrCreate(unlinkedUuid, "UnlinkedPlayer");
        lpUnlinked.setStatus(YouTubeStatus.LIVE_MINECRAFT); // stream is live but player not linked

        LiveStatusScheduler scheduler = new LiveStatusScheduler(
                null, config, null, linkService, allowedStorage, null, nametagProvider, indicatorRenderer, logger
        );

        assertFalse(scheduler.isEligibleForLiveIndicator(unlinkedPlayer, lpUnlinked));

        // Linked but unauthorized
        UUID unauthUuid = UUID.randomUUID();
        Player unauthPlayer = createMockPlayer(unauthUuid, "UnauthPlayer", createMockScoreboard(new MockScoreboardState()));
        LinkedPlayer lpUnauth = linkService.getOrCreate(unauthUuid, "UnauthPlayer");
        lpUnauth.setLinked(true);
        lpUnauth.setStatus(YouTubeStatus.LIVE_MINECRAFT);
        // allowedStorage does not contain unauthUuid
        assertFalse(scheduler.isEligibleForLiveIndicator(unauthPlayer, lpUnauth));
    }

    @Test
    @DisplayName("8. Replacing player scoreboard reconciles and applies to new scoreboard")
    void testScoreboardReplacementReconciliation() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState sbState1 = new MockScoreboardState();
        Scoreboard sb1 = createMockScoreboard(sbState1);
        Player streamer = createMockPlayer(streamerUuid, "Streamer8", sb1);

        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");
        assertNotNull(sb1.getEntryTeam(streamer.getName()));

        // Another plugin replaces the scoreboard with a new one
        MockScoreboardState sbState2 = new MockScoreboardState();
        Scoreboard sb2 = createMockScoreboard(sbState2);
        streamer.setScoreboard(sb2);

        // Old scoreboard had it, but new scoreboard is missing it
        assertFalse(nametagProvider.hasLiveIndicator(streamer));

        // Reconcile
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");
        assertTrue(nametagProvider.hasLiveIndicator(streamer));
        assertNotNull(sb2.getEntryTeam(streamer.getName()));
        assertEquals(" LIVE", serializer.serialize(sb2.getEntryTeam(streamer.getName()).suffix()));
    }

    @Test
    @DisplayName("9. Another online player with different scoreboard receives indicator on viewer join")
    void testViewerJoinSynchronization() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState streamerSbState = new MockScoreboardState();
        Scoreboard streamerSb = createMockScoreboard(streamerSbState);
        Player streamer = createMockPlayer(streamerUuid, "Streamer9", streamerSb);

        // Apply live indicator to streamer
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");

        // New viewer joins with separate scoreboard
        UUID viewerUuid = UUID.randomUUID();
        MockScoreboardState viewerSbState = new MockScoreboardState();
        Scoreboard viewerSb = createMockScoreboard(viewerSbState);
        Player viewer = createMockPlayer(viewerUuid, "Viewer9", viewerSb);

        // Suffix is not yet in viewer's scoreboard
        assertNull(viewerSb.getEntryTeam(streamer.getName()));

        // onViewerJoin is called when a viewer joins the server
        nametagProvider.onViewerJoin(viewer);
        assertNotNull(viewerSb.getEntryTeam(streamer.getName()));
        assertEquals(" LIVE", serializer.serialize(viewerSb.getEntryTeam(streamer.getName()).suffix()));
    }

    @Test
    @DisplayName("10. Existing team prefixes, colors, and options are preserved on shared team")
    void testSharedTeamPropertiesPreserved() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState sbState = new MockScoreboardState();
        Scoreboard sb = createMockScoreboard(sbState);

        // Shared team with multiple entries (e.g. "Admins")
        Team sharedTeam = sb.registerNewTeam("Admins");
        sharedTeam.prefix(Component.text("[ADMIN] "));
        sharedTeam.suffix(Component.text(" [PRO]"));
        sharedTeam.color(NamedTextColor.RED);
        sharedTeam.addEntry("Streamer10");
        sharedTeam.addEntry("OtherAdmin");

        Player streamer = createMockPlayer(streamerUuid, "Streamer10", sb);

        // Apply live indicator: because team is shared, a personal dynamic team is created copying prefix/color
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");

        Team dynamicTeam = sb.getEntryTeam("Streamer10");
        assertNotNull(dynamicTeam);
        assertNotEquals("Admins", dynamicTeam.getName()); // entry moved to dedicated dynamic team
        assertEquals("[ADMIN] ", serializer.serialize(dynamicTeam.prefix()));
        assertEquals(" [PRO] LIVE", serializer.serialize(dynamicTeam.suffix()));
        assertEquals(NamedTextColor.RED, dynamicTeam.color());

        // OtherAdmin remained in original team unaffected
        assertEquals("Admins", sb.getEntryTeam("OtherAdmin").getName());
        assertEquals(" [PRO]", serializer.serialize(sb.getEntryTeam("OtherAdmin").suffix()));

        // Removing live indicator restores Streamer10 back to original shared team
        nametagProvider.removeLiveIndicator(streamer);
        Team restoredTeam = sb.getEntryTeam("Streamer10");
        assertNotNull(restoredTeam);
        assertEquals("Admins", restoredTeam.getName());
        assertEquals(" [PRO]", serializer.serialize(restoredTeam.suffix()));
    }

    @Test
    @DisplayName("11. Indicator is restored correctly after player reconnects")
    void testReconnectRestoration() {
        UUID streamerUuid = UUID.randomUUID();
        MockScoreboardState sbState = new MockScoreboardState();
        Scoreboard sb = createMockScoreboard(sbState);
        Player streamer = createMockPlayer(streamerUuid, "Streamer11", sb);

        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");
        assertTrue(nametagProvider.hasLiveIndicator(streamer));

        // Player quits
        nametagProvider.cleanUp(streamer);
        assertFalse(nametagProvider.hasLiveIndicator(streamer));

        // Player reconnects while still live
        nametagProvider.applyLiveIndicator(streamer, indicatorRenderer.render(), " ");
        assertTrue(nametagProvider.hasLiveIndicator(streamer));
        assertEquals(" LIVE", serializer.serialize(sb.getEntryTeam(streamer.getName()).suffix()));
    }

    @Test
    @DisplayName("12. Nametag eligibility check evaluates admin approval, linking, and status")
    void testEligibilityEvaluation() {
        LiveStatusScheduler scheduler = new LiveStatusScheduler(
                null, config, null, linkService, allowedStorage, null, nametagProvider, indicatorRenderer, logger
        );

        UUID uuid = UUID.randomUUID();
        Player player = createMockPlayer(uuid, "TestPlayer12", createMockScoreboard(new MockScoreboardState()));
        LinkedPlayer lp = linkService.getOrCreate(uuid, "TestPlayer12");

        // 1. Unlinked, unauthorized
        assertFalse(scheduler.isEligibleForLiveIndicator(player, lp));

        // 2. Authorized but unlinked
        linkService.allowLink(uuid, "TestPlayer12");
        assertFalse(scheduler.isEligibleForLiveIndicator(player, lp));

        // 3. Linked but offline
        lp.setLinked(true);
        lp.setStatus(YouTubeStatus.OFFLINE);
        assertFalse(scheduler.isEligibleForLiveIndicator(player, lp));

        // 4. Linked and LIVE_MINECRAFT -> Eligible!
        lp.setStatus(YouTubeStatus.LIVE_MINECRAFT);
        assertTrue(scheduler.isEligibleForLiveIndicator(player, lp));
    }
}
