package com.example.livestatus.premium;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PremiumServiceTest {

    private PremiumService premiumService;
    private PremiumStorage storage;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        storage = new PremiumStorage(tempDir.toFile(), Logger.getLogger("TestLogger"));
        premiumService = new PremiumService(storage, Logger.getLogger("TestLogger"));
    }

    @Test
    @DisplayName("Redeem valid premium code activates tier and prevents code reuse")
    void testRedeemCode() {
        String testCode = "YT-LIVE-PREM-TEST-1234";
        premiumService.registerPreGeneratedCode(testCode, "PRO", 30);

        UUID userUuid = UUID.randomUUID();
        String username = "ContentCreator99";

        // First redemption should succeed
        PremiumService.RedemptionResult result = premiumService.redeemCode(testCode, userUuid, username);
        assertTrue(result.success(), "First redemption should succeed");
        assertTrue(premiumService.isPremium(userUuid));

        assertTrue(premiumService.getRecord(userUuid).isPresent());
        PremiumRecord record = premiumService.getRecord(userUuid).get();
        assertEquals("PRO", record.tier());
        assertEquals("ContentCreator99", record.playerName());
        assertEquals(testCode, record.referenceId());

        // Second redemption attempt with the same code must fail
        PremiumService.RedemptionResult secondResult = premiumService.redeemCode(testCode, UUID.randomUUID(), "OtherUser");
        assertFalse(secondResult.success(), "Reusing a redeemed code must fail");
        assertTrue(secondResult.message().toLowerCase().contains("already been redeemed"));
    }

    @Test
    @DisplayName("Redeem invalid code fails safely")
    void testRedeemInvalidCode() {
        UUID userUuid = UUID.randomUUID();
        PremiumService.RedemptionResult result = premiumService.redeemCode("INVALID-CODE-XXX", userUuid, "Bob");

        assertFalse(result.success());
        assertFalse(premiumService.isPremium(userUuid));
    }

    @Test
    @DisplayName("Manual activation and deactivation correctly updates state")
    void testManualActivationAndDeactivation() {
        UUID userUuid = UUID.randomUUID();
        String username = "ServerOwner";

        premiumService.setAdminPremium(userUuid, username, "ENTERPRISE", -1, "[LIVE]", "🔴");

        assertTrue(premiumService.isPremium(userUuid));
        assertTrue(premiumService.getRecord(userUuid).isPresent());
        PremiumRecord record = premiumService.getRecord(userUuid).get();
        assertNull(record.expiresAt(), "Lifetime subscription should have null expiresAt");
        assertTrue(record.isActive());
        assertEquals("ENTERPRISE", record.tier());

        // Revoke
        assertTrue(premiumService.revokePremium(userUuid));
        assertFalse(premiumService.isPremium(userUuid));
        assertTrue(premiumService.getRecord(userUuid).isEmpty());
    }
}
