package com.example.livestatus.nametag;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Composite provider that forwards nametag operations to multiple underlying providers.
 */
public class CompositeNametagProvider implements NametagProvider {

    private final List<NametagProvider> providers;

    public CompositeNametagProvider(List<NametagProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public void applyLiveIndicator(Player player, Component indicator, String separator) {
        for (NametagProvider provider : providers) {
            provider.applyLiveIndicator(player, indicator, separator);
        }
    }

    @Override
    public void removeLiveIndicator(Player player) {
        for (NametagProvider provider : providers) {
            provider.removeLiveIndicator(player);
        }
    }

    @Override
    public boolean hasLiveIndicator(Player player) {
        for (NametagProvider provider : providers) {
            if (provider.hasLiveIndicator(player)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cleanUp(Player player) {
        for (NametagProvider provider : providers) {
            provider.cleanUp(player);
        }
    }

    @Override
    public void cleanUpAll() {
        for (NametagProvider provider : providers) {
            provider.cleanUpAll();
        }
    }
}

