package com.example.livestatus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CommandRegistryValidationTest {

    @Test
    @DisplayName("Command registry JSON contains valid, canonical, implemented commands")
    void testRegistryIntegrity() {
        InputStream stream = getClass().getResourceAsStream("/commands.json");
        assertNotNull(stream, "commands.json must be present in resources");

        Gson gson = new Gson();
        JsonArray array = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonArray.class);
        assertNotNull(array);
        assertEquals(14, array.size(), "Should have exactly 14 implemented canonical commands in registry");

        int initialCount = 0;
        int playerCount = 0;
        int adminCount = 0;
        Set<String> syntaxes = new HashSet<>();

        for (JsonElement elem : array) {
            JsonObject obj = elem.getAsJsonObject();
            String id = obj.get("id").getAsString();
            String syntax = obj.get("syntax").getAsString();
            String category = obj.get("category").getAsString();
            String permission = obj.get("permission").getAsString();
            boolean implemented = obj.get("implemented").getAsBoolean();
            boolean initialVisible = obj.get("initialVisible").getAsBoolean();

            assertFalse(id.isBlank());
            assertTrue(syntax.startsWith("/mcast "), "Canonical command syntax must start with /mcast : " + syntax);
            assertTrue(category.equals("player") || category.equals("admin"));
            assertTrue(permission.equals("mcast.link") || permission.equals("mcast.admin"));
            assertTrue(implemented, "Command must be implemented: " + syntax);

            if (category.equals("player")) playerCount++;
            if (category.equals("admin")) adminCount++;
            if (initialVisible) initialCount++;

            syntaxes.add(syntax);
        }

        assertEquals(14, syntaxes.size(), "All commands must have unique syntaxes");
        assertEquals(4, initialCount, "Exactly 4 commands must be displayed by default in initial view");
        assertEquals(4, playerCount, "Must have 4 player commands");
        assertEquals(10, adminCount, "Must have 10 admin commands");

        // Specific canonical check
        assertTrue(syntaxes.contains("/mcast link"));
        assertTrue(syntaxes.contains("/mcast unlink"));
        assertTrue(syntaxes.contains("/mcast status"));
        assertTrue(syntaxes.contains("/mcast info <player>"));
        assertTrue(syntaxes.contains("/mcast reload"));
        assertTrue(syntaxes.contains("/mcast admin set <player> <url>"));
        assertTrue(syntaxes.contains("/mcast admin remove <player>"));
        assertTrue(syntaxes.contains("/mcast admin grant <player>"));
        assertTrue(syntaxes.contains("/mcast admin revoke <player>"));
        assertTrue(syntaxes.contains("/mcast set announcement <message>"));
        assertTrue(syntaxes.contains("/mcast authorize user <player>"));
        assertTrue(syntaxes.contains("/mcast remove user <player>"));
        assertTrue(syntaxes.contains("/mcast user list"));
        assertTrue(syntaxes.contains("/mcast youtuber list"));
    }
}
