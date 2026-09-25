package com.example.livestatus.command;

import com.example.livestatus.oauth.OAuthService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Handles the player command /link youtube [unlink].
 */
public class LinkYoutubeCommand implements CommandExecutor, TabCompleter {

    private final OAuthService oAuthService;

    public LinkYoutubeCommand(OAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by in-game players.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("livestatus.link") && !player.hasPermission("youtubelivetag.link")) {
            player.sendMessage(Component.text("You do not have permission to link accounts.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("youtube")) {
            player.sendMessage(Component.text("Usage: /link youtube [unlink]", NamedTextColor.YELLOW));
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("unlink")) {
            oAuthService.unlink(player);
            return true;
        }

        // Start linking flow
        oAuthService.startLinkFlow(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if ("youtube".startsWith(args[0].toLowerCase())) {
                return List.of("youtube");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("youtube")) {
            if ("unlink".startsWith(args[1].toLowerCase())) {
                return List.of("unlink");
            }
        }
        return Collections.emptyList();
    }
}

