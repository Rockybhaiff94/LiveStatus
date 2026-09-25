package com.example.livestatus.nametag;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Abstraction for applying and removing the LIVE indicator to a player's nametag.
 */
public interface NametagProvider {

    /**
     * Applies the LIVE indicator after the player's existing nametag.
     *
     * @param player    The target player.
     * @param indicator The rendered LIVE indicator component.
     * @param separator The separator string (e.g. " ").
     */
    void applyLiveIndicator(Player player, Component indicator, String separator);

    /**
     * Removes the LIVE indicator and completely restores the original nametag state.
     *
     * @param player The target player.
     */
    void removeLiveIndicator(Player player);

    /**
     * Checks if the player currently has the LIVE indicator applied.
     */
    boolean hasLiveIndicator(Player player);

    /**
     * Cleans up resources associated with a specific player (e.g. on disconnect).
     */
    void cleanUp(Player player);

    /**
     * Cleans up all applied indicators and restores original states across all players.
     */
    void cleanUpAll();
}

