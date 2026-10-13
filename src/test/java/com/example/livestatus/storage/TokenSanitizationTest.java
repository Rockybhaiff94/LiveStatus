package com.example.livestatus.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class TokenSanitizationTest {

    private Path tempDir;
    private Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("token-sanitization-test");
        logger = Logger.getLogger("TokenSanitizationTest");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    public void testSanitizesLegacyTokensOnStartup() throws IOException {
        UUID playerUuid = UUID.randomUUID();
        File jsonFile = new File(tempDir.toFile(), "linked-accounts.json");

        // Write legacy record containing plaintext access and refresh tokens
        Map<String, Object> legacyMap = new HashMap<>();
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("minecraftUuid", playerUuid.toString());
        recordMap.put("playerName", "StreamerGuy");
        recordMap.put("youtubeChannelId", "UC1234567890abcdef");
        recordMap.put("channelTitle", "Streamer Guy Gaming");
        recordMap.put("customUrl", "@StreamerGuy");
        recordMap.put("accessToken", "ya29.sensitive_access_token_to_remove");
        recordMap.put("refreshToken", "1//sensitive_refresh_token_to_remove");
        recordMap.put("tokenExpiresAt", System.currentTimeMillis() + 3600_000);
        recordMap.put("linkedAt", System.currentTimeMillis());
        recordMap.put("lastVerifiedAt", System.currentTimeMillis());
        recordMap.put("linkStatus", "ACTIVE");
        legacyMap.put(playerUuid.toString(), recordMap);

        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(legacyMap, writer);
        }

        // Initialize JsonAccountStorage which loads the file
        JsonAccountStorage storage = new JsonAccountStorage(tempDir.toFile(), logger);
        boolean sanitized = storage.sanitizeLegacyTokens();
        assertTrue(sanitized, "Should detect and sanitize tokens");

        Optional<AccountRecord> opt = storage.findByUuid(playerUuid);
        assertTrue(opt.isPresent(), "Account record should still be present");

        AccountRecord loaded = opt.get();
        assertEquals("StreamerGuy", loaded.playerName());
        assertEquals("UC1234567890abcdef", loaded.youtubeChannelId());
        assertEquals("Streamer Guy Gaming", loaded.channelTitle());

        // Verify tokens were scrubbed
        assertTrue(loaded.accessToken() == null || loaded.accessToken().isBlank(), "Access token should be sanitized");
        assertTrue(loaded.refreshToken() == null || loaded.refreshToken().isBlank(), "Refresh token should be sanitized");

        // Verify the file on disk was also updated to scrub the tokens
        String fileContent = Files.readString(jsonFile.toPath());
        assertFalse(fileContent.contains("sensitive_access_token_to_remove"), "Plaintext access token must be scrubbed from disk");
        assertFalse(fileContent.contains("sensitive_refresh_token_to_remove"), "Plaintext refresh token must be scrubbed from disk");
    }
}
