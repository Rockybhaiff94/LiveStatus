package com.example.livestatus.command;

import com.example.livestatus.LiveStatusPlugin;
import com.example.livestatus.admin.AdminAuthService;
import com.example.livestatus.chat.IntervalMode;
import com.example.livestatus.chat.LiveAnnouncementManager;
import com.example.livestatus.chat.LiveStreamState;
import com.example.livestatus.chat.StreamerChatSettings;
import com.example.livestatus.config.PluginConfig;
import com.example.livestatus.mcast.MCastApiClient;
import com.example.livestatus.oauth.OAuthService;
import com.example.livestatus.player.AllowedPlayersStorage;
import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.player.PlayerLinkService;
import com.example.livestatus.stats.PluginStatistics;
import com.example.livestatus.storage.AccountRecord;
import com.example.livestatus.storage.AccountStorage;
import com.example.livestatus.storage.StreamerConfigStore;
import com.example.livestatus.youtube.YouTubeAccount;
import com.example.livestatus.youtube.YouTubeBroadcast;
import com.example.livestatus.youtube.YouTubeService;
import com.example.livestatus.youtube.YouTubeStatus;
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
 * Unified canonical command executor and tab completer for /mcast.
 * Also provides backwards-compatible routing for legacy /youtube, /livestatus, and /ytlive commands.
 */
public class MCastCommand implements CommandExecutor, TabCompleter {

    protected final LiveStatusPlugin plugin;
    protected final PlayerLinkService linkService;
    protected final YouTubeService youTubeService;
    protected final OAuthService oAuthService;
    protected final LiveAnnouncementManager announcementManager;
    protected final StreamerConfigStore streamerConfigStore;
    protected final AllowedPlayersStorage allowedStorage;
    protected final AccountStorage accountStorage;
    protected final AdminAuthService adminAuthService;

    public MCastCommand(
            LiveStatusPlugin plugin,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            OAuthService oAuthService,
            LiveAnnouncementManager announcementManager,
            StreamerConfigStore streamerConfigStore,
            AllowedPlayersStorage allowedStorage,
            AccountStorage accountStorage,
            AdminAuthService adminAuthService
    ) {
        this.plugin = plugin;
        this.linkService = linkService;
        this.youTubeService = youTubeService;
        this.oAuthService = oAuthService;
        this.announcementManager = announcementManager;
        this.streamerConfigStore = streamerConfigStore;
        this.allowedStorage = allowedStorage;
        this.accountStorage = accountStorage;
        this.adminAuthService = adminAuthService;
    }

    public MCastCommand(
            LiveStatusPlugin plugin,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            OAuthService oAuthService,
            LiveAnnouncementManager announcementManager,
            StreamerConfigStore streamerConfigStore,
            AllowedPlayersStorage allowedStorage,
            AccountStorage accountStorage
    ) {
        this(
                plugin,
                linkService,
                youTubeService,
                oAuthService,
                announcementManager,
                streamerConfigStore,
                allowedStorage,
                accountStorage,
                plugin != null ? plugin.getAdminAuthService() : null
        );
    }

    public MCastCommand(
            LiveStatusPlugin plugin,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            OAuthService oAuthService,
            LiveAnnouncementManager announcementManager,
            StreamerConfigStore streamerConfigStore
    ) {
        this(
                plugin,
                linkService,
                youTubeService,
                oAuthService,
                announcementManager,
                streamerConfigStore,
                plugin != null ? plugin.getAllowedStorage() : null,
                plugin != null ? plugin.getAccountStorage() : null
        );
    }

