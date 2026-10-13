package com.example.livestatus.command;

import com.example.livestatus.LiveStatusPlugin;
import com.example.livestatus.chat.IntervalMode;
import com.example.livestatus.chat.LiveAnnouncementManager;
import com.example.livestatus.chat.LiveStreamState;
import com.example.livestatus.chat.StreamerChatSettings;
import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.stats.PluginStatistics;
import com.example.livestatus.storage.StreamerConfigStore;
import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeBroadcast;
import com.example.livestatus.youtube.YouTubeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;

/**
 * Handles all commands under /youtube (/yt, /livestatus), including admin commands
 * and streamer live chat announcement settings.
 */
public class YoutubeCommand implements CommandExecutor, TabCompleter {

    private final LiveStatusPlugin plugin;
    private final PlayerLinkService linkService;
    private final YouTubeService youTubeService;
    private final LiveAnnouncementManager announcementManager;
    private final StreamerConfigStore streamerConfigStore;

    public YoutubeCommand(LiveStatusPlugin plugin, PlayerLinkService linkService, YouTubeService youTubeService) {
        this(plugin, linkService, youTubeService, plugin != null ? plugin.getAnnouncementManager() : null, plugin != null ? plugin.getStreamerConfigStore() : null);
    }

    public YoutubeCommand(
            LiveStatusPlugin plugin,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            LiveAnnouncementManager announcementManager,
            StreamerConfigStore streamerConfigStore
    ) {
        this.plugin = plugin;
        this.linkService = linkService;
        this.youTubeService = youTubeService;
        this.announcementManager = announcementManager;
        this.streamerConfigStore = streamerConfigStore;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("chat")) {
            handleChat(sender, label, args);
            return true;
        }

