package com.example.livestatus.nametag;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * NametagProvider implementation utilizing Paper/Bukkit Scoreboard Teams.
 * Safely manages the LIVE indicator suffix across all active viewer scoreboards
 * without destroying existing teams, prefixes, base suffixes, or team memberships.
 */
public class PaperTeamNametagProvider implements NametagProvider {

    private final Logger logger;

    /**
     * Maps target streaming player UUID to their active indicator state across all scoreboards.
     */
    private final Map<UUID, AppliedPlayerState> appliedStates = new ConcurrentHashMap<>();

    public static class AppliedPlayerState {
        private volatile Player player;
        private volatile Component indicator;
        private volatile String separator;
        private final Map<Scoreboard, ScoreboardRecord> scoreboardRecords = new ConcurrentHashMap<>();

        public AppliedPlayerState(Player player, Component indicator, String separator) {
            this.player = player;
            this.indicator = indicator;
            this.separator = separator;
        }

        public Player getPlayer() {
            return player;
        }

        public void setPlayer(Player player) {
            this.player = player;
        }

        public Component getIndicator() {
            return indicator;
        }

        public void setIndicator(Component indicator) {
            this.indicator = indicator;
        }

        public String getSeparator() {
            return separator;
        }

        public void setSeparator(String separator) {
            this.separator = separator;
        }

        public Map<Scoreboard, ScoreboardRecord> getScoreboardRecords() {
            return scoreboardRecords;
        }
    }

    public record ScoreboardRecord(
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
        Component liveSuffix = Component.text(separator != null ? separator : " ").append(indicator);

        AppliedPlayerState state = appliedStates.computeIfAbsent(uuid, id -> new AppliedPlayerState(player, indicator, separator));
        state.setPlayer(player);
        state.setIndicator(indicator);
        state.setSeparator(separator);

        Set<Scoreboard> targetScoreboards = discoverActiveScoreboards(player);
        int appliedCount = 0;

        for (Scoreboard scoreboard : targetScoreboards) {
            try {
                if (applyToScoreboard(player, scoreboard, state, liveSuffix)) {
                    appliedCount++;
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("[LiveStatus] Could not apply team to scoreboard for " + player.getName() + ": " + e.getMessage());
                }
            }
        }

        if (logger != null) {
            logger.fine(String.format(
                    "[LiveStatus] Applied/reconciled LIVE indicator for %s (%s) across %d scoreboards (modified: %d)",
                    player.getName(), uuid, targetScoreboards.size(), appliedCount
            ));
        }
    }

