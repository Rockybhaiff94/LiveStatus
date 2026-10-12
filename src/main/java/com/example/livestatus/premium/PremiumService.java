package com.example.livestatus.premium;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;

/**
 * High-level service managing premium accounts, code validation/redemption,
 * administrative activation, and extensible premium features.
 */
public class PremiumService {

    private final PremiumStorage storage;
    private final Logger logger;

    public record RedemptionResult(boolean success, String message, PremiumRecord record) {}

    public PremiumService(PremiumStorage storage, Logger logger) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.logger = logger;
    }

    public boolean isPremium(UUID uuid) {
        if (uuid == null) return false;
        PremiumRecord record = storage.getPremiumUsers().get(uuid);
        return record != null && record.isActive();
    }

    public Optional<PremiumRecord> getRecord(UUID uuid) {
        return Optional.ofNullable(storage.getPremiumUsers().get(uuid));
    }

    public List<PremiumRecord> getAllActive() {
        return storage.getPremiumUsers().values().stream()
                .filter(PremiumRecord::isActive)
                .toList();
    }

    public int getActiveCount() {
        return (int) storage.getPremiumUsers().values().stream()
                .filter(PremiumRecord::isActive)
                .count();
    }

    /**
     * Redeems a private premium code for a given player UUID.
     * Prevents re-use and validates single-use status.
     */
    public synchronized RedemptionResult redeemCode(String rawCode, UUID playerUuid, String playerName) {
        if (rawCode == null || rawCode.isBlank()) {
            return new RedemptionResult(false, "Code cannot be empty.", null);
        }
        String cleanCode = rawCode.trim().toUpperCase(Locale.ROOT);

        PremiumCodeRecord codeRecord = storage.getCodes().get(cleanCode);
        if (codeRecord == null) {
            return new RedemptionResult(false, "Invalid premium code.", null);
        }

        if (codeRecord.redeemed()) {
            return new RedemptionResult(false, "This premium code has already been redeemed.", null);
        }

        Instant now = Instant.now();
        Instant expiresAt = codeRecord.durationDays() > 0 ? now.plus(codeRecord.durationDays(), ChronoUnit.DAYS) : null;

        // Mark code as redeemed
        PremiumCodeRecord updatedCode = codeRecord.withRedeemed(playerUuid, playerName);
        storage.getCodes().put(cleanCode, updatedCode);

        // Activate premium for the player
        PremiumRecord premiumRecord = new PremiumRecord(
                playerUuid,
                playerName != null ? playerName : "Unknown",
                codeRecord.tier() != null ? codeRecord.tier() : "PRO",
                now,
                expiresAt,
                "CODE",
                cleanCode,
                null,
                null,
                null
        );

        storage.getPremiumUsers().put(playerUuid, premiumRecord);
        storage.save();

        if (logger != null) {
            logger.info("[Premium] Player " + playerName + " successfully redeemed code " + cleanCode + " (Tier: " + premiumRecord.tier() + ")");
        }

        return new RedemptionResult(true, "Premium successfully activated!", premiumRecord);
    }

    /**
     * Activates premium via verified payment webhook.
     */
    public synchronized PremiumRecord activateFromPayment(
            UUID playerUuid,
            String playerName,
            String tier,
            int durationDays,
            String orderId
    ) {
        Instant now = Instant.now();
        Instant expiresAt = durationDays > 0 ? now.plus(durationDays, ChronoUnit.DAYS) : null;

        PremiumRecord record = new PremiumRecord(
                playerUuid,
                playerName != null ? playerName : "Unknown",
                tier != null ? tier : "PRO",
                now,
                expiresAt,
                "PAYMENT",
                orderId,
                null,
                null,
                null
        );

        storage.getPremiumUsers().put(playerUuid, record);
        storage.save();

        if (logger != null) {
            logger.info("[Premium] Verified payment order " + orderId + " activated premium for " + playerName);
        }
        return record;
    }

    /**
     * Manual admin activation.
     */
    public synchronized PremiumRecord setAdminPremium(
            UUID playerUuid,
            String playerName,
            String tier,
            int durationDays,
            String customLiveText,
            String customLiveIcon
    ) {
        Instant now = Instant.now();
        Instant expiresAt = durationDays > 0 ? now.plus(durationDays, ChronoUnit.DAYS) : null;

        PremiumRecord record = new PremiumRecord(
                playerUuid,
                playerName != null ? playerName : "Unknown",
                tier != null ? tier : "ADMIN",
                now,
                expiresAt,
                "ADMIN_OVERRIDE",
                "MANUAL",
                customLiveText,
                customLiveIcon,
                null
        );

        storage.getPremiumUsers().put(playerUuid, record);
        storage.save();
        return record;
    }

    public synchronized boolean revokePremium(UUID playerUuid) {
        PremiumRecord removed = storage.getPremiumUsers().remove(playerUuid);
        if (removed != null) {
            storage.save();
            if (logger != null) {
                logger.info("[Premium] Revoked premium for " + removed.playerName());
            }
            return true;
        }
        return false;
    }

    public synchronized void registerPreGeneratedCode(String code, String tier, int durationDays) {
        String clean = code.trim().toUpperCase(Locale.ROOT);
        storage.getCodes().put(clean, new PremiumCodeRecord(clean, tier, durationDays, false, null, null, null));
        storage.save();
    }

    public Map<String, PremiumCodeRecord> getAllCodes() {
        return Collections.unmodifiableMap(storage.getCodes());
    }

    public PremiumStorage getStorage() {
        return storage;
    }
}
