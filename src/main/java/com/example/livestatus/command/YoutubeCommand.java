package com.example.livestatus.command;

import com.example.livestatus.LiveStatusPlugin;
import com.example.livestatus.player.LinkedPlayer;
import com.example.livestatus.player.PlayerLinkService;
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

import java.util.*;

/**
 * Handles all administrator commands under /youtube (/yt).
 */
public class YoutubeCommand implements CommandExecutor, TabCompleter {

    private final LiveStatusPlugin plugin;
    private final PlayerLinkService linkService;
    private final YouTubeService youTubeService;

    public YoutubeCommand(LiveStatusPlugin plugin, PlayerLinkService linkService, YouTubeService youTubeService) {
        this.plugin = plugin;
        this.linkService = linkService;
        this.youTubeService = youTubeService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("livestatus.admin") && !sender.hasPermission("youtubelivetag.admin")) {
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
            default -> sendHelp(sender, label);
        }

        return true;
    }

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
            boolean allowed = linkService.isAuthorizedToLink(target.getUniqueId());

            sender.sendMessage(Component.text("--- LiveStatus: " + targetName + " ---", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Admin Authorized: ", NamedTextColor.GRAY)
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

                // Check cached broadcast details for stream title
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
        sender.sendMessage(Component.text("Reloading LiveStatus configuration...", NamedTextColor.GRAY));
        try {
            plugin.reloadPluginConfig();
            sender.sendMessage(Component.text("LiveStatus configuration and storage reloaded successfully.", NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload configuration: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("=== LiveStatus Admin Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " allow-link <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Authorize player to link YouTube", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " revoke-link <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Revoke linking authorization", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " status <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - View YouTube live status & stream info", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " unlink <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Force-unlink player's YouTube account", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload config.yml & storage", NamedTextColor.GRAY)));
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
        if (!sender.hasPermission("livestatus.admin") && !sender.hasPermission("youtubelivetag.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = List.of("allow-link", "revoke-link", "status", "unlink", "reload", "help");
            return filterMatching(subs, args[0]);
        }

        if (args.length == 2 && List.of("allow-link", "revoke-link", "status", "unlink").contains(args[0].toLowerCase())) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return filterMatching(names, args[1]);
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

