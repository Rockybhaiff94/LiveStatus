package com.example.livestatus.premium;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Validates and processes incoming server-to-server payment gateway webhooks.
 * Enforces HMAC-SHA256 signature verification to prevent spoofing or unauthorized activation.
 */
public class PaymentWebhookHandler {

    private final PremiumService premiumService;
    private final String webhookSecret;
    private final Logger logger;

    public record WebhookResult(boolean success, int httpStatus, String responseMessage) {}

    public PaymentWebhookHandler(PremiumService premiumService, String webhookSecret, Logger logger) {
        this.premiumService = Objects.requireNonNull(premiumService, "premiumService");
        this.webhookSecret = webhookSecret != null ? webhookSecret : "";
        this.logger = logger;
    }

    /**
     * Verifies the HMAC-SHA256 signature of the payload against the provided header.
     */
    public boolean verifySignature(byte[] payload, String signatureHeader) {
        if (webhookSecret.isBlank() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] computedHmac = mac.doFinal(payload);
            String computedHex = HexFormat.of().formatHex(computedHmac);

            // Strip prefix if header is e.g. "sha256=abcdef..."
            String cleanProvided = signatureHeader.trim();
            if (cleanProvided.startsWith("sha256=")) {
                cleanProvided = cleanProvided.substring(7);
            }

            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    cleanProvided.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            if (logger != null) {
                logger.warning("[PaymentWebhook] Error verifying webhook signature: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Handles verified webhook payload.
     * Payload structure:
     * {
     *   "event": "payment.completed" | "payment.refunded",
     *   "orderId": "ORD-12345",
     *   "minecraftUuid": "...",
     *   "playerName": "...",
     *   "tier": "PRO",
     *   "durationDays": 30
     * }
     */
    public WebhookResult processWebhook(
            byte[] rawPayload,
            String signatureHeader,
            String eventType,
            String orderId,
            UUID playerUuid,
            String playerName,
            String tier,
            int durationDays
    ) {
        // Enforce signature check
        if (!verifySignature(rawPayload, signatureHeader)) {
            if (logger != null) {
                logger.warning("[PaymentWebhook] Rejected webhook request: Invalid HMAC signature.");
            }
            return new WebhookResult(false, 401, "Invalid webhook signature");
        }

        if (playerUuid == null || orderId == null || orderId.isBlank()) {
            return new WebhookResult(false, 400, "Missing required order metadata");
        }

        if ("payment.completed".equalsIgnoreCase(eventType) || "checkout.completed".equalsIgnoreCase(eventType)) {
            premiumService.activateFromPayment(playerUuid, playerName, tier, durationDays, orderId);
            return new WebhookResult(true, 200, "Premium activated successfully");
        } else if ("payment.refunded".equalsIgnoreCase(eventType) || "payment.cancelled".equalsIgnoreCase(eventType)) {
            premiumService.revokePremium(playerUuid);
            return new WebhookResult(true, 200, "Premium revoked due to refund/cancellation");
        }

        return new WebhookResult(true, 200, "Event ignored");
    }
}
