package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class RGACommand implements CommandExecutor, TabCompleter {

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

            case "help" -> sendHelp(sender);

            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.getConfigManager().getMessage("reloaded"));
            }

            case "listworlds" -> {
                sender.sendMessage("§6§lLoaded Worlds:");
                for (World world : Bukkit.getWorlds()) {
                    var settings = plugin.getWorldManager().getSettings(world.getName());
                    String gamemode = settings != null ? settings.getGamemode().name() : "UNKNOWN";
                    String pvp = settings != null ? (settings.isPvp() ? "§cPVP" : "§aNoPVP") : "§7?";
                    sender.sendMessage("§e" + world.getName() + " §7| " + gamemode + " | " + pvp);
                }
            }

            case "tp" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rga tp <player> <world>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
                plugin.getWorldManager().teleportToWorld(target, args[2]);
            }

            case "compass" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rga compass <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
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
                World.Environment environment;
                GameMode gamemode;
                boolean pvp = Boolean.parseBoolean(args[4]);

                try {
                    environment = World.Environment.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cInvalid environment. Valid: NORMAL, NETHER, THE_END");
                    return true;
                }
                try {
                    gamemode = GameMode.valueOf(args[3].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cInvalid gamemode. Valid: SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR");
                    return true;
                }

                if (Bukkit.getWorld(worldName) != null) {
                    sender.sendMessage("§cWorld '" + worldName + "' is already loaded.");
                    return true;
                }

                sender.sendMessage("§eCreating world '" + worldName + "'...");
                boolean success = plugin.getWorldManager().createWorld(worldName, environment, gamemode, pvp);
                if (success) {
                    sender.sendMessage("§aWorld '" + worldName + "' created successfully!");
                } else {
                    sender.sendMessage("§cFailed to create world '" + worldName + "'.");
                }
            }

            case "loadworld" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rga loadworld <name>");
                    return true;
                }
                String worldName = args[1];
                if (Bukkit.getWorld(worldName) != null) {
                    sender.sendMessage("§cWorld '" + worldName + "' is already loaded.");
                    return true;
                }
                sender.sendMessage("§eLoading world '" + worldName + "'...");
                boolean success = plugin.getWorldManager().loadExistingWorld(worldName);
                if (success) {
                    sender.sendMessage("§aWorld '" + worldName + "' loaded successfully!");
                } else {
                    sender.sendMessage("§cFailed to load world '" + worldName + "'. Does the folder exist?");
                }
            }

            case "unloadworld" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rga unloadworld <name>");
                    return true;
                }
                String worldName = args[1];
                String hubWorld = plugin.getConfigManager().getHubWorld();
                if (worldName.equalsIgnoreCase(hubWorld) || worldName.equalsIgnoreCase("world")) {
                    sender.sendMessage("§cYou cannot unload the Hub or default world.");
                    return true;
                }
                boolean success = plugin.getWorldManager().unloadWorld(worldName, sender);
                if (success) {
                    sender.sendMessage("§aWorld '" + worldName + "' unloaded.");
                } else {
                    sender.sendMessage("§cFailed to unload world '" + worldName + "'.");
                }
            }

            case "deleteworld" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rga deleteworld <name>");
                    return true;
                }
                String worldName = args[1];
                String hubWorld = plugin.getConfigManager().getHubWorld();
                if (worldName.equalsIgnoreCase(hubWorld) || worldName.equalsIgnoreCase("world")) {
                    sender.sendMessage("§cYou cannot delete the Hub or default world.");
                    return true;
                }
                sender.sendMessage("§eDeleting world '" + worldName + "'...");
                boolean success = plugin.getWorldManager().deleteWorld(worldName, sender);
                if (success) {
                    sender.sendMessage("§aWorld '" + worldName + "' deleted successfully.");
                } else {
                    sender.sendMessage("§cFailed to delete world '" + worldName + "'.");
                }
            }

            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can use this command.");
                    return true;
                }
                String worldName = args.length >= 2 ? args[1] : player.getWorld().getName();
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    sender.sendMessage("§cWorld '" + worldName + "' not found.");
                    return true;
                }
                world.setSpawnLocation(player.getLocation());
                sender.sendMessage("§aSpawn point for '" + worldName + "' set to your current location.");
            }

            case "setworldgamemode" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rga setworldgamemode <world> <gamemode>");
                    return true;
                }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                GameMode gm;
                try {
                    gm = GameMode.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cInvalid gamemode. Valid: SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR");
                    return true;
                }
                plugin.getWorldManager().setWorldGamemode(args[1], gm);
                // Update all players currently in that world
                for (Player p : world.getPlayers()) p.setGameMode(gm);
                sender.sendMessage("§aGamemode for '" + args[1] + "' set to " + gm.name() + ".");
            }

            case "setworldpvp" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rga setworldpvp <world> <true/false>");
                    return true;
                }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                boolean pvp = Boolean.parseBoolean(args[2]);
                world.setPVP(pvp);
                plugin.getWorldManager().setWorldPvp(args[1], pvp);
                sender.sendMessage("§aPVP for '" + args[1] + "' set to " + pvp + ".");
            }

            case "setworlddifficulty" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rga setworlddifficulty <world> <difficulty>");
                    sender.sendMessage("§7Difficulties: PEACEFUL, EASY, NORMAL, HARD");
                    return true;
                }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                Difficulty difficulty;
                try {
                    difficulty = Difficulty.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cInvalid difficulty. Valid: PEACEFUL, EASY, NORMAL, HARD");
                    return true;
                }
                world.setDifficulty(difficulty);
                plugin.getWorldManager().setWorldDifficulty(args[1], difficulty);
                sender.sendMessage("§aDifficulty for '" + args[1] + "' set to " + difficulty.name() + ".");
            }

            case "gamerule" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /rga gamerule <world> <rule> <value>");
                    return true;
                }
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { sender.sendMessage("§cWorld not found."); return true; }
                GameRule<?> rule = GameRule.getByName(args[2]);
                if (rule == null) {
                    sender.sendMessage("§cUnknown gamerule: " + args[2]);
                    return true;
                }
                boolean applied = applyGameRule(world, rule, args[3]);
                if (applied) {
                    sender.sendMessage("§aGamerule " + args[2] + " set to " + args[3] + " in " + args[1] + ".");
                } else {
                    sender.sendMessage("§cInvalid value '" + args[3] + "' for gamerule " + args[2] + ".");
                }
            }

            case "conclude" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /rga conclude <worldname>");
                    return true;
                }
                sender.sendMessage("§eMinigame conclude coming in Stage 3.");
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    // ── Gamerule helper ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> boolean applyGameRule(World world, GameRule<T> rule, String value) {
        if (rule.getType() == Boolean.class) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) return false;
            world.setGameRule((GameRule<Boolean>) rule, Boolean.parseBoolean(value));
            return true;
        } else if (rule.getType() == Integer.class) {
            try {
                world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(value));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    // ── Tab Completion ───────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rga.admin")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of(
                "help", "reload", "listworlds", "tp", "compass",
                "createworld", "loadworld", "unloadworld", "deleteworld",
                "setspawn", "setworldgamemode", "setworldpvp", "setworlddifficulty",
                "gamerule", "conclude"
            ));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "tp", "unloadworld", "deleteworld", "setspawn",
                     "setworldgamemode", "setworldpvp", "setworlddifficulty", "gamerule" ->
                    Bukkit.getWorlds().forEach(w -> completions.add(w.getName()));
                case "compass" ->
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                case "loadworld" -> {
                    // Suggest world folders that aren't currently loaded
                    File serverDir = Bukkit.getWorldContainer();
                    File[] dirs = serverDir.listFiles(File::isDirectory);
                    if (dirs != null) {
                        Set<String> loaded = Bukkit.getWorlds().stream()
                                .map(World::getName).collect(Collectors.toSet());
                        for (File dir : dirs) {
                            if (!loaded.contains(dir.getName())) {
                                completions.add(dir.getName());
                            }
                        }
                    }
                }
                case "createworld" -> completions.add("<worldname>");
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "createworld" -> completions.addAll(List.of("NORMAL", "NETHER", "THE_END"));
                case "setworldgamemode" -> completions.addAll(List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"));
                case "setworldpvp" -> completions.addAll(List.of("true", "false"));
                case "setworlddifficulty" -> completions.addAll(List.of("PEACEFUL", "EASY", "NORMAL", "HARD"));
                case "gamerule" -> {
                    for (GameRule<?> rule : GameRule.values()) {
                        completions.add(rule.getName());
                    }
                }
                case "tp" -> Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 4) {
            switch (args[0].toLowerCase()) {
                case "createworld" -> completions.addAll(List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"));
                case "gamerule" -> {
                    GameRule<?> rule = GameRule.getByName(args[2]);
                    if (rule != null) {
                        if (rule.getType() == Boolean.class) {
                            completions.addAll(List.of("true", "false"));
                        } else {
                            completions.add("<value>");
                        }
                    }
                }
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("createworld")) {
            completions.addAll(List.of("true", "false"));
        }

        // Filter by what the player has typed so far
        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
    }

    // ── Help ─────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l========= Ronlab Game Assistant =========");
        sender.sendMessage("§e/rga help §7- Show this help menu");
        sender.sendMessage("§e/rga reload §7- Reload all configs");
        sender.sendMessage("§e/rga listworlds §7- List all loaded worlds");
        sender.sendMessage("§e/rga tp <player> <world> §7- Teleport player to world");
        sender.sendMessage("§e/rga compass <player> §7- Give navigator compass");
        sender.sendMessage("§e/rga createworld <name> <env> <gamemode> <pvp> §7- Create world");
        sender.sendMessage("§e/rga loadworld <name> §7- Load existing world folder");
        sender.sendMessage("§e/rga unloadworld <name> §7- Unload world without deleting");
        sender.sendMessage("§e/rga deleteworld <name> §7- Delete world permanently");
        sender.sendMessage("§e/rga setspawn [world] §7- Set world spawn to your location");
        sender.sendMessage("§e/rga setworldgamemode <world> <gamemode> §7- Set world gamemode");
        sender.sendMessage("§e/rga setworldpvp <world> <true/false> §7- Toggle world PVP");
        sender.sendMessage("§e/rga setworlddifficulty <world> <difficulty> §7- Set difficulty");
        sender.sendMessage("§e/rga gamerule <world> <rule> <value> §7- Set a gamerule");
        sender.sendMessage("§e/hub §7- Return to the Hub world");
        sender.sendMessage("§6§l=========================================");
    }
}
