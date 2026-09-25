package com.example.livestatus.player;

import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing linked player state, authorization, and caching.
 */
public class PlayerLinkService {

    private final AllowedPlayersStorage allowedStorage;
    private final Map<UUID, LinkedPlayer> players = new ConcurrentHashMap<>();

    public PlayerLinkService(AllowedPlayersStorage allowedStorage) {
        this.allowedStorage = Objects.requireNonNull(allowedStorage, "allowedStorage");
    }

    public LinkedPlayer getOrCreate(UUID uuid, String playerName) {
        return players.computeIfAbsent(uuid, id -> {
            LinkedPlayer player = new LinkedPlayer(id, playerName);
            player.setLinkingAllowed(allowedStorage.isAllowed(id));
            return player;
        });
    }

    public Optional<LinkedPlayer> get(UUID uuid) {
        return Optional.ofNullable(players.get(uuid));
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
        if (player != null) {
            player.setLinked(linked);
            player.setAccount(account);
            if (!linked) {
                player.setStatus(YouTubeStatus.NOT_LINKED);
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
    }

    public Collection<LinkedPlayer> getAllTrackedPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public void clear() {
        players.clear();
    }
}