    public MCastCommand(
            LiveStatusPlugin plugin,
            PlayerLinkService linkService,
            YouTubeService youTubeService,
            LiveAnnouncementManager announcementManager,
            StreamerConfigStore streamerConfigStore
    ) {
        this(
                plugin,
                linkService,
                youTubeService,
                plugin != null ? plugin.getOAuthService() : null,
                announcementManager,
                streamerConfigStore
        );
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Direct /link compatibility handling
        if (label.equalsIgnoreCase("link")) {
            return handleLinkRoot(sender, args);
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // 1. CANONICAL PLAYER COMMANDS
        switch (sub) {
            case "link" -> {
                handleLink(sender);
                return true;
            }
            case "unlink" -> {
                // If admin passes a player argument (legacy /youtube unlink <player>)
                if (args.length >= 2 && hasAdminPermission(sender)) {
                    handleAdminRemove(sender, args[1]);
                } else {
                    handleUnlink(sender);
                }
                return true;
            }
            case "status" -> {
                // If player argument is given (/mcast status <player> or legacy /youtube status <player>)
                if (args.length >= 2) {
                    handleInfo(sender, args[1]);
                } else {
                    handlePlayerStatus(sender);
                }
                return true;
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " info <player>", NamedTextColor.RED));
                    return true;
                }
                handleInfo(sender, args[1]);
                return true;
            }
            case "chat" -> {
                handleChat(sender, label, args);
                return true;
            }
        }

        // 2. CANONICAL & LEGACY ADMINISTRATOR COMMANDS
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        switch (sub) {
            case "reload" -> handleReload(sender);
            case "admin" -> handleAdminSubcommand(sender, label, args);
            case "set" -> handleSetSubcommand(sender, label, args);
            case "authorize" -> handleAuthorizeSubcommand(sender, label, args);
            case "remove" -> handleRemoveSubcommand(sender, label, args);
            case "user" -> handleUserSubcommand(sender, label, args);
            case "youtuber" -> handleYoutuberSubcommand(sender, label, args);
            case "panel" -> handlePanel(sender, args);

            // Backwards-compatible legacy syntax aliases
            case "allow-link" -> handleAuthorizeUser(sender, args.length >= 2 ? args[1] : null);
            case "revoke-link" -> handleRemoveUser(sender, args.length >= 2 ? args[1] : null);

            default -> sendHelp(sender, label);
        }

        return true;
    }

    // =========================================================================
    // PLAYER HANDLERS
    // =========================================================================

    private boolean handleLinkRoot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by in-game players.", NamedTextColor.RED));
            return true;
        }

        if (!hasLinkPermission(player)) {
            player.sendMessage(Component.text("You do not have permission to link accounts.", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 1 && (args[0].equalsIgnoreCase("unlink") || (args.length >= 2 && args[1].equalsIgnoreCase("unlink")))) {
            handleUnlink(player);
            return true;
        }

        handleLink(player);
        return true;
    }

    private void handleLink(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by in-game players.", NamedTextColor.RED));
            return;
        }

        if (!hasLinkPermission(player)) {
            player.sendMessage(Component.text("You do not have permission to link accounts.", NamedTextColor.RED));
            return;
        }

        OAuthService oauth = getEffectiveOAuthService();
        if (oauth == null) {
            player.sendMessage(Component.text("MCast linking service is currently unavailable.", NamedTextColor.RED));
            return;
        }

        oauth.startLinkFlow(player);
    }

    private void handleUnlink(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by in-game players.", NamedTextColor.RED));
            return;
        }

        if (!hasLinkPermission(player)) {
            player.sendMessage(Component.text("You do not have permission to unlink accounts.", NamedTextColor.RED));
            return;
        }

        OAuthService oauth = getEffectiveOAuthService();
        if (oauth == null) {
            player.sendMessage(Component.text("MCast linking service is currently unavailable.", NamedTextColor.RED));
            return;
        }

        oauth.unlink(player);
    }

    private void handlePlayerStatus(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Console cannot have a stream status. Use /mcast info <player> to view a player's status.", NamedTextColor.RED));
            return;
        }

        if (!hasLinkPermission(player)) {
            player.sendMessage(Component.text("You do not have permission to check status.", NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();
        Optional<LinkedPlayer> opt = linkService != null ? linkService.get(uuid) : Optional.empty();

        player.sendMessage(Component.text("=== Your MCast Status ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        boolean allowed = (plugin == null || !plugin.getPluginConfig().isRequireAdminApproval())
                || (linkService != null && linkService.isAuthorizedToLink(uuid));
        player.sendMessage(Component.text("Authorized: ", NamedTextColor.GRAY)
                .append(allowed ? Component.text("YES", NamedTextColor.GREEN) : Component.text("NO (Contact an Admin)", NamedTextColor.RED)));

        if (opt.isPresent() && opt.get().isLinked()) {
            LinkedPlayer lp = opt.get();
            YouTubeAccount acc = lp.getAccount();
            String chanName = acc != null ? (acc.channelTitle() != null ? acc.channelTitle() : acc.channelId()) : "Unknown";
            player.sendMessage(Component.text("Linked Channel: ", NamedTextColor.GRAY)
                    .append(Component.text(chanName, NamedTextColor.AQUA)));

            YouTubeStatus status = lp.getStatus() != null ? lp.getStatus() : YouTubeStatus.OFFLINE;
            NamedTextColor statusColor = switch (status) {
                case LIVE_MINECRAFT -> NamedTextColor.GREEN;
                case LIVE_NOT_MINECRAFT -> NamedTextColor.YELLOW;
                case OFFLINE -> NamedTextColor.GRAY;
                default -> NamedTextColor.RED;
            };
            player.sendMessage(Component.text("Live Status: ", NamedTextColor.GRAY)
                    .append(Component.text(status.getDisplayName(), statusColor, TextDecoration.BOLD)));

            if (youTubeService != null) {
                youTubeService.getCachedStatus(uuid).ifPresent(cs -> {
                    if (cs.broadcast() != null) {
                        YouTubeBroadcast bc = cs.broadcast();
                        player.sendMessage(Component.text("Stream Title: ", NamedTextColor.GRAY)
                                .append(Component.text(bc.title(), NamedTextColor.WHITE)));
                    }
                });
            }
        } else {
            player.sendMessage(Component.text("Account Linked: ", NamedTextColor.GRAY)
                    .append(Component.text("NO", NamedTextColor.RED))
                    .append(Component.text(" (Run /mcast link to connect)", NamedTextColor.YELLOW)));
        }
    }

    private void handleInfo(CommandSender sender, String targetName) {
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Checking MCast information for " + targetName + "...", NamedTextColor.GRAY));

        java.util.function.Consumer<YouTubeStatus> renderer = (status) -> {
            Optional<LinkedPlayer> opt = linkService != null ? linkService.get(target.getUniqueId()) : Optional.empty();
            boolean allowed = (plugin != null && !plugin.getPluginConfig().isRequireAdminApproval())
                    || (linkService != null && linkService.isAuthorizedToLink(target.getUniqueId()));

            sender.sendMessage(Component.text("--- MCast Info: " + targetName + " ---", NamedTextColor.GOLD));

            if (hasAdminPermission(sender)) {
                sender.sendMessage(Component.text("Authorized: ", NamedTextColor.GRAY)
                        .append(allowed ? Component.text("YES", NamedTextColor.GREEN) : Component.text("NO", NamedTextColor.RED)));
            }

            if (opt.isPresent() && opt.get().isLinked()) {
                LinkedPlayer lp = opt.get();
                sender.sendMessage(Component.text("Account Linked: ", NamedTextColor.GRAY)
                        .append(Component.text("YES", NamedTextColor.GREEN)));

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
                        .append(Component.text(status != null ? status.getDisplayName() : "Unknown", statusColor)));

                if (youTubeService != null) {
                    Optional<YouTubeService.CachedBroadcastStatus> optCached = youTubeService.getCachedStatus(target.getUniqueId());
                    if (optCached.isPresent() && optCached.get().broadcast() != null) {
                        YouTubeBroadcast bc = optCached.get().broadcast();
                        sender.sendMessage(Component.text("Stream Title: ", NamedTextColor.GRAY)
                                .append(Component.text(bc.title(), NamedTextColor.WHITE)));
                    }
                }

                if (hasAdminPermission(sender)) {
                    if (lp.getLastChecked() != null) {
                        sender.sendMessage(Component.text("Last Checked: ", NamedTextColor.GRAY)
                                .append(Component.text(lp.getLastChecked().toString(), NamedTextColor.DARK_GRAY)));
                    }
                    if (lp.getLastError() != null && !lp.getLastError().isBlank()) {
                        sender.sendMessage(Component.text("Last Error: ", NamedTextColor.DARK_RED)
                                .append(Component.text(lp.getLastError(), NamedTextColor.RED)));
                    }
                }
            } else {
                sender.sendMessage(Component.text("Account Linked: NO", NamedTextColor.GRAY));
            }
        };

        if (plugin != null && plugin.getMcastApiClient() != null) {
            plugin.getMcastApiClient().fetchBatchStatus(Collections.singleton(target.getUniqueId()))
                    .thenAccept(results -> {
                        MCastApiClient.PlayerLiveStatus mStatus = results.get(target.getUniqueId());
                        if (mStatus != null) {
                            if (mStatus.linked() && mStatus.channelId() != null && !mStatus.channelId().isBlank()) {
                                YouTubeAccount ytAccount = new YouTubeAccount(mStatus.channelId(), mStatus.channelTitle(), null);
                                if (linkService != null) linkService.updateAccount(target.getUniqueId(), true, ytAccount);
                            }
                            YouTubeStatus effectiveStatus = mStatus.status();
                            if (linkService != null) {
                                linkService.updateStatus(target.getUniqueId(), effectiveStatus, mStatus.error());
                            }
                            renderer.accept(effectiveStatus);
                            if (plugin.getLiveStatusScheduler() != null) {
                                plugin.getLiveStatusScheduler().syncVisualIndicators();
                            }
                        } else {
                            YouTubeStatus cur = linkService != null ? linkService.get(target.getUniqueId()).map(LinkedPlayer::getStatus).orElse(YouTubeStatus.UNKNOWN) : YouTubeStatus.UNKNOWN;
                            renderer.accept(cur);
                        }
                    })
                    .exceptionally(ex -> {
                        YouTubeStatus cur = linkService != null ? linkService.get(target.getUniqueId()).map(LinkedPlayer::getStatus).orElse(YouTubeStatus.UNKNOWN) : YouTubeStatus.UNKNOWN;
                        renderer.accept(cur);
                        return null;
                    });
        } else if (youTubeService != null) {
            youTubeService.checkStatus(target.getUniqueId()).thenAccept(status -> {
                renderer.accept(status);
                if (plugin != null && plugin.getLiveStatusScheduler() != null) {
                    plugin.getLiveStatusScheduler().syncVisualIndicators();
                }
            });
        } else {
            YouTubeStatus cur = linkService != null ? linkService.get(target.getUniqueId()).map(LinkedPlayer::getStatus).orElse(YouTubeStatus.UNKNOWN) : YouTubeStatus.UNKNOWN;
            renderer.accept(cur);
        }
    }

    // =========================================================================
    // ADMINISTRATOR SUBCOMMAND DISPATCHERS
    // =========================================================================

    private void handleAdminSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendAdminHelp(sender, label);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " admin set <player> <youtube-url>", NamedTextColor.RED));
                    return;
                }
                handleAdminSet(sender, args[2], args[3]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /" + label + " admin remove <player>", NamedTextColor.RED));
                    return;
                }
                handleAdminRemove(sender, args[2]);
            }
            case "grant" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /" + label + " admin grant <player>", NamedTextColor.RED));
                    return;
                }
                handleAdminGrant(sender, args[2]);
            }
            case "revoke" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /" + label + " admin revoke <player>", NamedTextColor.RED));
                    return;
                }
                handleAdminRevoke(sender, args[2]);
            }
            default -> sendAdminHelp(sender, label);
        }
    }

    private void handleSetSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("announcement")) {
            sender.sendMessage(Component.text("Usage: /" + label + " set announcement <message>", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " set announcement <message>", NamedTextColor.RED));
            return;
        }

        String rawMessage = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        handleSetAnnouncement(sender, rawMessage);
    }

    private void handleAuthorizeSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("user")) {
            sender.sendMessage(Component.text("Usage: /" + label + " authorize user <player>", NamedTextColor.RED));
            return;
        }
        handleAuthorizeUser(sender, args[2]);
    }

    private void handleRemoveSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("user")) {
            sender.sendMessage(Component.text("Usage: /" + label + " remove user <player>", NamedTextColor.RED));
            return;
        }
        handleRemoveUser(sender, args[2]);
    }

    private void handleUserSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            handleUserList(sender);
        } else {
            sender.sendMessage(Component.text("Usage: /" + label + " user list", NamedTextColor.RED));
        }
    }

    private void handleYoutuberSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            handleYoutuberList(sender);
        } else {
            sender.sendMessage(Component.text("Usage: /" + label + " youtuber list", NamedTextColor.RED));
        }
    }

    // =========================================================================
    // ADMINISTRATOR BUSINESS LOGIC
    // =========================================================================

    private void handleReload(CommandSender sender) {
        sender.sendMessage(Component.text("Reloading MCast configuration and persistent storage...", NamedTextColor.GRAY));
        try {
            if (plugin != null) {
                plugin.reloadPluginConfig();
            }
            sender.sendMessage(Component.text("MCast configuration and storage reloaded successfully.", NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload configuration: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleAdminSet(CommandSender sender, String targetName, String rawUrl) {
        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        YouTubeAccount account = YouTubeUrlParser.parse(rawUrl);
        if (account == null) {
            sender.sendMessage(Component.text("Invalid YouTube channel URL or ID: '" + rawUrl + "'.", NamedTextColor.RED));
            sender.sendMessage(Component.text("Supported formats: https://youtube.com/channel/UC..., https://youtube.com/@handle, or @handle", NamedTextColor.GRAY));
            return;
        }

        UUID uuid = target.getUniqueId();
        String safeName = target.getName() != null ? target.getName() : targetName;

        // Auto-authorize player so they can stream
        if (linkService != null) {
            linkService.allowLink(uuid, safeName);
            linkService.updateAccount(uuid, true, account);
        }

        AccountStorage accStore = getEffectiveAccountStorage();
        if (accStore != null) {
            AccountRecord record = new AccountRecord(
                    uuid,
                    safeName,
                    account.channelId(),
                    account.channelTitle(),
                    account.customUrl(),
                    "",
                    "",
                    0L,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    "ACTIVE"
            );
            accStore.saveOrUpdate(record);
        }

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            if (plugin != null && plugin.getLiveStatusScheduler() != null) {
                plugin.getLiveStatusScheduler().checkPlayerImmediate(onlineTarget);
                plugin.getLiveStatusScheduler().syncVisualIndicators();
            }
            onlineTarget.sendMessage(Component.text("An administrator assigned YouTube channel '", NamedTextColor.GREEN)
                    .append(Component.text(account.channelTitle(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("' to your account.", NamedTextColor.GREEN)));
        }

        sender.sendMessage(Component.text("Successfully assigned YouTube channel '", NamedTextColor.GREEN)
                .append(Component.text(account.channelTitle(), NamedTextColor.YELLOW))
                .append(Component.text("' (" + account.channelId() + ") to ", NamedTextColor.GREEN))
                .append(Component.text(safeName, NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN)));
    }

    private void handleAdminRemove(CommandSender sender, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            sender.sendMessage(Component.text("Usage: /mcast admin remove <player>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Removing assigned channel for " + targetName + "...", NamedTextColor.GRAY));

        boolean unlinked = false;
        OAuthService oauth = getEffectiveOAuthService();
        if (oauth != null) {
            unlinked = oauth.unlinkByUuid(target.getUniqueId());
        } else if (linkService != null) {
            linkService.unlink(target.getUniqueId());
            unlinked = true;
        }

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline() && plugin != null && plugin.getNametagProvider() != null) {
            plugin.getNametagProvider().removeLiveIndicator(onlineTarget);
        }
        if (plugin != null && plugin.getLiveStatusScheduler() != null) {
            plugin.getLiveStatusScheduler().syncVisualIndicators();
        }

        if (unlinked) {
            sender.sendMessage(Component.text("Successfully removed assigned YouTube channel link for ", NamedTextColor.GREEN)
                    .append(Component.text(targetName, NamedTextColor.YELLOW)));
        } else {
            sender.sendMessage(Component.text("Player " + targetName + " had no linked channel.", NamedTextColor.YELLOW));
        }
    }

    private void handleAdminGrant(CommandSender sender, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            sender.sendMessage(Component.text("Usage: /mcast admin grant <player>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(targetName);
        UUID uuid = target != null ? target.getUniqueId() : null;
        String safeName = (target != null && target.getName() != null) ? target.getName() : targetName.trim();

        String grantedBy = sender instanceof Player p ? p.getName() : "CONSOLE";

        AdminAuthService auth = getEffectiveAdminAuthService();
        if (auth == null) {
            sender.sendMessage(Component.text("Administrator authorization service is unavailable.", NamedTextColor.RED));
            return;
        }

        if (auth.isGrantedAdmin(uuid, safeName)) {
            sender.sendMessage(Component.text(safeName + " is already an authorized MCast administrator.", NamedTextColor.YELLOW));
            return;
        }

        boolean granted = auth.grantAdmin(uuid, safeName, grantedBy);
        if (granted) {
            sender.sendMessage(Component.text("Granted MCast administrator privileges to ", NamedTextColor.GREEN)
                    .append(Component.text(safeName, NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GREEN)));

            if (target != null && target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null && !onlineTarget.equals(sender)) {
                    onlineTarget.sendMessage(Component.text("You have been granted MCast administrator privileges by " + grantedBy + ".", NamedTextColor.GOLD));
                }
            }
        } else {
            sender.sendMessage(Component.text("Failed to save administrator grant for " + safeName + ".", NamedTextColor.RED));
        }
    }

    private void handleAdminRevoke(CommandSender sender, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            sender.sendMessage(Component.text("Usage: /mcast admin revoke <player>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(targetName);
        UUID uuid = target != null ? target.getUniqueId() : null;
        String safeName = (target != null && target.getName() != null) ? target.getName() : targetName.trim();

        AdminAuthService auth = getEffectiveAdminAuthService();
        if (auth == null) {
            sender.sendMessage(Component.text("Administrator authorization service is unavailable.", NamedTextColor.RED));
            return;
        }

        boolean isConfigAdmin = auth.isConfigAdmin(uuid, safeName);
        boolean isGranted = auth.isGrantedAdmin(uuid, safeName);

        if (!isGranted && !isConfigAdmin) {
            if (auth.getPluginConfig() != null && auth.getPluginConfig().isOpDefaultAdmin()) {
                boolean isOp = target != null && target.isOp();
                if (isOp) {
                    sender.sendMessage(Component.text(safeName + " does not have an explicit MCast administrator grant (they currently derive admin access from Minecraft OP status).", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("To disable OP-based admin access, set 'admin.op-default-admin: false' in config.yml.", NamedTextColor.GRAY));
                    return;
                }
            }
            sender.sendMessage(Component.text(safeName + " is not an authorized MCast administrator.", NamedTextColor.RED));
            return;
        }

        boolean revoked = auth.revokeAdmin(uuid, safeName);
        if (revoked) {
            sender.sendMessage(Component.text("Revoked MCast administrator privileges for ", NamedTextColor.GREEN)
                    .append(Component.text(safeName, NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GREEN)));

            if (isConfigAdmin) {
                sender.sendMessage(Component.text("Notice: " + safeName + " is also listed in config.yml under admin.admins and will retain access until removed from config.yml.", NamedTextColor.YELLOW));
            }

            if (sender instanceof Player p && p.getUniqueId().equals(uuid)) {
                sender.sendMessage(Component.text("You have revoked your own MCast administrator privileges.", NamedTextColor.RED));
            } else if (target != null && target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(Component.text("Your MCast administrator privileges have been revoked.", NamedTextColor.RED));
                }
            }
        } else if (isConfigAdmin) {
            sender.sendMessage(Component.text(safeName + " is configured as an MCast administrator in config.yml under admin.admins. Remove them from config.yml to revoke access.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Failed to revoke administrator privileges for " + safeName + ".", NamedTextColor.RED));
        }
    }

    private void handleSetAnnouncement(CommandSender sender, String rawMessage) {
        if (rawMessage.isBlank()) {
            sender.sendMessage(Component.text("Announcement message template cannot be empty.", NamedTextColor.RED));
            return;
        }

        if (plugin != null) {
            plugin.getPluginConfig().setAnnouncementMessageLines(List.of(rawMessage));
            plugin.getConfig().set("chat.message", List.of(rawMessage));
            plugin.saveConfig();
        }

        sender.sendMessage(Component.text("Announcement message template updated to:", NamedTextColor.GREEN));
        sender.sendMessage(Component.text(rawMessage, NamedTextColor.YELLOW));
    }

    private void handleAuthorizeUser(CommandSender sender, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            sender.sendMessage(Component.text("Usage: /mcast authorize user <player>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        String safeName = target.getName() != null ? target.getName() : targetName;
        if (linkService != null) {
            linkService.allowLink(target.getUniqueId(), safeName);
        }

        sender.sendMessage(Component.text("Authorized ", NamedTextColor.GREEN)
                .append(Component.text(safeName, NamedTextColor.YELLOW))
                .append(Component.text(" to link their YouTube account. They can now run ", NamedTextColor.GREEN))
                .append(Component.text("/mcast link", NamedTextColor.AQUA)));

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.sendMessage(Component.text("You have been authorized by an admin to link your YouTube account! Run ", NamedTextColor.GREEN)
                    .append(Component.text("/mcast link", NamedTextColor.YELLOW, TextDecoration.UNDERLINED))
                    .append(Component.text(" to connect.", NamedTextColor.GREEN)));
        }
    }

    private void handleRemoveUser(CommandSender sender, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            sender.sendMessage(Component.text("Usage: /mcast remove user <player>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = resolvePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return;
        }

        boolean revoked = linkService != null && linkService.revokeLink(target.getUniqueId());
        if (revoked) {
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null && onlineTarget.isOnline() && plugin != null && plugin.getNametagProvider() != null) {
                plugin.getNametagProvider().removeLiveIndicator(onlineTarget);
            }
            if (plugin != null && plugin.getLiveStatusScheduler() != null) {
                plugin.getLiveStatusScheduler().syncVisualIndicators();
            }
            sender.sendMessage(Component.text("Revoked YouTube linking authorization for ", NamedTextColor.YELLOW)
                    .append(Component.text(targetName, NamedTextColor.RED)));
        } else {
            sender.sendMessage(Component.text(targetName + " was not authorized.", NamedTextColor.GRAY));
        }
    }

    private void handleUserList(CommandSender sender) {
        AllowedPlayersStorage storage = getEffectiveAllowedStorage();
        Map<UUID, String> users = storage != null ? storage.getAllowedPlayersMap() : Collections.emptyMap();

        if (users.isEmpty()) {
            sender.sendMessage(Component.text("No players are currently authorized to link YouTube accounts.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("=== Authorized MCast Users (" + users.size() + ") ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        List<String> names = new ArrayList<>(users.values());
        Collections.sort(names);
        sender.sendMessage(Component.text(String.join(", ", names), NamedTextColor.YELLOW));
    }

    private void handleYoutuberList(CommandSender sender) {
        AccountStorage storage = getEffectiveAccountStorage();
        Collection<AccountRecord> accounts = storage != null ? storage.getAllAccounts() : Collections.emptyList();

        if (accounts.isEmpty()) {
            sender.sendMessage(Component.text("No players currently have linked YouTube channels.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("=== Linked MCast YouTubers (" + accounts.size() + ") ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (AccountRecord acc : accounts) {
            String pName = acc.playerName() != null ? acc.playerName() : acc.minecraftUuid().toString();
            String cTitle = acc.channelTitle() != null ? acc.channelTitle() : acc.youtubeChannelId();
            String cId = acc.youtubeChannelId() != null ? acc.youtubeChannelId() : "-";

            YouTubeStatus status = linkService != null
                    ? linkService.get(acc.minecraftUuid()).map(LinkedPlayer::getStatus).orElse(YouTubeStatus.OFFLINE)
                    : YouTubeStatus.OFFLINE;

            NamedTextColor color = (status == YouTubeStatus.LIVE_MINECRAFT) ? NamedTextColor.GREEN : NamedTextColor.GRAY;

            sender.sendMessage(Component.text("● ", color)
                    .append(Component.text(pName, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" -> ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(cTitle, NamedTextColor.AQUA))
                    .append(Component.text(" (" + cId + ") ", NamedTextColor.GRAY))
                    .append(Component.text("[" + (status != null ? status.getDisplayName() : "OFFLINE") + "]", color)));
        }
    }

    // =========================================================================
    // STREAMER CHAT SETTINGS & WEB PANEL
    // =========================================================================

    private void handleChat(CommandSender sender, String label, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("admin")) {
            handleChatAdmin(sender, label, args);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Streamer chat announcement settings can only be configured by in-game players.", NamedTextColor.RED));
            return;
        }

        if (!hasChatPermission(player)) {
            player.sendMessage(Component.text("You do not have permission to configure live chat announcements.", NamedTextColor.RED));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (plugin != null && plugin.getAllowedStorage() != null && !plugin.getAllowedStorage().isAllowed(uuid)) {
            player.sendMessage(Component.text("You are not an authorized streamer on this server.", NamedTextColor.RED));
            return;
        }

        if (plugin != null && plugin.getAccountStorage() != null && plugin.getAccountStorage().findByUuid(uuid).isEmpty()) {
            player.sendMessage(Component.text("You must link your YouTube account first using ", NamedTextColor.RED)
                    .append(Component.text("/mcast link", NamedTextColor.YELLOW, TextDecoration.UNDERLINED)));
            return;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("help")) {
            sendChatHelp(player, label);
            return;
        }

        String chatSub = args[1].toLowerCase(Locale.ROOT);
        switch (chatSub) {
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

        int defInt = plugin != null ? plugin.getPluginConfig().getDefaultIntervalMinutes() : 15;
        int randMin = plugin != null ? plugin.getPluginConfig().getRandomMinimumMinutes() : 5;
        int randMax = plugin != null ? plugin.getPluginConfig().getRandomMaximumMinutes() : 60;
        String modeInfo = switch (settings.getIntervalMode()) {
            case CUSTOM -> "CUSTOM (" + settings.getCustomIntervalMinutes() + " minutes)";
            case RANDOM -> "RANDOM (" + randMin + "m - " + randMax + "m)";
            case DEFAULT -> "DEFAULT (" + defInt + " minutes)";
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
            }
        }
    }

    private void handleStreamerChatToggle(Player player, boolean enable) {
        StreamerConfigStore store = getEffectiveStore();
        if (store == null) return;

        PluginConfig config = plugin != null ? plugin.getPluginConfig() : null;
        if (!enable && config != null && !config.isStreamerCanDisable()) {
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
            player.sendMessage(Component.text("Usage: /mcast chat title <clear | text...>", NamedTextColor.RED));
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
            player.sendMessage(Component.text("Usage: /mcast chat interval <minutes | random | default>", NamedTextColor.RED));
            return;
        }

        PluginConfig config = plugin != null ? plugin.getPluginConfig() : null;
        StreamerChatSettings settings = store.getOrCreate(player.getUniqueId(), player.getName());
        String val = args[2].toLowerCase(Locale.ROOT);

        if (val.equals("random")) {
            if (config != null && !config.isRandomIntervalEnabled()) {
                player.sendMessage(Component.text("RANDOM interval mode is disabled on this server.", NamedTextColor.RED));
                return;
            }
            settings.setIntervalMode(IntervalMode.RANDOM);
            store.saveOrUpdate(settings);
            player.sendMessage(Component.text("Announcement interval set to ", NamedTextColor.GREEN)
                    .append(Component.text("RANDOM", NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.GREEN)));
            return;
        }

        if (val.equals("default")) {
            settings.setIntervalMode(IntervalMode.DEFAULT);
            store.saveOrUpdate(settings);
            int def = config != null ? config.getDefaultIntervalMinutes() : 15;
            player.sendMessage(Component.text("Announcement interval reset to ", NamedTextColor.GREEN)
                    .append(Component.text("DEFAULT (" + def + "m)", NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.GREEN)));
            return;
        }

        try {
            int minutes = Integer.parseInt(val);
            int minAllowed = config != null ? config.getMinimumIntervalMinutes() : 5;
            int maxAllowed = config != null ? config.getMaximumIntervalMinutes() : 1440;
            if (minutes < minAllowed) {
                player.sendMessage(Component.text("Minimum announcement interval is " + minAllowed + " minutes.", NamedTextColor.RED));
                return;
            }
            if (minutes > maxAllowed) {
                player.sendMessage(Component.text("Maximum announcement interval is " + maxAllowed + " minutes.", NamedTextColor.RED));
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

    private void handleChatAdmin(CommandSender sender, String label, String[] args) {
        if (!hasChatAdminPermission(sender)) {
            sender.sendMessage(Component.text("You do not have permission to manage chat announcement administration.", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sendChatAdminHelp(sender, label);
            return;
        }

        String sub = args[2].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> handleChatAdminStatus(sender);
            case "on" -> handleChatAdminGlobalToggle(sender, true);
            case "off" -> handleChatAdminGlobalToggle(sender, false);
            default -> sendChatAdminHelp(sender, label);
        }
    }

    private void handleChatAdminStatus(CommandSender sender) {
        PluginStatistics stats = announcementManager != null ? announcementManager.getStatistics() : new PluginStatistics();
        int authorized = plugin != null ? plugin.getAllowedStorage().getAllowedUuids().size() : 0;
        int linked = plugin != null ? plugin.getAccountStorage().getAllAccounts().size() : 0;

        sender.sendMessage(Component.text("=== MCast Live Chat Status ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Authorized Players: ", NamedTextColor.GRAY).append(Component.text(authorized, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Linked Accounts: ", NamedTextColor.GRAY).append(Component.text(linked, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Announcements Sent (Session): ", NamedTextColor.GRAY).append(Component.text(stats.getSessionAnnouncements(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Announcements Sent (Today): ", NamedTextColor.GRAY).append(Component.text(stats.getTodayAnnouncements(), NamedTextColor.WHITE)));
    }

    private void handleChatAdminGlobalToggle(CommandSender sender, boolean enable) {
        if (plugin != null) {
            plugin.getPluginConfig().setAdminChatEnabled(enable);
        }
        sender.sendMessage(Component.text("Global YouTube live chat announcements are now ", enable ? NamedTextColor.GREEN : NamedTextColor.RED)
                .append(Component.text(enable ? "ENABLED" : "DISABLED", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(".", enable ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private void handlePanel(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("revoke")) {
            if (plugin != null && plugin.getWebSecurityManager() != null) {
                plugin.getWebSecurityManager().revokeAll();
                sender.sendMessage(Component.text("[MCast] All active web panel sessions have been revoked.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("[MCast] Web panel is not active.", NamedTextColor.RED));
            }
            return;
        }

        if (plugin == null || plugin.getWebSecurityManager() == null || !plugin.getPluginConfig().isWebEnabled()) {
            sender.sendMessage(Component.text("[MCast] Web panel is disabled in configuration.", NamedTextColor.RED));
            return;
        }

        PluginConfig cfg = plugin.getPluginConfig();
        int expiryHours = cfg.getWebLinkExpirationHours();
        Duration duration = Duration.ofHours(expiryHours);
        var session = plugin.getWebSecurityManager().createSession(sender.getName(), duration);

        String host = cfg.getWebHost();
        if ("0.0.0.0".equals(host)) {
            host = "localhost";
        }
        String proto = cfg.isWebSslEnabled() ? "https" : "http";
        String url = proto + "://" + host + ":" + cfg.getWebPort() + "/panel/" + session.token();

        sender.sendMessage(Component.text("----------------------------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("[MCast] Web Control Panel:", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Access Code: ", NamedTextColor.GRAY)
                .append(Component.text(session.accessCode(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        sender.sendMessage(Component.text("Expires: ", NamedTextColor.GRAY)
                .append(Component.text(expiryHours + " hours from now", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("----------------------------------------", NamedTextColor.DARK_GRAY));
    }

    // =========================================================================
    // HELP & PERMISSION CHECKS
    // =========================================================================

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("=== MCast Commands ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/" + label + " link", NamedTextColor.YELLOW)
                .append(Component.text(" - Connect your YouTube account", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " unlink", NamedTextColor.YELLOW)
                .append(Component.text(" - Remove your connected channel", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " status", NamedTextColor.YELLOW)
                .append(Component.text(" - Check your stream or linking status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " info <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - View another player's stream info", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat", NamedTextColor.YELLOW)
                .append(Component.text(" - Configure live chat announcements", NamedTextColor.GRAY)));

        if (hasAdminPermission(sender)) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("--- Administrator Commands ---", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.YELLOW)
                    .append(Component.text(" - Safely reload plugin config & storage", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " admin set <player> <url>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Administratively assign a YouTube channel", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " admin remove <player>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Remove an assigned channel link", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " admin grant <player>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Grant MCast administrator access", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " admin revoke <player>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Revoke MCast administrator access", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " set announcement <message>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Configure chat announcement template", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " authorize user <player>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Authorize player to link YouTube", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " remove user <player>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Revoke player linking authorization", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " user list", NamedTextColor.YELLOW)
                    .append(Component.text(" - List authorized players", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " youtuber list", NamedTextColor.YELLOW)
                    .append(Component.text(" - List all linked YouTubers", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/" + label + " panel", NamedTextColor.YELLOW)
                    .append(Component.text(" - Generate temporary Web Control Panel link", NamedTextColor.GRAY)));
        }
    }

    private void sendAdminHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("=== MCast Administrator Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " admin set <player> <url>", NamedTextColor.YELLOW)
                .append(Component.text(" - Assign a YouTube channel", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " admin remove <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Remove assigned channel link", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " admin grant <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Grant MCast administrator access", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " admin revoke <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Revoke MCast administrator access", NamedTextColor.GRAY)));
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
                .append(Component.text(" - Clear custom title", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat interval <minutes>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set custom re-announcement interval", NamedTextColor.GRAY)));
    }

    private void sendChatAdminHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("=== YouTube Live Chat Admin Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " chat admin status", NamedTextColor.YELLOW)
                .append(Component.text(" - View announcement statistics & scheduler ETA", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat admin on", NamedTextColor.YELLOW)
                .append(Component.text(" - Globally enable chat announcements", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " chat admin off", NamedTextColor.YELLOW)
                .append(Component.text(" - Globally disable chat announcements", NamedTextColor.GRAY)));
    }

    public boolean hasAdminPermission(CommandSender sender) {
        AdminAuthService auth = getEffectiveAdminAuthService();
        if (auth != null) {
            return auth.isAdministrator(sender);
        }
        if (!(sender instanceof Player)) return true;
        return sender.hasPermission("mcast.admin")
                || sender.hasPermission("livestatus.admin")
                || sender.hasPermission("youtubelivetag.admin")
                || sender.hasPermission("ytlive.admin")
                || sender.hasPermission("ytlive.panel");
    }

    protected AdminAuthService getEffectiveAdminAuthService() {
        if (adminAuthService != null) return adminAuthService;
        if (plugin != null) return plugin.getAdminAuthService();
        return null;
    }

    public boolean hasLinkPermission(CommandSender sender) {
        return sender.hasPermission("mcast.link")
                || sender.hasPermission("livestatus.link")
                || sender.hasPermission("youtubelivetag.link")
                || hasAdminPermission(sender);
    }

    public boolean hasChatPermission(CommandSender sender) {
        return sender.hasPermission("youtubelivetag.chat")
                || sender.hasPermission("livestatus.chat")
                || hasAdminPermission(sender);
    }

    public boolean hasChatAdminPermission(CommandSender sender) {
        return sender.hasPermission("youtubelivetag.chat.admin")
                || sender.hasPermission("livestatus.chat.admin")
                || hasAdminPermission(sender);
    }

    protected OAuthService getEffectiveOAuthService() {
        if (oAuthService != null) return oAuthService;
        if (plugin != null) return plugin.getOAuthService();
        return null;
    }

    protected AllowedPlayersStorage getEffectiveAllowedStorage() {
        if (allowedStorage != null) return allowedStorage;
        if (plugin != null) return plugin.getAllowedStorage();
        return null;
    }

    protected AccountStorage getEffectiveAccountStorage() {
        if (accountStorage != null) return accountStorage;
        if (plugin != null) return plugin.getAccountStorage();
        return null;
    }

    protected StreamerConfigStore getEffectiveStore() {
        if (streamerConfigStore != null) return streamerConfigStore;
        if (plugin != null) return plugin.getStreamerConfigStore();
        return null;
    }

    protected String sanitizeTitle(String text) {
        if (text == null) return "";
        return text.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim();
    }

    protected OfflinePlayer resolvePlayer(String name) {
        if (name == null || name.isBlank()) return null;
        if (Bukkit.getServer() == null) {
            return createOfflinePlayerFallback(name);
        }
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

    private OfflinePlayer createOfflinePlayerFallback(String name) {
        UUID foundUuid = null;
        if (linkService != null) {
            for (LinkedPlayer lp : linkService.getAllTrackedPlayers()) {
                if (name.equalsIgnoreCase(lp.getPlayerName())) {
                    foundUuid = lp.getUuid();
                    break;
                }
            }
        }
        if (foundUuid == null && plugin != null && plugin.getAllowedStorage() != null) {
            for (Map.Entry<UUID, String> entry : plugin.getAllowedStorage().getAllowedPlayersMap().entrySet()) {
                if (name.equalsIgnoreCase(entry.getValue())) {
                    foundUuid = entry.getKey();
                    break;
                }
            }
        }
        final UUID finalUuid = foundUuid != null ? foundUuid : UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return (OfflinePlayer) java.lang.reflect.Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if (mName.equals("getName")) return name;
                    if (mName.equals("getUniqueId")) return finalUuid;
                    if (mName.equals("isOnline")) return false;
                    if (mName.equals("getPlayer")) return null;
                    if (mName.equals("toString")) return "OfflinePlayer[" + name + "]";
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(long.class)) return 0L;
                    if (method.getReturnType().equals(int.class)) return 0;
                    return null;
                }
        );
    }

    // =========================================================================
    // TAB COMPLETION
    // =========================================================================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (alias.equalsIgnoreCase("link")) {
            if (args.length == 1) {
                return filterMatching(List.of("youtube", "unlink"), args[0]);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("youtube")) {
                return filterMatching(List.of("unlink"), args[1]);
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            // Base player subcommands
            if (hasLinkPermission(sender)) {
                options.addAll(List.of("link", "unlink", "status", "info"));
            }
            if (hasChatPermission(sender)) {
                options.add("chat");
            }
            options.add("help");

            // Administrator subcommands
            if (hasAdminPermission(sender)) {
                options.addAll(List.of("reload", "admin", "set", "authorize", "remove", "user", "youtuber", "panel"));
            }
            return filterMatching(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Player /mcast info <player>
        if (sub.equals("info") && args.length == 2) {
            return filterMatching(getOnlinePlayerNames(), args[1]);
        }

        // Chat subcommands
        if (sub.equals("chat")) {
            return handleChatTab(sender, args);
        }

        // Administrator subcommands
        if (!hasAdminPermission(sender)) {
            return Collections.emptyList();
        }

        switch (sub) {
            case "admin" -> {
                if (args.length == 2) {
                    return filterMatching(List.of("set", "remove", "grant", "revoke"), args[1]);
                }
                if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("grant")) {
                        return filterMatching(getOnlinePlayerNames(), args[2]);
                    }
                    if (args[1].equalsIgnoreCase("remove")) {
                        return filterMatching(getTrackedOrOnlinePlayerNames(), args[2]);
                    }
                    if (args[1].equalsIgnoreCase("revoke")) {
                        return filterMatching(getAdminPlayerNames(), args[2]);
                    }
                }
                if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                    return filterMatching(List.of("https://youtube.com/@", "https://youtube.com/channel/UC"), args[3]);
                }
            }
            case "set" -> {
                if (args.length == 2) {
                    return filterMatching(List.of("announcement"), args[1]);
                }
            }
            case "authorize" -> {
                if (args.length == 2) {
                    return filterMatching(List.of("user"), args[1]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("user")) {
                    return filterMatching(getOnlinePlayerNames(), args[2]);
                }
            }
            case "remove" -> {
                if (args.length == 2) {
                    return filterMatching(List.of("user"), args[1]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("user")) {
                    return filterMatching(getAllowedPlayerNames(), args[2]);
                }
            }
            case "user" -> {
                if (args.length == 2) {
                    return filterMatching(List.of("list"), args[1]);
                }
            }
            case "youtuber" -> {
                if (args.length == 2) {
                    return filterMatching(List.of("list"), args[1]);
                }
            }
            case "panel" -> {
                if (args.length == 2) {
                    return filterMatching(List.of("revoke"), args[1]);
                }
            }
            // Compatibility aliases
            case "allow-link" -> {
                if (args.length == 2) return filterMatching(getOnlinePlayerNames(), args[1]);
            }
            case "revoke-link" -> {
                if (args.length == 2) return filterMatching(getAllowedPlayerNames(), args[1]);
            }
            case "unlink", "status" -> {
                if (args.length == 2) return filterMatching(getOnlinePlayerNames(), args[1]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> handleChatTab(CommandSender sender, String[] args) {
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
                return filterMatching(List.of("status", "on", "off"), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> getOnlinePlayerNames() {
        if (Bukkit.getServer() == null) {
            return Collections.emptyList();
        }
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private List<String> getAllowedPlayerNames() {
        AllowedPlayersStorage storage = getEffectiveAllowedStorage();
        if (storage != null) {
            return new ArrayList<>(storage.getAllowedPlayersMap().values());
        }
        return getOnlinePlayerNames();
    }

    private List<String> getTrackedOrOnlinePlayerNames() {
        Set<String> set = new LinkedHashSet<>(getOnlinePlayerNames());
        AccountStorage storage = getEffectiveAccountStorage();
        if (storage != null) {
            for (AccountRecord rec : storage.getAllAccounts()) {
                if (rec.playerName() != null) set.add(rec.playerName());
            }
        }
        return new ArrayList<>(set);
    }

    private List<String> getAdminPlayerNames() {
        AdminAuthService auth = getEffectiveAdminAuthService();
        if (auth != null) {
            Set<String> names = new LinkedHashSet<>(auth.getAllAdminNames());
            names.addAll(getOnlinePlayerNames());
            return new ArrayList<>(names);
        }
        return getOnlinePlayerNames();
    }

    private List<String> filterMatching(Collection<String> items, String prefix) {
        return items.stream()
                .filter(item -> item.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .sorted()
                .toList();
    }
}
