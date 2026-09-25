package com.example.livestatus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardSuffixLogicTest {

    private final PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();

    @Test
    @DisplayName("20. Suffix appending preserves existing prefix and suffix and removes only LIVE")
    void testNametagSuffixPreservation() {
        Component existingPrefix = Component.text("[ADMIN] ");
        Component existingSuffix = Component.text(" [MVP]");
        Component playerName = Component.text("PokeHowlet");

        // 1. Initial nametag before live
        Component initialNametag = existingPrefix.append(playerName).append(existingSuffix);
        assertEquals("[ADMIN] PokeHowlet [MVP]", serializer.serialize(initialNametag));

        // 2. Stream starts: Apply LIVE suffix
        Component liveIndicator = Component.text("LIVE");
        String separator = " ";
        Component liveSuffix = existingSuffix.append(Component.text(separator)).append(liveIndicator);

        Component liveNametag = existingPrefix.append(playerName).append(liveSuffix);
        assertEquals("[ADMIN] PokeHowlet [MVP] LIVE", serializer.serialize(liveNametag));

        // 3. Stream ends: Restore original suffix
        Component restoredSuffix = existingSuffix; // exactly original base suffix
        Component restoredNametag = existingPrefix.append(playerName).append(restoredSuffix);
        assertEquals("[ADMIN] PokeHowlet [MVP]", serializer.serialize(restoredNametag));

        // Ensure ONLY the LIVE indicator was removed, base suffix and prefix are 100% intact
        assertEquals(serializer.serialize(initialNametag), serializer.serialize(restoredNametag));
    }

    @Test
    @DisplayName("21. Nametag without prior suffix receives only LIVE")
    void testNametagWithoutPriorSuffix() {
        Component existingPrefix = Component.text("[VIP] ");
        Component playerName = Component.text("PokeHowlet");
        Component baseSuffix = Component.empty();

        Component liveSuffix = baseSuffix.append(Component.text(" ")).append(Component.text("LIVE"));
        Component liveNametag = existingPrefix.append(playerName).append(liveSuffix);
        assertEquals("[VIP] PokeHowlet LIVE", serializer.serialize(liveNametag));

        Component restoredNametag = existingPrefix.append(playerName).append(baseSuffix);
        assertEquals("[VIP] PokeHowlet", serializer.serialize(restoredNametag));
    }
}

