package com.example.livestatus.nametag;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NametagProvider for servers that delegate overhead nametag and tablist formatting
 * to external plugins (such as TAB or NameTagEdit) via PlaceholderAPI.
 */
public class PlaceholderProvider implements NametagProvider {

    private final Map<UUID, Boolean> livePlayers = new ConcurrentHashMap<>();

    @Override
    public void applyLiveIndicator(Player player, Component indicator, String separator) {
        if (player != null) {
            livePlayers.put(player.getUniqueId(), Boolean.TRUE);
        }
    }

    @Override
    public void removeLiveIndicator(Player player) {
        if (player != null) {
            livePlayers.remove(player.getUniqueId());
        }
    }

    @Override
    public boolean hasLiveIndicator(Player player) {
        return player != null && Boolean.TRUE.equals(livePlayers.get(player.getUniqueId()));
    }

    @Override
    public void cleanUp(Player player) {
        removeLiveIndicator(player);
    }

    @Override
    public void cleanUpAll() {
        livePlayers.clear();
    }
}