    /**
     * Reconciles or applies the LIVE indicator onto a single scoreboard instance.
     *
     * @return true if the scoreboard was newly modified or repaired.
     */
    private boolean applyToScoreboard(Player player, Scoreboard scoreboard, AppliedPlayerState state, Component liveSuffix) {
        if (scoreboard == null) {
            return false;
        }

        String entry = player.getName();
        String dynamicTeamName = createDynamicTeamName(player.getUniqueId());
        Team currentTeam = scoreboard.getEntryTeam(entry);
        ScoreboardRecord existingRecord = state.getScoreboardRecords().get(scoreboard);

        // Case 1: Player already belongs to our dedicated dynamic team on this scoreboard
        if (currentTeam != null && currentTeam.getName().equals(dynamicTeamName)) {
            if (!liveSuffix.equals(currentTeam.suffix())) {
                currentTeam.suffix(liveSuffix);
                return true;
            }
            return false;
        }

        // Case 2: Previously modified an existing sole-member team in-place on this scoreboard
        if (existingRecord != null && existingRecord.modifiedInPlace() && existingRecord.originalTeamName() != null) {
            Team origTeam = scoreboard.getTeam(existingRecord.originalTeamName());
            if (origTeam != null && origTeam.hasEntry(entry)) {
                Component base = existingRecord.originalSuffix() != null ? existingRecord.originalSuffix() : Component.empty();
                Component targetSuffix = base.append(liveSuffix);
                if (!targetSuffix.equals(origTeam.suffix())) {
                    origTeam.suffix(targetSuffix);
                    return true;
                }
                return false;
            }
        }

        // Case 3: Player has no existing team on this scoreboard -> create dedicated dynamic team
        if (currentTeam == null) {
            Team dynamicTeam = getOrCreateTeam(scoreboard, dynamicTeamName);
            dynamicTeam.prefix(Component.empty());
            dynamicTeam.suffix(liveSuffix);
            if (!dynamicTeam.hasEntry(entry)) {
                dynamicTeam.addEntry(entry);
            }
            state.getScoreboardRecords().put(scoreboard, new ScoreboardRecord(dynamicTeamName, null, null, false));
            return true;
        }

        // Case 4: Player is the sole member of an existing team -> safely modify suffix in place
        if (currentTeam.getEntries().size() == 1) {
            Component originalSuffix = currentTeam.suffix();
            Component baseSuffix = originalSuffix != null ? originalSuffix : Component.empty();
            currentTeam.suffix(baseSuffix.append(liveSuffix));
            state.getScoreboardRecords().put(scoreboard, new ScoreboardRecord(null, currentTeam.getName(), originalSuffix, true));
            return true;
        }

        // Case 5: Player shares a team with other members -> copy properties to personal dynamic team
        String originalTeamName = currentTeam.getName();
        Component originalSuffix = currentTeam.suffix();

        Team dynamicTeam = getOrCreateTeam(scoreboard, dynamicTeamName);
        dynamicTeam.prefix(currentTeam.prefix());
        if (currentTeam.color() instanceof net.kyori.adventure.text.format.NamedTextColor namedColor) {
            dynamicTeam.color(namedColor);
        }
        for (Team.Option option : Team.Option.values()) {
            try {
                dynamicTeam.setOption(option, currentTeam.getOption(option));
            } catch (Exception e) {
                if (logger != null) {
                    logger.finer("Could not copy team option " + option + ": " + e.getMessage());
                }
            }
        }

        Component baseSuffix = originalSuffix != null ? originalSuffix : Component.empty();
        dynamicTeam.suffix(baseSuffix.append(liveSuffix));
        if (!dynamicTeam.hasEntry(entry)) {
            dynamicTeam.addEntry(entry);
        }

        state.getScoreboardRecords().put(scoreboard, new ScoreboardRecord(dynamicTeamName, originalTeamName, originalSuffix, false));
        return true;
    }

    @Override
    public void removeLiveIndicator(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        AppliedPlayerState state = appliedStates.remove(uuid);
        String dynamicTeamName = createDynamicTeamName(uuid);

        Set<Scoreboard> targetScoreboards = discoverActiveScoreboards(player);
        if (state != null) {
            targetScoreboards.addAll(state.getScoreboardRecords().keySet());
        }

        for (Scoreboard scoreboard : targetScoreboards) {
            ScoreboardRecord record = state != null ? state.getScoreboardRecords().get(scoreboard) : null;
            try {
                removeFromScoreboard(player, scoreboard, dynamicTeamName, record);
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("[LiveStatus] Could not clean up team from scoreboard for " + player.getName() + ": " + e.getMessage());
                }
            }
        }

