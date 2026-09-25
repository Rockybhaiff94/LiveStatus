package com.example.livestatus.nametag;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * NametagProvider implementation utilizing Paper Scoreboard Teams.
 * Safely manages the LIVE indicator suffix without destroying existing teams, prefixes, or base suffixes.
 */
public class PaperTeamNametagProvider implements NametagProvider {

    private final Logger logger;
    private final Map<UUID, AppliedState> appliedStates = new ConcurrentHashMap<>();

    private record AppliedState(
            String dynamicTeamName,
            String originalTeamName,
            Component originalSuffix,
            boolean modifiedInPlace
    ) {}

    public PaperTeamNametagProvider() {
        this(null);
    }

    public PaperTeamNametagProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void applyLiveIndicator(Player player, Component indicator, String separator) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (appliedStates.containsKey(uuid)) {
            return; // Already applied
        }

        Scoreboard scoreboard = player.getScoreboard();
        String entry = player.getName();
        Team existingTeam = scoreboard.getEntryTeam(entry);

        Component liveSuffix = Component.text(separator != null ? separator : " ").append(indicator);

        if (existingTeam == null) {
            // Case 1: Player has no existing team. Create a dedicated dynamic team.
            String teamName = createDynamicTeamName(uuid);
            Team dynamicTeam = getOrCreateTeam(scoreboard, teamName);
            dynamicTeam.prefix(Component.empty());
            dynamicTeam.suffix(liveSuffix);
            dynamicTeam.addEntry(entry);

            appliedStates.put(uuid, new AppliedState(teamName, null, null, false));
        } else if (existingTeam.getEntries().size() == 1) {
            // Case 2: Player is the sole member of their team. Safely modify suffix in place.
            Component originalSuffix = existingTeam.suffix();
            Component baseSuffix = originalSuffix != null ? originalSuffix : Component.empty();
            existingTeam.suffix(baseSuffix.append(liveSuffix));

            appliedStates.put(uuid, new AppliedState(null, existingTeam.getName(), originalSuffix, true));
        } else {
            // Case 3: Player shares a team with other members.
            // Copy team visual properties to a personal dynamic team so other members are untouched.
            String teamName = createDynamicTeamName(uuid);
            Team dynamicTeam = getOrCreateTeam(scoreboard, teamName);

            dynamicTeam.prefix(existingTeam.prefix());
            if (existingTeam.color() instanceof net.kyori.adventure.text.format.NamedTextColor namedColor) {
                dynamicTeam.color(namedColor);
            }
            for (Team.Option option : Team.Option.values()) {
                try {
                    dynamicTeam.setOption(option, existingTeam.getOption(option));
                } catch (Exception e) {
                    if (logger != null) {
                        logger.finer("Could not copy team option " + option + ": " + e.getMessage());
                    }
                }
            }

            Component baseSuffix = existingTeam.suffix() != null ? existingTeam.suffix() : Component.empty();
            dynamicTeam.suffix(baseSuffix.append(liveSuffix));
            dynamicTeam.addEntry(entry);

            appliedStates.put(uuid, new AppliedState(teamName, existingTeam.getName(), existingTeam.suffix(), false));
        }

        if (logger != null) {
            logger.fine("Applied live indicator to " + player.getName());
        }
    }

    @Override
    public void removeLiveIndicator(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        AppliedState state = appliedStates.remove(uuid);
        if (state == null) {
            return;
        }

        Scoreboard scoreboard = player.isOnline() ? player.getScoreboard() : Bukkit.getScoreboardManager().getMainScoreboard();
        String entry = player.getName();

        if (state.modifiedInPlace()) {
            Team team = scoreboard.getTeam(state.originalTeamName());
            if (team != null) {
                team.suffix(state.originalSuffix() != null ? state.originalSuffix() : Component.empty());
            }
        } else if (state.dynamicTeamName() != null) {
            Team dynamicTeam = scoreboard.getTeam(state.dynamicTeamName());
            if (dynamicTeam != null) {
                dynamicTeam.removeEntry(entry);
                try {
                    dynamicTeam.unregister();
                } catch (Exception e) {
                    if (logger != null) {
                        logger.fine("Could not unregister dynamic team " + state.dynamicTeamName() + ": " + e.getMessage());
                    }
                }
            }

            // Restore membership in original team if applicable
            if (state.originalTeamName() != null) {
                Team originalTeam = scoreboard.getTeam(state.originalTeamName());
                if (originalTeam != null && player.isOnline()) {
                    originalTeam.addEntry(entry);
                }
            }
        }

        if (logger != null) {
            logger.fine("Removed live indicator from " + player.getName());
        }
    }

    @Override
    public boolean hasLiveIndicator(Player player) {
        return player != null && appliedStates.containsKey(player.getUniqueId());
    }

    @Override
    public void cleanUp(Player player) {
        removeLiveIndicator(player);
    }

    @Override
    public void cleanUpAll() {
        for (UUID uuid : appliedStates.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeLiveIndicator(player);
            } else {
                appliedStates.remove(uuid);
            }
        }
    }

    private Team getOrCreateTeam(Scoreboard scoreboard, String name) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        return team;
    }

    private String createDynamicTeamName(UUID uuid) {
        String hex = uuid.toString().replace("-", "");
        return "ytl_" + hex.substring(0, Math.min(12, hex.length()));
    }
}

