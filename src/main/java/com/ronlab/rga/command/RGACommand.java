package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RGACommand implements CommandExecutor {

    private final RGA plugin;

    public RGACommand(RGA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rga.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.getConfigManager().getMessage("reloaded"));
            }

            case "tp" -> {
                // /rga tp <player> <world>
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rga tp <player> <world>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer '" + args[1] + "' not found.");
                    return true;
                }
                plugin.getWorldManager().teleportToWorld(target, args[2]);
            }

            case "conclude" -> {
                // /rga conclude <worldname> — reserved for Stage 3 minigame cleanup
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rga conclude <worldname>");
                    return true;
                }
                // Stage 3 will wire this up to MinigameManager
                sender.sendMessage("§eMinigame conclude coming in Stage 3.");
            }

            case "compass" -> {
                // /rga compass <player> — give compass to a player manually
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rga compass <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer '" + args[1] + "' not found.");
                    return true;
                }
                plugin.getHubListener().giveCompass(target);
                sender.sendMessage("§aGave navigator compass to " + target.getName() + ".");
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lRonlab Game Assistant §7Commands:");
        sender.sendMessage("§e/rga reload §7- Reload all configs");
        sender.sendMessage("§e/rga tp <player> <world> §7- Teleport player to world");
        sender.sendMessage("§e/rga compass <player> §7- Give navigator compass to player");
        sender.sendMessage("§e/rga conclude <world> §7- Conclude a minigame (Stage 3)");
    }
}
