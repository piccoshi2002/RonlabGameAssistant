package com.ronlab.rga.command;

import com.ronlab.rga.RGA;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HubCommand implements CommandExecutor {

    private final RGA plugin;

    public HubCommand(RGA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("rga.hub")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        String currentWorld = player.getWorld().getName();
        String hubWorld = plugin.getConfigManager().getHubWorld();

        // Don't teleport if already in Hub
        if (currentWorld.equalsIgnoreCase(hubWorld)) {
            player.sendMessage("§eYou are already in the Hub!");
            return true;
        }

        // If leaving an SMP world, save the player's current location first
        if (plugin.getConfigManager().getSmpWorlds().contains(currentWorld)) {
            plugin.getLocationTracker().saveLocation(player, player.getLocation());
        }

        // Teleport to Hub
        plugin.getWorldManager().teleportToWorld(player, hubWorld);

        return true;
    }
}
