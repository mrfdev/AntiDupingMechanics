package com.example.antiduping;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class AntiDupingCommand implements CommandExecutor, TabCompleter {

    private final AntiDupingMechanics plugin;
    private final AntiDupingListener listener;

    public AntiDupingCommand(AntiDupingMechanics plugin, AntiDupingListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("antiduping.admin")) {
            sender.sendMessage(color("&cNo permission."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(color("&7Usage: &f/antiduping <reload|status>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(color("&aAntiDupingMechanics reloaded."));
            }
            case "status" -> {
                sender.sendMessage(color("&6AntiDupingMechanics status:"));
                sender.sendMessage(color("&7- Debug: &f" + listener.statusDebug()));
                sender.sendMessage(color("&7- Worlds loaded: &f" + listener.statusWorldsLoaded()));
                sender.sendMessage(color("&7- Bypass perm: &fantiduping.bypass"));
                sender.sendMessage(color("&7Use /antiduping reload after config changes."));
            }
            default -> sender.sendMessage(color("&7Usage: &f/antiduping <reload|status>"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("antiduping.admin")) return List.of();
        if (args.length == 1) {
            List<String> opts = new ArrayList<>();
            opts.add("reload");
            opts.add("status");
            return opts.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
