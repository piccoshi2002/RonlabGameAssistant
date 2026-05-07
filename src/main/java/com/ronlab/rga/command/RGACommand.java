package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
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
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rga conclude <worldname>");
                    return true;
                }
                sender.sendMessage("§eMinigame conclude coming in Stage 3.");
            }

            case "compass" -> {
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

            case "createworld" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /rga createworld <name> <environment> <gamemode> <pvp>");
                    sender.sendMessage("§7Environments: NORMAL, NETHER, THE_END");
                    sender.sendMessage("§7Gamemodes: SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR");
                    sender.sendMessage("§7PVP: true or false");
                    return true;
                }

                String worldName = args[1];
                String envArg = args[2].toUpperCase();
                String gamemodeArg = args[3].toUpperCase();
                boolean pvp = Boolean.parseBoolean(args[4]);

                World.Environment environment;
                try {
                    environment = World.Environment.valueOf(envArg);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cInvalid environment: " + envArg);
                    sender.sendMessage("§7Valid options: NORMAL, NETHER, THE_END");
                    return true;
                }

                GameMode gamemode;
                try {
                    gamemode = GameMode.valueOf(gamemodeArg);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cInvalid gamemode: " + gamemodeArg);
                    sender.sendMessage("§7Valid options: SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR");
                    return true;
                }

                if (Bukkit.getWorld(worldName) != null) {
                    sender.sendMessage("§cWorld '" + worldName + "' is already loaded.");
                    return true;
                }

                sender.sendMessage("§eCreating world '" + worldName + "'... this may take a moment.");
                boolean success = plugin.getWorldManager().createWorld(worldName, environment, gamemode, pvp);
                if (success) {
                    sender.sendMessage("§aWorld '" + worldName + "' created and loaded successfully!");
                    sender.sendMessage("§7It has been saved to worlds.yml and will load on restart.");
                } else {
                    sender.sendMessage("§cFailed to create world '" + worldName + "'. Check console for details.");
                }
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
        sender.sendMessage("§e/rga createworld <name> <environment> <gamemode> <pvp> §7- Create a new world");
        sender.sendMessage("§e/rga conclude <world> §7- Conclude a minigame (Stage 3)");
    }
}
