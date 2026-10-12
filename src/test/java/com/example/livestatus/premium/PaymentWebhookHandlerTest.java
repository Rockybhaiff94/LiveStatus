package com.example.livestatus.premium;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PaymentWebhookHandlerTest {

    private PaymentWebhookHandler webhookHandler;
    private PremiumService premiumService;
    private final String webhookSecret = "super_secure_webhook_secret_key_998877";

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        PremiumStorage storage = new PremiumStorage(tempDir.toFile(), Logger.getLogger("TestLogger"));
        premiumService = new PremiumService(storage, Logger.getLogger("TestLogger"));
        webhookHandler = new PaymentWebhookHandler(premiumService, webhookSecret, Logger.getLogger("TestLogger"));
    }

    private String calculateHmac(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmac = mac.doFinal(payload);
        return HexFormat.of().formatHex(hmac);
    }

    @Test
    @DisplayName("Valid HMAC signature activates premium via webhook")
    void testValidWebhookActivation() throws Exception {
        UUID userUuid = UUID.randomUUID();
        String jsonPayload = """
                {"event":"payment.completed","orderId":"ORD-12345","uuid":"%s","username":"TopStreamer"}
                """.formatted(userUuid);
        byte[] payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);

        String signature = calculateHmac(payloadBytes, webhookSecret);

        PaymentWebhookHandler.WebhookResult result = webhookHandler.processWebhook(
                payloadBytes,
                signature,
                "payment.completed",
                "ORD-12345",
                userUuid,
                "TopStreamer",
                "PLUS",
                30
        );

        assertTrue(result.success());
        assertEquals(200, result.httpStatus());
        assertTrue(premiumService.isPremium(userUuid));

        assertTrue(premiumService.getRecord(userUuid).isPresent());
        PremiumRecord record = premiumService.getRecord(userUuid).get();
        assertEquals("PLUS", record.tier());
        assertEquals("TopStreamer", record.playerName());
        assertEquals("ORD-12345", record.referenceId());
    }

    @Test
    @DisplayName("Invalid or tampered signature is rejected with HTTP 401")
    void testInvalidSignature() {
        byte[] payloadBytes = "{\"test\":true}".getBytes(StandardCharsets.UTF_8);
        String fakeSignature = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

        PaymentWebhookHandler.WebhookResult result = webhookHandler.processWebhook(
                payloadBytes,
                fakeSignature,
                "payment.completed",
                "ORD-HACK",
                UUID.randomUUID(),
                "Attacker",
                "ENTERPRISE",
                -1
        );

        assertFalse(result.success());
        assertEquals(401, result.httpStatus());
        assertTrue(result.responseMessage().contains("Invalid webhook signature"));
    }

    @Test
    @DisplayName("Refund event safely revokes user premium")
    void testRefundEvent() throws Exception {
        UUID userUuid = UUID.randomUUID();
        premiumService.activateFromPayment(userUuid, "StreamerX", "PRO", 30, "ORD-REFUND-ME");
        assertTrue(premiumService.isPremium(userUuid));

        byte[] payloadBytes = "{\"refund\":true}".getBytes(StandardCharsets.UTF_8);
        String signature = calculateHmac(payloadBytes, webhookSecret);

        PaymentWebhookHandler.WebhookResult result = webhookHandler.processWebhook(
                payloadBytes,
                signature,
                "payment.refunded",
                "ORD-REFUND-ME",
                userUuid,
                "StreamerX",
                "PRO",
                0
        );

        assertTrue(result.success());
        assertFalse(premiumService.isPremium(userUuid));
    }
}
