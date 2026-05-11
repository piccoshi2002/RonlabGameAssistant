package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import com.ronlab.rga.party.Party;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class MinigameWorldListener implements Listener {

    private final RGA plugin;

    public MinigameWorldListener(RGA plugin) {
        this.plugin = plugin;
    }

    // ── Portal routing ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        String currentWorld = player.getWorld().getName();

        // Only handle players in minigame worlds
        if (!isMinigameWorld(currentWorld)) return;

        String baseName = getBaseName(currentWorld);
        if (baseName == null) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (currentWorld.equals(baseName)) {
                // Overworld → Nether
                World nether = Bukkit.getWorld(baseName + "_the_nether");
                if (nether != null) {
                    event.setCancelled(true);
                    // Calculate nether coordinates (divide by 8)
                    Location from = player.getLocation();
                    Location dest = new Location(nether,
                            from.getX() / 8, from.getY(), from.getZ() / 8,
                            from.getYaw(), from.getPitch());
                    player.teleport(dest);
                }
            } else if (currentWorld.equals(baseName + "_the_nether")) {
                // Nether → Overworld
                World overworld = Bukkit.getWorld(baseName);
                if (overworld != null) {
                    event.setCancelled(true);
                    // Calculate overworld coordinates (multiply by 8)
                    Location from = player.getLocation();
                    Location dest = new Location(overworld,
                            from.getX() * 8, from.getY(), from.getZ() * 8,
                            from.getYaw(), from.getPitch());
                    player.teleport(dest);
                }
            }
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (currentWorld.equals(baseName)) {
                // Overworld → End
                World end = Bukkit.getWorld(baseName + "_the_end");
                if (end != null) {
                    event.setCancelled(true);
                    player.teleport(end.getSpawnLocation());
                }
            } else if (currentWorld.equals(baseName + "_the_end")) {
                // End → Overworld (End exit portal / dragon death)
                World overworld = Bukkit.getWorld(baseName);
                if (overworld != null) {
                    event.setCancelled(true);
                    player.teleport(overworld.getSpawnLocation());
                }
            }
        }
    }

    // ── Respawn routing ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String currentWorld = player.getWorld().getName();

        if (!isMinigameWorld(currentWorld)) return;

        String baseName = getBaseName(currentWorld);
        if (baseName == null) return;

        // Always respawn at the minigame overworld spawn
        World overworld = Bukkit.getWorld(baseName);
        if (overworld != null) {
            event.setRespawnLocation(overworld.getSpawnLocation());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Returns true if the world name belongs to an active minigame.
     */
    private boolean isMinigameWorld(String worldName) {
        if (!worldName.startsWith("minigame_")) return false;
        // Check active parties
        for (Party party : plugin.getPartyManager().getActiveParties().values()) {
            String active = party.getActiveWorldName();
            if (active == null) continue;
            if (worldName.equals(active)
                    || worldName.equals(active + "_the_nether")
                    || worldName.equals(active + "_the_end")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given any minigame world name (overworld, nether, or end),
     * returns the base overworld name.
     */
    private String getBaseName(String worldName) {
        for (Party party : plugin.getPartyManager().getActiveParties().values()) {
            String active = party.getActiveWorldName();
            if (active == null) continue;
            if (worldName.equals(active)
                    || worldName.equals(active + "_the_nether")
                    || worldName.equals(active + "_the_end")) {
                return active;
            }
        }
        return null;
    }
}
