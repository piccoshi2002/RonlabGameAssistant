package com.ronlab.rga.player;

import com.ronlab.rga.RGA;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LocationTracker implements Listener {

    private final RGA plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // In-memory cache: player UUID -> last SMP location
    private final Map<UUID, Location> lastSmpLocations = new HashMap<>();

    public LocationTracker(RGA plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "player-data.yml");
        loadFromDisk();

        // Register this as a listener for auto-saving
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Event Handlers ───────────────────────────────────────────

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        String fromWorld = event.getFrom().getName();
        if (plugin.getConfigManager().getSmpWorlds().contains(fromWorld)) {
            // Player just left an SMP world — save their location
            saveLocation(event.getPlayer(), event.getPlayer().getLocation());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String currentWorld = player.getWorld().getName();
        if (plugin.getConfigManager().getSmpWorlds().contains(currentWorld)) {
            saveLocation(player, player.getLocation());
        }
    }

    // ── Public API ───────────────────────────────────────────────

    public void saveLocation(Player player, Location location) {
        lastSmpLocations.put(player.getUniqueId(), location.clone());
    }

    public boolean hasLocation(Player player) {
        if (lastSmpLocations.containsKey(player.getUniqueId())) return true;
        // Check disk
        return dataConfig.contains(player.getUniqueId().toString());
    }

    public Location getLocation(Player player) {
        // Check memory first
        if (lastSmpLocations.containsKey(player.getUniqueId())) {
            return lastSmpLocations.get(player.getUniqueId());
        }
        // Fall back to disk
        return loadLocationFromDisk(player.getUniqueId());
    }

    public void teleportToLastLocation(Player player) {
        if (!hasLocation(player)) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-smp-location"));
            return;
        }

        Location loc = getLocation(player);
        if (loc == null || loc.getWorld() == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-smp-location"));
            return;
        }

        // Make sure the world is loaded
        World world = Bukkit.getWorld(loc.getWorld().getName());
        if (world == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("world-not-found", loc.getWorld().getName()));
            return;
        }

        player.sendMessage(plugin.getConfigManager().getMessage("teleporting"));
        player.teleport(loc);

        // Apply SMP gamemode (SURVIVAL)
        plugin.getWorldManager().getSettings(world.getName());
        var settings = plugin.getWorldManager().getSettings(world.getName());
        if (settings != null) {
            player.setGameMode(settings.getGamemode());
        }
    }

    // ── Persistence ──────────────────────────────────────────────

    public void saveAll() {
        for (Map.Entry<UUID, Location> entry : lastSmpLocations.entrySet()) {
            writeLocationToDisk(entry.getKey(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player-data.yml: " + e.getMessage());
        }
    }

    private void loadFromDisk() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player-data.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void writeLocationToDisk(UUID uuid, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String path = uuid.toString();
        dataConfig.set(path + ".world", loc.getWorld().getName());
        dataConfig.set(path + ".x", loc.getX());
        dataConfig.set(path + ".y", loc.getY());
        dataConfig.set(path + ".z", loc.getZ());
        dataConfig.set(path + ".yaw", loc.getYaw());
        dataConfig.set(path + ".pitch", loc.getPitch());
    }

    private Location loadLocationFromDisk(UUID uuid) {
        String path = uuid.toString();
        if (!dataConfig.contains(path)) return null;

        String worldName = dataConfig.getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = dataConfig.getDouble(path + ".x");
        double y = dataConfig.getDouble(path + ".y");
        double z = dataConfig.getDouble(path + ".z");
        float yaw = (float) dataConfig.getDouble(path + ".yaw");
        float pitch = (float) dataConfig.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }
}