        // Administrative commands require admin permissions
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "allow-link" -> handleAllowLink(sender, args);
            case "revoke-link" -> handleRevokeLink(sender, args);
            case "status" -> handleStatus(sender, args);
            case "unlink" -> handleUnlink(sender, args);
            case "reload" -> handleReload(sender);
            case "panel" -> handlePanel(sender, args);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void handlePanel(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("revoke")) {
            if (plugin != null && plugin.getWebSecurityManager() != null) {
                plugin.getWebSecurityManager().revokeAll();
                sender.sendMessage(Component.text("[YTLive] All active web panel sessions have been revoked.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("[YTLive] Web panel is not active.", NamedTextColor.RED));
            }
            return;
        }

        if (plugin == null || plugin.getWebSecurityManager() == null || !plugin.getPluginConfig().isWebEnabled()) {
            sender.sendMessage(Component.text("[YTLive] Web panel is disabled in configuration.", NamedTextColor.RED));
            return;
        }

        PluginConfig cfg = plugin.getPluginConfig();
        int expiryHours = cfg.getWebLinkExpirationHours();
        java.time.Duration duration = java.time.Duration.ofHours(expiryHours);
        com.example.livestatus.web.PanelSession session = plugin.getWebSecurityManager().createSession(sender.getName(), duration);

        String host = cfg.getWebHost();
        if ("0.0.0.0".equals(host)) {
            host = "localhost";
        }
        String proto = cfg.isWebSslEnabled() ? "https" : "http";
        String url = proto + "://" + host + ":" + cfg.getWebPort() + "/panel/" + session.token();

        sender.sendMessage(Component.text("----------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("[YTLive] Web Panel:", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Access Code: ", NamedTextColor.GRAY)
                .append(Component.text(session.accessCode(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("Expires: ", NamedTextColor.GRAY)
                .append(Component.text(expiryHours + " hours from now", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("----------------------------------------", NamedTextColor.DARK_GRAY));
    }

    // =========================================================================
    // CHAT ANNOUNCEMENT COMMAND ROUTING
    // =========================================================================

    private void handleChat(CommandSender sender, String label, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("admin")) {
            handleChatAdmin(sender, label, args);
            return;
        }

        // Streamer chat commands must be run by an in-game player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Streamer chat announcement settings can only be configured by in-game players.", NamedTextColor.RED));
            return;
        }

        if (!hasChatPermission(player)) {
            player.sendMessage(Component.text("You do not have permission to configure live chat announcements.", NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!plugin.getAllowedStorage().isAllowed(uuid)) {
            player.sendMessage(Component.text("You are not an authorized streamer on this server.", NamedTextColor.RED));
            return;
        }

        if (plugin.getAccountStorage().findByUuid(uuid).isEmpty()) {
            player.sendMessage(Component.text("You must link your YouTube account first using ", NamedTextColor.RED)
                    .append(Component.text("/link youtube", NamedTextColor.YELLOW, TextDecoration.UNDERLINED)));
            return;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("help")) {
            sendChatHelp(player, label);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "status" -> handleStreamerChatStatus(player);
            case "on" -> handleStreamerChatToggle(player, true);
            case "off" -> handleStreamerChatToggle(player, false);
            case "title" -> handleStreamerChatTitle(player, args);
            case "interval" -> handleStreamerChatInterval(player, args);
            default -> sendChatHelp(player, label);
        }
    }

    private void handleStreamerChatStatus(Player player) {
        StreamerConfigStore store = getEffectiveStore();
        if (store == null) return;

        StreamerChatSettings settings = store.getOrCreate(player.getUniqueId(), player.getName());
        LiveStreamState state = announcementManager != null ? announcementManager.buildCurrentState(player.getUniqueId()) : null;

        player.sendMessage(Component.text("=== Your YouTube Live Chat Settings ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Announcements: ", NamedTextColor.GRAY)
                .append(settings.isAdminDisabled()
                        ? Component.text("DISABLED (by Admin)", NamedTextColor.RED)
                        : (settings.isEnabled() ? Component.text("ENABLED", NamedTextColor.GREEN) : Component.text("DISABLED", NamedTextColor.RED))));

        String modeInfo = switch (settings.getIntervalMode()) {
            case CUSTOM -> "CUSTOM (" + settings.getCustomIntervalMinutes() + " minutes)";
            case RANDOM -> "RANDOM (" + plugin.getPluginConfig().getRandomMinimumMinutes() + "m - " + plugin.getPluginConfig().getRandomMaximumMinutes() + "m)";
            case DEFAULT -> "DEFAULT (" + plugin.getPluginConfig().getDefaultIntervalMinutes() + " minutes)";
        };
        player.sendMessage(Component.text("Interval Mode: ", NamedTextColor.GRAY).append(Component.text(modeInfo, NamedTextColor.AQUA)));

        String customTitle = settings.getCustomTitle();
        if (customTitle != null && !customTitle.isBlank()) {
            player.sendMessage(Component.text("Custom Title: ", NamedTextColor.GRAY).append(Component.text("\"" + customTitle + "\"", NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text("Custom Title: ", NamedTextColor.GRAY).append(Component.text("(None - dynamic YouTube title)", NamedTextColor.DARK_GRAY)));
        }

        if (state != null) {
            player.sendMessage(Component.text("Currently Live: ", NamedTextColor.GRAY)
                    .append(state.currentlyLive() ? Component.text("YES (Live on YouTube)", NamedTextColor.GREEN) : Component.text("NO (Offline)", NamedTextColor.RED)));
            if (state.currentlyLive()) {
                player.sendMessage(Component.text("Stream Title: ", NamedTextColor.GRAY).append(Component.text(state.getEffectiveTitle(), NamedTextColor.YELLOW)));
                if (state.nextEligibleTime() != null) {
                    if (state.nextEligibleTime().isBefore(java.time.Instant.now())) {
                        player.sendMessage(Component.text("Cooldown: ", NamedTextColor.GRAY).append(Component.text("Ready / Eligible now", NamedTextColor.GREEN)));
                    } else {
                        Duration d = Duration.between(java.time.Instant.now(), state.nextEligibleTime());
                        player.sendMessage(Component.text("Cooldown: ", NamedTextColor.GRAY).append(Component.text("~" + formatDuration(d) + " remaining", NamedTextColor.YELLOW)));
                    }
                }
            }
        }
    }

    private void handleStreamerChatToggle(Player player, boolean enable) {
        StreamerConfigStore store = getEffectiveStore();
        if (store == null) return;

        PluginConfig config = plugin.getPluginConfig();
        if (!enable && !config.isStreamerCanDisable()) {
            player.sendMessage(Component.text("Server administrators have enforced live announcements. You cannot disable them.", NamedTextColor.RED));
            return;
        }

        StreamerChatSettings settings = store.getOrCreate(player.getUniqueId(), player.getName());
        settings.setEnabled(enable);
        store.saveOrUpdate(settings);

        if (enable) {
            player.sendMessage(Component.text("Your YouTube Live chat announcements are now ", NamedTextColor.GREEN)
                    .append(Component.text("ENABLED", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("Your YouTube Live chat announcements are now ", NamedTextColor.RED)
                    .append(Component.text("DISABLED", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.RED)));
        }
    }

    private void handleStreamerChatTitle(Player player, String[] args) {
        StreamerConfigStore store = getEffectiveStore();
        if (store == null) return;

        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /youtube chat title <clear | text...>", NamedTextColor.RED));
            return;
        }

        StreamerChatSettings settings = store.getOrCreate(player.getUniqueId(), player.getName());

        if (args[2].equalsIgnoreCase("clear")) {
            settings.setCustomTitle(null);
            store.saveOrUpdate(settings);
            player.sendMessage(Component.text("Custom announcement title cleared. Using dynamic YouTube stream title.", NamedTextColor.GREEN));
            return;
        }

        String rawTitle = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        String sanitized = sanitizeTitle(rawTitle);

        if (sanitized.length() > 100) {
            player.sendMessage(Component.text("Custom announcement title cannot exceed 100 characters.", NamedTextColor.RED));
            return;
        }

        settings.setCustomTitle(sanitized);
        store.saveOrUpdate(settings);
        player.sendMessage(Component.text("Custom announcement title set to: ", NamedTextColor.GREEN)
                .append(Component.text("\"" + sanitized + "\"", NamedTextColor.YELLOW)));
    }

    private void handleStreamerChatInterval(Player player, String[] args) {
        StreamerConfigStore store = getEffectiveStore();
        if (store == null) return;

        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /youtube chat interval <minutes | random | default>", NamedTextColor.RED));
            return;
        }

        PluginConfig config = plugin.getPluginConfig();
        StreamerChatSettings settings = store.getOrCreate(player.getUniqueId(), player.getName());
        String val = args[2].toLowerCase();

        if (val.equals("random")) {
            if (!config.isRandomIntervalEnabled()) {
                player.sendMessage(Component.text("RANDOM interval mode is disabled on this server.", NamedTextColor.RED));
                return;
            }
            settings.setIntervalMode(IntervalMode.RANDOM);
            store.saveOrUpdate(settings);
            player.sendMessage(Component.text("Announcement interval set to ", NamedTextColor.GREEN)
                    .append(Component.text("RANDOM", NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(" (" + config.getRandomMinimumMinutes() + "m - " + config.getRandomMaximumMinutes() + "m).", NamedTextColor.GREEN)));
            return;
        }

        if (val.equals("default")) {
            settings.setIntervalMode(IntervalMode.DEFAULT);
            store.saveOrUpdate(settings);
            player.sendMessage(Component.text("Announcement interval reset to ", NamedTextColor.GREEN)
                    .append(Component.text("DEFAULT", NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(" (" + config.getDefaultIntervalMinutes() + " minutes).", NamedTextColor.GREEN)));
            return;
        }

        try {
            int minutes = Integer.parseInt(val);
            if (minutes < config.getMinimumIntervalMinutes()) {
                player.sendMessage(Component.text("Minimum announcement interval is " + config.getMinimumIntervalMinutes() + " minutes.", NamedTextColor.RED));
                return;
            }
            if (minutes > config.getMaximumIntervalMinutes()) {
                player.sendMessage(Component.text("Maximum announcement interval is " + config.getMaximumIntervalMinutes() + " minutes.", NamedTextColor.RED));
                return;
            }

            settings.setIntervalMode(IntervalMode.CUSTOM);
            settings.setCustomIntervalMinutes(minutes);
            store.saveOrUpdate(settings);
            player.sendMessage(Component.text("Announcement interval set to ", NamedTextColor.GREEN)
                    .append(Component.text(minutes + " minutes", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" (CUSTOM mode).", NamedTextColor.GREEN)));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid interval: must be a number of minutes, 'random', or 'default'.", NamedTextColor.RED));
        }
    }

    // =========================================================================
    // ADMINISTRATOR CHAT COMMANDS
    // =========================================================================

    private void handleChatAdmin(CommandSender sender, String label, String[] args) {
        if (!hasChatAdminPermission(sender)) {
            sender.sendMessage(Component.text("You do not have permission to manage chat announcement administration.", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sendChatAdminHelp(sender, label);
            return;
        }

        String sub = args[2].toLowerCase();
        switch (sub) {
            case "status" -> handleChatAdminStatus(sender);
            case "on" -> handleChatAdminGlobalToggle(sender, true);
            case "off" -> handleChatAdminGlobalToggle(sender, false);
            case "enable" -> handleChatAdminPlayerToggle(sender, args, true);
            case "disable" -> handleChatAdminPlayerToggle(sender, args, false);
            case "interval" -> handleChatAdminInterval(sender, args);
            case "title" -> handleChatAdminTitle(sender, args);
            default -> sendChatAdminHelp(sender, label);
        }
    }

    private void handleChatAdminStatus(CommandSender sender) {
        PluginStatistics stats = announcementManager != null ? announcementManager.getStatistics() : new PluginStatistics();
        PluginConfig config = plugin.getPluginConfig();

        int authorized = plugin.getAllowedStorage().getAllowedUuids().size();
        int linked = plugin.getAccountStorage().getAllAccounts().size();

        int liveCount = 0;
        int eligibleCount = 0;
        int enabledCount = 0;
        int disabledCount = 0;

        java.time.Instant now = java.time.Instant.now();
        if (announcementManager != null) {
            for (UUID uuid : announcementManager.getCandidateUuids()) {
                LiveStreamState state = announcementManager.buildCurrentState(uuid);
                if (state != null) {
                    if (state.currentlyLive()) liveCount++;
                    if (state.chatEnabled()) enabledCount++;
                    else disabledCount++;
                    if (state.isEligibleForAnnouncement(now, config.isAdminChatEnabled() && config.isChatEnabled())) {
                        eligibleCount++;
                    }
                }
            }
        }

        sender.sendMessage(Component.text("=== YouTubeLiveTag Announcement Status ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Authorized Players: ", NamedTextColor.GRAY).append(Component.text(authorized, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Linked Accounts: ", NamedTextColor.GRAY).append(Component.text(linked, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Currently Live: ", NamedTextColor.GRAY).append(Component.text(liveCount, NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("Chat Enabled: ", NamedTextColor.GRAY).append(Component.text(enabledCount, NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("Chat Disabled: ", NamedTextColor.GRAY).append(Component.text(disabledCount, NamedTextColor.DARK_GRAY)));

        sender.sendMessage(Component.text("Scheduler: ", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  Global: ", NamedTextColor.GRAY)
                .append(config.isAdminChatEnabled() && config.isChatEnabled() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)));
        sender.sendMessage(Component.text("  Eligible: ", NamedTextColor.GRAY).append(Component.text(eligibleCount, NamedTextColor.YELLOW)));

        if (announcementManager != null) {
            Duration eta = announcementManager.getEstimatedTimeToNextAnnouncement();
            String etaStr = eta.isZero() ? "Immediate" : "~" + formatDuration(eta);
            sender.sendMessage(Component.text("  Next Announcement: ", NamedTextColor.GRAY).append(Component.text(etaStr, NamedTextColor.AQUA)));
        }

        sender.sendMessage(Component.text("Announcements Sent (Session): ", NamedTextColor.GRAY).append(Component.text(stats.getSessionAnnouncements(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Announcements Sent (Today): ", NamedTextColor.GRAY).append(Component.text(stats.getTodayAnnouncements(), NamedTextColor.WHITE)));
    }

    private void handleChatAdminGlobalToggle(CommandSender sender, boolean enable) {
        plugin.getPluginConfig().setAdminChatEnabled(enable);
        if (enable) {
            sender.sendMessage(Component.text("Global YouTube live chat announcements are now ", NamedTextColor.GREEN)
                    .append(Component.text("ENABLED", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("Global YouTube live chat announcements are now ", NamedTextColor.RED)
                    .append(Component.text("DISABLED", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.RED)));
        }
    }

    private void handleChatAdminPlayerToggle(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /youtube chat admin " + (enable ? "enable" : "disable") + " <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[3];
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        StreamerConfigStore store = getEffectiveStore();
        if (store == null) return;

        StreamerChatSettings settings = store.getOrCreate(target.getUniqueId(), target.getName() != null ? target.getName() : targetName);
        settings.setAdminDisabled(!enable);
        if (enable) {
            settings.setEnabled(true);
        }
        store.saveOrUpdate(settings);

        if (enable) {
            sender.sendMessage(Component.text("Enabled live chat announcements for ", NamedTextColor.GREEN)
                    .append(Component.text(targetName, NamedTextColor.YELLOW)));
        } else {
            sender.sendMessage(Component.text("Disabled live chat announcements for ", NamedTextColor.YELLOW)
                    .append(Component.text(targetName, NamedTextColor.RED))
                    .append(Component.text(" (Admin override).", NamedTextColor.YELLOW)));
        }
    }

    private void handleChatAdminInterval(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /youtube chat admin interval <player> <minutes | random | default>", NamedTextColor.RED));
            return;
        }

        String targetName = args[3];
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        StreamerConfigStore store = getEffectiveStore();
        if (store == null) return;

        PluginConfig config = plugin.getPluginConfig();
        StreamerChatSettings settings = store.getOrCreate(target.getUniqueId(), target.getName() != null ? target.getName() : targetName);
        String val = args[4].toLowerCase();

        if (val.equals("random")) {
            settings.setIntervalMode(IntervalMode.RANDOM);
            store.saveOrUpdate(settings);
            sender.sendMessage(Component.text("Set announcement interval for " + targetName + " to RANDOM.", NamedTextColor.GREEN));
            return;
        }

        if (val.equals("default")) {
            settings.setIntervalMode(IntervalMode.DEFAULT);
            store.saveOrUpdate(settings);
            sender.sendMessage(Component.text("Reset announcement interval for " + targetName + " to DEFAULT.", NamedTextColor.GREEN));
            return;
        }

        try {
            int minutes = Integer.parseInt(val);
            if (minutes < config.getMinimumIntervalMinutes()) {
                sender.sendMessage(Component.text("Minimum announcement interval is " + config.getMinimumIntervalMinutes() + " minutes.", NamedTextColor.RED));
                return;
            }
            if (minutes > config.getMaximumIntervalMinutes()) {
                sender.sendMessage(Component.text("Maximum announcement interval is " + config.getMaximumIntervalMinutes() + " minutes.", NamedTextColor.RED));
                return;
            }

            settings.setIntervalMode(IntervalMode.CUSTOM);
            settings.setCustomIntervalMinutes(minutes);
            store.saveOrUpdate(settings);
            sender.sendMessage(Component.text("Set announcement interval for " + targetName + " to " + minutes + " minutes.", NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid interval: must be a number of minutes, 'random', or 'default'.", NamedTextColor.RED));
        }
    }

    private void handleChatAdminTitle(CommandSender sender, String[] args) {
        // Supports:
        // /youtube chat admin title clear <player>
        // /youtube chat admin title <player> clear
        // /youtube chat admin title <player> <text...>
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /youtube chat admin title <player> <text...> OR /youtube chat admin title clear <player>", NamedTextColor.RED));
            return;
        }

        String targetName;
        String titleArg;

        if (args[3].equalsIgnoreCase("clear")) {
            targetName = args[4];
            titleArg = "clear";
        } else if (args[4].equalsIgnoreCase("clear") && args.length == 5) {
            targetName = args[3];
            titleArg = "clear";
        } else {
            targetName = args[3];
            titleArg = String.join(" ", Arrays.copyOfRange(args, 4, args.length)).trim();
        }

        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        StreamerConfigStore store = getEffectiveStore();
        if (store == null) return;

        StreamerChatSettings settings = store.getOrCreate(target.getUniqueId(), target.getName() != null ? target.getName() : targetName);

        if (titleArg.equalsIgnoreCase("clear")) {
            settings.setCustomTitle(null);
            store.saveOrUpdate(settings);
            sender.sendMessage(Component.text("Cleared custom announcement title for " + targetName + ".", NamedTextColor.GREEN));
            return;
        }

        String sanitized = sanitizeTitle(titleArg);
        if (sanitized.length() > 100) {
            sender.sendMessage(Component.text("Custom title cannot exceed 100 characters.", NamedTextColor.RED));
            return;
        }

        settings.setCustomTitle(sanitized);
        store.saveOrUpdate(settings);
        sender.sendMessage(Component.text("Set custom title for " + targetName + ": ", NamedTextColor.GREEN)
                .append(Component.text("\"" + sanitized + "\"", NamedTextColor.YELLOW)));
    }

    // =========================================================================
    // EXISTING ADMINISTRATIVE COMMANDS
    // =========================================================================

    private void handleAllowLink(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /youtube allow-link <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        linkService.allowLink(target.getUniqueId(), target.getName() != null ? target.getName() : targetName);
        sender.sendMessage(Component.text("Authorized ", NamedTextColor.GREEN)
                .append(Component.text(targetName, NamedTextColor.YELLOW))
                .append(Component.text(" to link their YouTube account. They can now run ", NamedTextColor.GREEN))
                .append(Component.text("/link youtube", NamedTextColor.AQUA)));

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.sendMessage(Component.text("You have been authorized by an admin to link your YouTube account! Run ", NamedTextColor.GREEN)
                    .append(Component.text("/link youtube", NamedTextColor.YELLOW, TextDecoration.UNDERLINED))
                    .append(Component.text(" to connect.", NamedTextColor.GREEN)));
        }
    }

    private void handleRevokeLink(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /youtube revoke-link <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        boolean revoked = linkService.revokeLink(target.getUniqueId());
        if (revoked) {
            sender.sendMessage(Component.text("Revoked YouTube linking authorization for ", NamedTextColor.YELLOW)
                    .append(Component.text(targetName, NamedTextColor.RED)));
        } else {
            sender.sendMessage(Component.text(targetName + " was not authorized.", NamedTextColor.GRAY));
        }
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /youtube status <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Checking live status for " + targetName + "...", NamedTextColor.GRAY));

        youTubeService.checkStatus(target.getUniqueId()).thenAccept(status -> {
            Optional<LinkedPlayer> opt = linkService.get(target.getUniqueId());
            boolean allowed = (plugin != null && !plugin.getPluginConfig().isRequireAdminApproval()) || linkService.isAuthorizedToLink(target.getUniqueId());

            sender.sendMessage(Component.text("--- LiveStatus: " + targetName + " ---", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Authorized: ", NamedTextColor.GRAY)
                    .append(allowed ? Component.text("YES", NamedTextColor.GREEN) : Component.text("NO", NamedTextColor.RED)));

            if (opt.isPresent()) {
                LinkedPlayer lp = opt.get();
                sender.sendMessage(Component.text("Account Linked: ", NamedTextColor.GRAY)
                        .append(lp.isLinked() ? Component.text("YES", NamedTextColor.GREEN) : Component.text("NO", NamedTextColor.RED)));

                YouTubeAccount acc = lp.getAccount();
                if (acc != null) {
                    sender.sendMessage(Component.text("Channel: ", NamedTextColor.GRAY)
                            .append(Component.text(acc.channelTitle() != null ? acc.channelTitle() : acc.channelId(), NamedTextColor.AQUA)));
                }

                NamedTextColor statusColor = switch (status) {
                    case LIVE_MINECRAFT -> NamedTextColor.GREEN;
                    case LIVE_NOT_MINECRAFT -> NamedTextColor.YELLOW;
                    case OFFLINE -> NamedTextColor.GRAY;
                    default -> NamedTextColor.RED;
                };

                sender.sendMessage(Component.text("Live Status: ", NamedTextColor.GRAY)
                        .append(Component.text(status.getDisplayName(), statusColor)));

                Optional<YouTubeService.CachedBroadcastStatus> optCached = youTubeService.getCachedStatus(target.getUniqueId());
                if (optCached.isPresent() && optCached.get().broadcast() != null) {
                    YouTubeBroadcast bc = optCached.get().broadcast();
                    sender.sendMessage(Component.text("Stream Title: ", NamedTextColor.GRAY)
                            .append(Component.text(bc.title(), NamedTextColor.WHITE)));
                    sender.sendMessage(Component.text("Game Detection: ", NamedTextColor.GRAY)
                            .append(bc.isMinecraft() ? Component.text("Verified Minecraft", NamedTextColor.GREEN) : Component.text("Non-Minecraft", NamedTextColor.YELLOW)));
                }

                if (lp.getLastChecked() != null) {
                    sender.sendMessage(Component.text("Last Checked: ", NamedTextColor.GRAY)
                            .append(Component.text(lp.getLastChecked().toString(), NamedTextColor.DARK_GRAY)));
                }

                if (lp.getLastError() != null && !lp.getLastError().isBlank()) {
                    sender.sendMessage(Component.text("Last Error: ", NamedTextColor.DARK_RED)
                            .append(Component.text(lp.getLastError(), NamedTextColor.RED)));
                }
            } else {
                sender.sendMessage(Component.text("Account Linked: NO", NamedTextColor.GRAY));
            }
        });
    }

    private void handleUnlink(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /youtube unlink <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Unlinking " + targetName + "...", NamedTextColor.GRAY));
        boolean unlinked = plugin.getOAuthService().unlinkByUuid(target.getUniqueId());
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            plugin.getNametagProvider().removeLiveIndicator(onlineTarget);
        }

        if (unlinked) {
            sender.sendMessage(Component.text("Successfully unlinked YouTube account for " + targetName, NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Player " + targetName + " was not linked.", NamedTextColor.YELLOW));
        }
    }

    private void handleReload(CommandSender sender) {
        sender.sendMessage(Component.text("Reloading LiveStatus configuration and storage...", NamedTextColor.GRAY));
        try {
            plugin.reloadPluginConfig();
            sender.sendMessage(Component.text("LiveStatus configuration and storage reloaded successfully.", NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload configuration: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    // =========================================================================
    // HELPERS & TAB COMPLETION
    // =========================================================================

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("=== LiveStatus Administrative Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " allow-link <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Authorize player to link YouTube", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " revoke-link <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Revoke linking authorization", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " status <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - View YouTube live status & stream info", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " unlink <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Force-unlink player's YouTube account", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat", NamedTextColor.YELLOW)
                .append(Component.text(" - Configure live stream chat announcements", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload config.yml & storage", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " panel", NamedTextColor.YELLOW)
                .append(Component.text(" - Generate temporary Web Control Panel link", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " panel revoke", NamedTextColor.YELLOW)
                .append(Component.text(" - Revoke all active web panel sessions", NamedTextColor.GRAY)));
    }

    private void sendChatHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("=== YouTube Live Chat Announcement Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " chat status", NamedTextColor.YELLOW)
                .append(Component.text(" - View your announcement settings & live status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat on", NamedTextColor.YELLOW)
                .append(Component.text(" - Enable your live stream chat announcements", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat off", NamedTextColor.YELLOW)
                .append(Component.text(" - Disable your live stream chat announcements", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat title <text>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set custom announcement title", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat title clear", NamedTextColor.YELLOW)
                .append(Component.text(" - Clear custom title (use YouTube title)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat interval <minutes>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set custom re-announcement interval", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat interval random", NamedTextColor.YELLOW)
                .append(Component.text(" - Use randomized interval mode", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat interval default", NamedTextColor.YELLOW)
                .append(Component.text(" - Use server default interval", NamedTextColor.GRAY)));

        if (hasChatAdminPermission(sender)) {
            sender.sendMessage(Component.text("/" + label + " chat admin", NamedTextColor.AQUA)
                    .append(Component.text(" - Administrator announcement controls", NamedTextColor.GRAY)));
        }
    }

    private void sendChatAdminHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("=== YouTube Live Chat Admin Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " chat admin status", NamedTextColor.YELLOW)
                .append(Component.text(" - View announcement statistics & scheduler ETA", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat admin on", NamedTextColor.YELLOW)
                .append(Component.text(" - Globally enable chat announcements", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat admin off", NamedTextColor.YELLOW)
                .append(Component.text(" - Globally disable chat announcements", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat admin enable <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Enable announcements for a player", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat admin disable <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Disable announcements for a player", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat admin interval <player> <minutes|random|default>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set player interval", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat admin title <player> <text>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set player custom title", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat admin title clear <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Clear player custom title", NamedTextColor.GRAY)));
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission("livestatus.admin")
                || sender.hasPermission("youtubelivetag.admin")
                || sender.hasPermission("ytlive.admin")
                || sender.hasPermission("ytlive.panel");
    }

    private boolean hasChatPermission(CommandSender sender) {
        return sender.hasPermission("youtubelivetag.chat")
                || sender.hasPermission("livestatus.chat")
                || hasAdminPermission(sender);
    }

    private boolean hasChatAdminPermission(CommandSender sender) {
        return sender.hasPermission("youtubelivetag.chat.admin")
                || sender.hasPermission("livestatus.chat.admin")
                || hasAdminPermission(sender);
    }

    private StreamerConfigStore getEffectiveStore() {
        if (streamerConfigStore != null) return streamerConfigStore;
        if (plugin != null) return plugin.getStreamerConfigStore();
        return null;
    }

    private String sanitizeTitle(String text) {
        if (text == null) return "";
        return text.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim();
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "0s";
        }
        long minutes = duration.toMinutes();
        long seconds = duration.toSecondsPart();
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            return player;
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(offline.getName())) {
                return offline;
            }
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (hasChatPermission(sender)) {
                completions.add("chat");
            }
            if (hasAdminPermission(sender)) {
                completions.addAll(List.of("allow-link", "revoke-link", "status", "unlink", "reload", "panel", "help"));
            }
            return filterMatching(completions, args[0]);
        }

        if (args[0].equalsIgnoreCase("chat")) {
            if (args.length == 2) {
                List<String> chatSubs = new ArrayList<>(List.of("status", "on", "off", "title", "interval", "help"));
                if (hasChatAdminPermission(sender)) {
                    chatSubs.add("admin");
                }
                return filterMatching(chatSubs, args[1]);
            }

            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("interval")) {
                    return filterMatching(List.of("default", "random", "5", "10", "15", "30", "60"), args[2]);
                }
                if (args[1].equalsIgnoreCase("title")) {
                    return filterMatching(List.of("clear"), args[2]);
                }
                if (args[1].equalsIgnoreCase("admin") && hasChatAdminPermission(sender)) {
                    return filterMatching(List.of("status", "on", "off", "enable", "disable", "interval", "title"), args[2]);
                }
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("admin") && hasChatAdminPermission(sender)) {
                String sub = args[2].toLowerCase();
                if (List.of("enable", "disable", "interval", "title").contains(sub)) {
                    List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    return filterMatching(names, args[3]);
                }
            }

            if (args.length == 5 && args[1].equalsIgnoreCase("admin") && hasChatAdminPermission(sender)) {
                if (args[2].equalsIgnoreCase("interval")) {
                    return filterMatching(List.of("default", "random", "5", "10", "15", "30", "60"), args[4]);
                }
                if (args[2].equalsIgnoreCase("title")) {
                    return filterMatching(List.of("clear"), args[4]);
                }
            }

            return Collections.emptyList();
        }

        // Administrative commands
        if (!hasAdminPermission(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 2 && List.of("allow-link", "revoke-link", "status", "unlink").contains(args[0].toLowerCase())) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return filterMatching(names, args[1]);
        }

        if (args[0].equalsIgnoreCase("panel") && args.length == 2) {
            return filterMatching(List.of("revoke"), args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filterMatching(Collection<String> items, String prefix) {
        return items.stream()
                .filter(item -> item.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .toList();
    }
}
