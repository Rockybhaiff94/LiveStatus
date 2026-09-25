package com.example.livestatus.storage;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface for persisting linked YouTube accounts and OAuth tokens.
 */
public interface AccountStorage {

    /**
     * Saves or updates an account record.
     */
    void saveOrUpdate(AccountRecord record);

    /**
     * Retrieves an account record by Minecraft player UUID.
     */
    Optional<AccountRecord> findByUuid(UUID uuid);

    /**
     * Updates refreshed access token and expiration timestamp.
     */
    void updateTokens(UUID uuid, String accessToken, long expiresAt);

    /**
     * Deletes the linked account associated with the given Minecraft player UUID.
     */
    boolean deleteByUuid(UUID uuid);

    /**
     * Returns all stored account records.
     */
    Collection<AccountRecord> getAllAccounts();

    /**
     * Reloads data from disk.
     */
    void load();

    /**
     * Flushes data to disk.
     */
    void save();
}

