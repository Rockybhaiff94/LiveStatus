package com.example.livestatus.player;

import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing linked player state, authorization, and caching.
 * Restores and persists account associations to local storage across restarts.
 */
public class PlayerLinkService {

    private final AllowedPlayersStorage allowedStorage;
    private final AccountStorage accountStorage;
    private final Map<UUID, LinkedPlayer> players = new ConcurrentHashMap<>();

    public PlayerLinkService(AllowedPlayersStorage allowedStorage) {
        this(allowedStorage, null);
    }

    public PlayerLinkService(AllowedPlayersStorage allowedStorage, AccountStorage accountStorage) {
        this.allowedStorage = Objects.requireNonNull(allowedStorage, "allowedStorage");
        this.accountStorage = accountStorage;
    }

    public LinkedPlayer getOrCreate(UUID uuid, String playerName) {
        LinkedPlayer player = players.computeIfAbsent(uuid, id -> {
            LinkedPlayer lp = new LinkedPlayer(id, playerName != null ? playerName : id.toString());
            lp.setLinkingAllowed(allowedStorage.isAllowed(id));
            return lp;
        });

        if (playerName != null && !playerName.isBlank()) {
            player.setPlayerName(playerName);
        }

        // Auto-restore account association from persistent storage if not already loaded in memory
        if (!player.isLinked() && accountStorage != null) {
            accountStorage.findByUuid(uuid).ifPresent(record -> {
                if (record.youtubeChannelId() != null && !record.youtubeChannelId().isBlank()) {
                    YouTubeAccount acc = new YouTubeAccount(record.youtubeChannelId(), record.channelTitle(), record.customUrl());
                    player.setAccount(acc);
                    player.setLinked(true);
                }
            });
        }

        return player;
    }

    public Optional<LinkedPlayer> get(UUID uuid) {
        LinkedPlayer player = players.get(uuid);
        if (player != null && !player.isLinked() && accountStorage != null) {
            accountStorage.findByUuid(uuid).ifPresent(record -> {
                if (record.youtubeChannelId() != null && !record.youtubeChannelId().isBlank()) {
                    YouTubeAccount acc = new YouTubeAccount(record.youtubeChannelId(), record.channelTitle(), record.customUrl());
                    player.setAccount(acc);
                    player.setLinked(true);
                }
            });
        }
        return Optional.ofNullable(player);
    }

    public boolean isAuthorizedToLink(UUID uuid) {
        return allowedStorage.isAllowed(uuid);
    }

    public void allowLink(UUID uuid, String playerName) {
        allowedStorage.allowPlayer(uuid, playerName);
        LinkedPlayer player = getOrCreate(uuid, playerName);
        player.setLinkingAllowed(true);
    }

    public boolean revokeLink(UUID uuid) {
        boolean revoked = allowedStorage.revokePlayer(uuid);
        LinkedPlayer player = players.get(uuid);
        if (player != null) {
            player.setLinkingAllowed(false);
        }
        return revoked;
    }

    public void updateAccount(UUID uuid, boolean linked, YouTubeAccount account) {
        LinkedPlayer player = players.get(uuid);
        if (player == null) {
            player = getOrCreate(uuid, uuid.toString());
        }

        if (player != null) {
            player.setLinked(linked);
            player.setAccount(account);
            if (!linked) {
                player.setStatus(YouTubeStatus.NOT_LINKED);
                if (accountStorage != null) {
                    accountStorage.deleteByUuid(uuid);
                }
            } else if (account != null && account.channelId() != null && !account.channelId().isBlank()) {
                if (accountStorage != null) {
                    AccountRecord existing = accountStorage.findByUuid(uuid).orElse(null);
                    AccountRecord rec = new AccountRecord(
                            uuid,
                            player.getPlayerName() != null ? player.getPlayerName() : (existing != null ? existing.playerName() : uuid.toString()),
                            account.channelId(),
                            account.channelTitle(),
                            account.customUrl() != null ? account.customUrl() : (existing != null ? existing.customUrl() : null),
                            "",
                            "",
                            0L,
                            existing != null && existing.linkedAt() > 0 ? existing.linkedAt() : System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            "ACTIVE"
                    );
                    accountStorage.saveOrUpdate(rec);
                }
            }
        }
    }

    public void updateStatus(UUID uuid, YouTubeStatus status, String errorMessage) {
        LinkedPlayer player = players.get(uuid);
        if (player != null) {
            player.setStatus(status);
            player.setLastError(errorMessage);
        }
    }

    public void unlink(UUID uuid) {
        LinkedPlayer player = players.get(uuid);
        if (player != null) {
            player.setLinked(false);
            player.setAccount(null);
            player.setStatus(YouTubeStatus.NOT_LINKED);
        }
        if (accountStorage != null) {
            accountStorage.deleteByUuid(uuid);
        }
    }

    public Collection<LinkedPlayer> getAllTrackedPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public void clear() {
        players.clear();
    }
}

