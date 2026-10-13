package com.example.livestatus.mcast;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class MCastServerIdentityTest {

    private Path tempDir;
    private Logger logger;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("mcast-identity-test");
        logger = Logger.getLogger("MCastServerIdentityTest");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    public void testGeneratesAndPersistsIdentityOnFirstBoot() {
        MCastServerIdentity identity = new MCastServerIdentity(tempDir.toFile(), logger);

        assertNotNull(identity.getServerId());
        assertTrue(identity.getServerId().startsWith("srv_"), "Server ID should start with srv_ prefix");
        assertNotNull(identity.getServerSecret());
        assertTrue(identity.getServerSecret().length() >= 64, "Server secret should have sufficient cryptographic length");
        assertTrue(identity.getRegisteredAt() > 0);

        File identityFile = new File(tempDir.toFile(), "server-identity.json");
        assertTrue(identityFile.exists(), "server-identity.json should be saved to disk");
    }

    @Test
    public void testLoadsExistingIdentityFromDisk() {
        MCastServerIdentity identity1 = new MCastServerIdentity(tempDir.toFile(), logger);
        String serverId = identity1.getServerId();
        String serverSecret = identity1.getServerSecret();

        // Reload identity from same directory
        MCastServerIdentity identity2 = new MCastServerIdentity(tempDir.toFile(), logger);
        assertEquals(serverId, identity2.getServerId());
        assertEquals(serverSecret, identity2.getServerSecret());
    }
}