        if (logger != null) {
            logger.fine("[LiveStatus] Removed LIVE indicator from " + player.getName() + " across all scoreboards");
        }
    }

    private void removeFromScoreboard(Player player, Scoreboard scoreboard, String dynamicTeamName, ScoreboardRecord record) {
        if (scoreboard == null) {
            return;
        }

        String entry = player.getName();

        if (record != null && record.modifiedInPlace()) {
            if (record.originalTeamName() != null) {
                Team team = scoreboard.getTeam(record.originalTeamName());
                if (team != null) {
                    team.suffix(record.originalSuffix() != null ? record.originalSuffix() : Component.empty());
                }
            }
        } else {
            Team dynamicTeam = scoreboard.getTeam(dynamicTeamName);
            if (dynamicTeam != null) {
                dynamicTeam.removeEntry(entry);
                try {
                    dynamicTeam.unregister();
                } catch (Exception e) {
                    if (logger != null) {
                        logger.fine("Could not unregister dynamic team " + dynamicTeamName + ": " + e.getMessage());
                    }
                }
            }

            // Restore membership in original team if applicable
            if (record != null && record.originalTeamName() != null) {
                Team originalTeam = scoreboard.getTeam(record.originalTeamName());
                if (originalTeam != null && player.isOnline()) {
                    originalTeam.addEntry(entry);
                }
            }
        }
    }

    @Override
    public boolean hasLiveIndicator(Player player) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        AppliedPlayerState state = appliedStates.get(uuid);
        if (state == null) {
            return false;
        }

        // If player is online, verify that their indicator is still physically present
        if (player.isOnline()) {
            try {
                Scoreboard sb = player.getScoreboard();
                if (sb != null) {
                    String dynamicTeamName = createDynamicTeamName(uuid);
                    Team dyn = sb.getTeam(dynamicTeamName);
                    if (dyn != null && dyn.hasEntry(player.getName())) {
                        return true;
                    }
                    ScoreboardRecord rec = state.getScoreboardRecords().get(sb);
                    if (rec != null && rec.modifiedInPlace() && rec.originalTeamName() != null) {
                        Team orig = sb.getTeam(rec.originalTeamName());
                        return orig != null && orig.hasEntry(player.getName());
                    }
                    // Current scoreboard was replaced or team was removed
                    return false;
                }
            } catch (Exception ignored) {}
        }
        return true;
    }

    @Override
    public void cleanUp(Player player) {
        removeLiveIndicator(player);
    }

    @Override
    public void cleanUpAll() {
        for (Map.Entry<UUID, AppliedPlayerState> entry : appliedStates.entrySet()) {
            UUID uuid = entry.getKey();
            AppliedPlayerState state = entry.getValue();
            Player player = null;
            try {
                if (Bukkit.getServer() != null) {
                    player = Bukkit.getPlayer(uuid);
                }
            } catch (Exception ignored) {}
            if (player == null) {
                player = state.getPlayer();
            }
            if (player != null) {
                removeLiveIndicator(player);
            }
        }
        appliedStates.clear();
    }

    /**
     * Called when a viewer joins the server to instantly synchronize all active live indicators
     * onto the viewer's new scoreboard.
     */
    @Override
    public void onViewerJoin(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        Scoreboard viewerScoreboard = viewer.getScoreboard();
        if (viewerScoreboard == null) {
            return;
        }

        for (Map.Entry<UUID, AppliedPlayerState> entry : appliedStates.entrySet()) {
            UUID targetUuid = entry.getKey();
            AppliedPlayerState state = entry.getValue();
            Player targetPlayer = null;
            try {
                if (Bukkit.getServer() != null) {
                    targetPlayer = Bukkit.getPlayer(targetUuid);
                }
            } catch (Exception ignored) {}
            if (targetPlayer == null) {
                targetPlayer = state.getPlayer();
            }
            if (targetPlayer != null && targetPlayer.isOnline()) {
                Component liveSuffix = Component.text(state.getSeparator() != null ? state.getSeparator() : " ")
                        .append(state.getIndicator());
                try {
                    applyToScoreboard(targetPlayer, viewerScoreboard, state, liveSuffix);
                } catch (Exception e) {
                    if (logger != null) {
                        logger.fine("Could not synchronize live team for " + targetPlayer.getName() + " onto viewer " + viewer.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Collects all active scoreboard instances currently in use across the server.
     */
    private Set<Scoreboard> discoverActiveScoreboards(Player targetPlayer) {
        Set<Scoreboard> scoreboards = Collections.newSetFromMap(new IdentityHashMap<>());

        try {
            if (Bukkit.getServer() != null && Bukkit.getScoreboardManager() != null) {
                Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
                if (main != null) {
                    scoreboards.add(main);
                }
            }
        } catch (Exception ignored) {}

        if (targetPlayer != null && targetPlayer.isOnline()) {
            try {
                Scoreboard targetSb = targetPlayer.getScoreboard();
                if (targetSb != null) {
                    scoreboards.add(targetSb);
                }
            } catch (Exception ignored) {}
        }

        try {
            if (Bukkit.getServer() != null) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    try {
                        Scoreboard sb = online.getScoreboard();
                        if (sb != null) {
                            scoreboards.add(sb);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        return scoreboards;
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
