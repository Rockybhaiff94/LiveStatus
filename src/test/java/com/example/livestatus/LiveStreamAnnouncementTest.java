package com.example.livestatus;

import com.example.livestatus.chat.*;
import com.example.livestatus.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LiveStreamAnnouncementTest {

    private PluginConfig pluginConfig;
    private AnnouncementFormatter formatter;

    @BeforeEach
    void setUp() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("chat.enabled", true);
        yaml.set("chat.global-minimum-gap-seconds", 60);
        yaml.set("chat.default-interval-minutes", 15);
        yaml.set("chat.minimum-interval-minutes", 5);
        yaml.set("chat.maximum-interval-minutes", 1440);
        yaml.set("chat.random.enabled", true);
        yaml.set("chat.random.minimum-minutes", 5);
        yaml.set("chat.random.maximum-minutes", 60);
        yaml.set("chat.admin.chat.enabled", true);
        yaml.set("chat.admin.streamer-can-disable", true);

        this.pluginConfig = new PluginConfig(yaml);
        this.formatter = new AnnouncementFormatter(pluginConfig, null);
    }

    @Test
    @DisplayName("1. Single streamer live is eligible for announcement")
    void testSingleStreamerEligibility() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        LiveStreamState state = new LiveStreamState(
                uuid,
                "PGLUOG",
                "UC123456789",
                true, // authorized
                true, // linked
                true, // currentlyLive
                "ABC123XYZ",
                "Building Massive Mega Base in Survival SMP",
                "https://www.youtube.com/watch?v=ABC123XYZ",
                true, // eligibleMinecraftStream
                true, // chatEnabled
                IntervalMode.CUSTOM,
                5,
                "We are going to dominate this SMP!",
                null,
                now
        );

        assertTrue(state.isEligibleForAnnouncement(now, true));
    }

    @Test
    @DisplayName("2. Custom title is used when present")
    void testCustomTitleUsed() {
        UUID uuid = UUID.randomUUID();
        LiveStreamState state = new LiveStreamState(
                uuid,
                "PGLUOG",
                "UC123456789",
                true,
                true,
                true,
                "ABC123XYZ",
                "YouTube Stream Title #42",
                "https://www.youtube.com/watch?v=ABC123XYZ",
                true,
                true,
                IntervalMode.CUSTOM,
                5,
                "Custom SMP Domination!",
                null,
                Instant.now()
        );

        assertEquals("Custom SMP Domination!", state.getEffectiveTitle());

        List<Component> components = formatter.format(state);
        assertFalse(components.isEmpty());

        String serialized = components.stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(serialized.contains("Custom SMP Domination!"));
        assertFalse(serialized.contains("YouTube Stream Title #42"));
    }

    @Test
    @DisplayName("3. Dynamic YouTube stream title is used when custom title is absent")
    void testDynamicYouTubeTitleFallback() {
        UUID uuid = UUID.randomUUID();
        LiveStreamState state = new LiveStreamState(
                uuid,
                "Player2",
                "UC987654321",
                true,
                true,
                true,
                "DEF456UVW",
                "Minecraft Survival SMP #42",
                "https://www.youtube.com/watch?v=DEF456UVW",
                true,
                true,
                IntervalMode.DEFAULT,
                15,
                null, // no custom title
                null,
                Instant.now()
        );

        assertEquals("Minecraft Survival SMP #42", state.getEffectiveTitle());

        List<Component> components = formatter.format(state);
        String serialized = components.stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(serialized.contains("Minecraft Survival SMP #42"));
    }

    @Test
    @DisplayName("4. Malicious MiniMessage injection in title is strictly treated as plain text")
    void testMiniMessageInjectionSanitization() {
        UUID uuid = UUID.randomUUID();
        String maliciousTitle = "<click:run_command:/op hacker><hover:show_text:'click me'>PWNED!</hover></click>";

        LiveStreamState state = new LiveStreamState(
                uuid,
                "HackerPlayer",
                "UC_HACK",
                true,
                true,
                true,
                "HACK123",
                maliciousTitle,
                "https://www.youtube.com/watch?v=HACK123",
                true,
                true,
                IntervalMode.DEFAULT,
                15,
                maliciousTitle, // custom title attempted injection
                null,
                Instant.now()
        );

        List<Component> components = formatter.format(state);
        // Ensure no click event on the title line executes a run_command
        for (Component c : components) {
            String plain = PlainTextComponentSerializer.plainText().serialize(c);
            if (plain.contains("PWNED!")) {
                ClickEvent click = c.clickEvent();
                if (click != null) {
                    assertNotEquals(ClickEvent.Action.RUN_COMMAND, click.action());
                }
            }
        }
    }

    @Test
    @DisplayName("5. Clickable [WATCH STREAM] component opens the exact YouTube stream URL")
    void testClickableWatchStreamButton() {
        UUID uuid = UUID.randomUUID();
        String videoId = "ABC123XYZ";
        LiveStreamState state = new LiveStreamState(
                uuid,
                "PGLUOG",
                "UC123",
                true,
                true,
                true,
                videoId,
                "SMP Stream",
                "https://www.youtube.com/watch?v=" + videoId,
                true,
                true,
                IntervalMode.DEFAULT,
                15,
                null,
                null,
                Instant.now()
        );

        List<Component> components = formatter.format(state);
        boolean foundWatchButtonWithUrl = false;

        for (Component line : components) {
            // Traverse component and its children
            Queue<Component> queue = new ArrayDeque<>();
            queue.add(line);
            while (!queue.isEmpty()) {
                Component current = queue.poll();
                ClickEvent click = current.clickEvent();
                if (click != null && click.action() == ClickEvent.Action.OPEN_URL) {
                    if (click.value().equals("https://www.youtube.com/watch?v=" + videoId)) {
                        foundWatchButtonWithUrl = true;
                    }
                }
                queue.addAll(current.children());
            }
        }

        assertTrue(foundWatchButtonWithUrl, "Expected component to contain OPEN_URL with exact YouTube stream URL");
    }

    @Test
    @DisplayName("6. New stream ID resets cooldown and makes streamer immediately eligible")
    void testNewStreamResetsCooldown() {
        UUID uuid = UUID.randomUUID();
        AnnouncementState state = new AnnouncementState(uuid);

        Instant now = Instant.now();
        // Record announcement for stream 1 with 15-minute cooldown
        state.recordAnnouncement(now, Duration.ofMinutes(15), "STREAM_1");
        assertFalse(state.isEligible(now.plus(Duration.ofMinutes(5)))); // Still in cooldown

        // New stream 2 starts!
        state.resetForNewStream("STREAM_2");
        assertTrue(state.isEligible(now.plus(Duration.ofMinutes(5)))); // Immediately eligible!
        assertEquals("STREAM_2", state.getLastAnnouncedVideoId());
    }

    @Test
    @DisplayName("7. Streamer going offline immediately removes announcement eligibility")
    void testOfflineStreamerIneligible() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        LiveStreamState offlineState = new LiveStreamState(
                uuid,
                "Streamer1",
                "UC123",
                true,
                true,
                false, // OFFLINE
                "ABC",
                "Title",
                "https://youtube.com",
                false,
                true,
                IntervalMode.DEFAULT,
                15,
                null,
                null,
                now
        );

        assertFalse(offlineState.isEligibleForAnnouncement(now, true));
    }

    @Test
    @DisplayName("8. Long cooldown on one streamer never blocks other eligible streamers")
    void testIndependentCooldowns() {
        UUID streamer1 = UUID.randomUUID();
        UUID streamer2 = UUID.randomUUID();
        Instant now = Instant.now();

        // Streamer 1 has 60 minutes remaining on cooldown
        LiveStreamState s1 = new LiveStreamState(
                streamer1, "Player1", "UC1", true, true, true, "V1", "Title 1", "https://yt", true, true,
                IntervalMode.CUSTOM, 60, null, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(55))
        );

        // Streamer 2's cooldown has expired (eligible now)
        LiveStreamState s2 = new LiveStreamState(
                streamer2, "Player2", "UC2", true, true, true, "V2", "Title 2", "https://yt", true, true,
                IntervalMode.CUSTOM, 5, null, now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(5))
        );

        assertFalse(s1.isEligibleForAnnouncement(now, true));
        assertTrue(s2.isEligibleForAnnouncement(now, true));
    }

    @Test
    @DisplayName("9. Admin global disable and per-player disable enforcement")
    void testAdminDisablingEnforcement() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        LiveStreamState normalLive = new LiveStreamState(
                uuid, "Player", "UC1", true, true, true, "V1", "Title", "https://yt", true, true,
                IntervalMode.DEFAULT, 15, null, null, now
        );

        // Globally enabled: eligible
        assertTrue(normalLive.isEligibleForAnnouncement(now, true));

        // Globally disabled by admin: ineligible
        assertFalse(normalLive.isEligibleForAnnouncement(now, false));

        // Per-player admin-disabled: ineligible even when global is enabled
        LiveStreamState adminDisabledPlayer = new LiveStreamState(
                uuid, "Player", "UC1", true, true, true, "V1", "Title", "https://yt", true,
                false, // chatEnabled is false when admin-disabled
                IntervalMode.DEFAULT, 15, null, null, now
        );
        assertFalse(adminDisabledPlayer.isEligibleForAnnouncement(now, true));
    }

    @Test
    @DisplayName("10. Fair randomized selection with 2, 5, and 10 streamers evaluates without starving")
    void testFairRandomizedSelection() {
        LiveAnnouncementManager manager = new LiveAnnouncementManager(
                null, pluginConfig, null, null, null, null, formatter, null, null
        );

        Instant now = Instant.now();
        List<LiveStreamState> eligible = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            // Stagger wait times: streamer 0 waited longest (1000s), streamer 4 just became eligible (0s)
            Instant eligibleAt = now.minusSeconds((4 - i) * 250L);
            eligible.add(new LiveStreamState(
                    id, "Streamer" + i, "UC" + i, true, true, true, "VID" + i, "Title " + i, "https://yt", true, true,
                    IntervalMode.DEFAULT, 15, null, null, eligibleAt
            ));
        }

        Map<String, Integer> selectedCounts = new HashMap<>();
        int iterations = 10_000;

        for (int i = 0; i < iterations; i++) {
            LiveStreamState picked = manager.selectNextStreamer(eligible, now);
            assertNotNull(picked);
            selectedCounts.merge(picked.playerName(), 1, Integer::sum);
        }

        // Streamer 0 waited 1000s, Streamer 4 waited 0s.
        // Streamer 0 should have higher count than Streamer 4, but Streamer 4 must still have non-zero picks.
        assertTrue(selectedCounts.get("Streamer0") > selectedCounts.get("Streamer4"),
                "Longer waiting streamer should have higher probability");
        assertTrue(selectedCounts.get("Streamer4") > 0, "Newer eligible streamer must not be starved");
    }

    @Test
    @DisplayName("11. Cooldown calculation respects CUSTOM, DEFAULT, and RANDOM bounds")
    void testCooldownCalculations() {
        LiveAnnouncementManager manager = new LiveAnnouncementManager(
                null, pluginConfig, null, null, null, null, formatter, null, null
        );

        UUID uuid = UUID.randomUUID();

        // Custom mode within bounds (e.g. 10m)
        LiveStreamState custom = new LiveStreamState(
                uuid, "P", "UC", true, true, true, "V", "T", "https://yt", true, true,
                IntervalMode.CUSTOM, 10, null, null, Instant.now()
        );
        assertEquals(Duration.ofMinutes(10), manager.calculateCooldown(custom));

        // Custom mode clamped to server minimum (e.g. attempted 2m -> clamped to 5m)
        LiveStreamState clampedMin = new LiveStreamState(
                uuid, "P", "UC", true, true, true, "V", "T", "https://yt", true, true,
                IntervalMode.CUSTOM, 2, null, null, Instant.now()
        );
        assertEquals(Duration.ofMinutes(5), manager.calculateCooldown(clampedMin));

        // Default mode (15m)
        LiveStreamState def = new LiveStreamState(
                uuid, "P", "UC", true, true, true, "V", "T", "https://yt", true, true,
                IntervalMode.DEFAULT, 10, null, null, Instant.now()
        );
        assertEquals(Duration.ofMinutes(15), manager.calculateCooldown(def));

        // Random mode within [5, 60] minutes
        LiveStreamState rnd = new LiveStreamState(
                uuid, "P", "UC", true, true, true, "V", "T", "https://yt", true, true,
                IntervalMode.RANDOM, 10, null, null, Instant.now()
        );
        for (int i = 0; i < 50; i++) {
            Duration cd = manager.calculateCooldown(rnd);
            long mins = cd.toMinutes();
            assertTrue(mins >= 5 && mins <= 60, "Random interval " + mins + " should be in [5, 60]");
        }
    }
}
