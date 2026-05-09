package com.ronlab.rga.world;

import com.ronlab.rga.RGA;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class WorldEnforcementListener implements Listener {

    private final RGA plugin;

    public WorldEnforcementListener(RGA plugin) {
        this.plugin = plugin;
    }

    // Enforce gamemode when a player enters any world
    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        enforceGamemode(player, worldName);
    }

    // Enforce gamemode on respawn
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Delay by 1 tick so the respawn location is fully applied first
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            String worldName = player.getWorld().getName();
            enforceGamemode(player, worldName);
        }, 1L);
    }

    // Block non-admins from teleporting into template worlds
    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        String toWorld = event.getTo().getWorld().getName();

        WorldSettings settings = plugin.getWorldManager().getSettings(toWorld);
        if (settings == null || !settings.isTemplate()) return;

        if (!player.hasPermission("rga.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot enter a template world.");
        } else {
            player.sendMessage("§e[RGA] Warning: This is a template world. Changes will affect all future copies.");
        }
    }

    private void enforceGamemode(Player player, String worldName) {
        // Never enforce on admins in template worlds — they may need any gamemode
        WorldSettings settings = plugin.getWorldManager().getSettings(worldName);
        if (settings == null) return;

        GameMode current = player.getGameMode();
        GameMode required = settings.getGamemode();

        if (current != required) {
            player.setGameMode(required);
        }
    }
}
