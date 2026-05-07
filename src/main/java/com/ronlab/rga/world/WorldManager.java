package com.ronlab.rga.world;

import com.ronlab.rga.RGA;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class WorldManager {

    private final RGA plugin;
    private final Map<String, WorldSettings> worldSettings = new HashMap<>();

    public WorldManager(RGA plugin) {
        this.plugin = plugin;
    }

    public void loadConfiguredWorlds() {
        worldSettings.clear();
        ConfigurationSection worlds = plugin.getConfigManager().getWorldsConfig().getConfigurationSection("worlds");
        if (worlds == null) {
            plugin.getLogger().warning("No worlds section found in worlds.yml!");
            return;
        }

        for (String worldName : worlds.getKeys(false)) {
            ConfigurationSection section = worlds.getConfigurationSection(worldName);
            if (section == null) continue;

            boolean loadOnStartup = section.getBoolean("load-on-startup", true);
            String envString = section.getString("environment", "NORMAL");
            String gamemodeString = section.getString("gamemode", "SURVIVAL");
            boolean pvp = section.getBoolean("pvp", true);

            World.Environment environment;
            try {
                environment = World.Environment.valueOf(envString.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid environment '" + envString + "' for world " + worldName + ". Defaulting to NORMAL.");
                environment = World.Environment.NORMAL;
            }

            GameMode gamemode;
            try {
                gamemode = GameMode.valueOf(gamemodeString.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid gamemode '" + gamemodeString + "' for world " + worldName + ". Defaulting to SURVIVAL.");
                gamemode = GameMode.SURVIVAL;
            }

            WorldSettings settings = new WorldSettings(gamemode, pvp, environment);
            worldSettings.put(worldName, settings);

            if (loadOnStartup) {
                loadWorld(worldName, environment, settings);
            }
        }
    }

    private void loadWorld(String worldName, World.Environment environment, WorldSettings settings) {
        // If already loaded, just apply settings
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            applySettings(existing, settings);
            return;
        }

        // Check if world folder exists
        java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), worldName);
        if (!worldFolder.exists()) {
            plugin.getLogger().warning("World folder for '" + worldName + "' does not exist. Skipping.");
            return;
        }

        // Load the world
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(environment);
        World world = Bukkit.createWorld(creator);

        if (world == null) {
            plugin.getLogger().warning("Failed to load world: " + worldName);
            return;
        }

        applySettings(world, settings);
        plugin.getLogger().info("Loaded world: " + worldName);
    }

    private void applySettings(World world, WorldSettings settings) {
        world.setPVP(settings.isPvp());
        // Set per-world gamemode via world rules where supported
    }

    /**
     * Teleports a player to the spawn of a named world.
     * Applies that world's configured gamemode on arrival.
     */
    public boolean teleportToWorld(Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("world-not-found", worldName));
            return false;
        }

        player.sendMessage(plugin.getConfigManager().getMessage("teleporting"));
        player.teleport(world.getSpawnLocation());

        // Apply configured gamemode
        WorldSettings settings = worldSettings.get(worldName);
        if (settings != null) {
            player.setGameMode(settings.getGamemode());
        }

        return true;
    }

    /**
     * Creates and loads a brand new world, then saves it to worlds.yml.
     * Returns true on success, false on failure.
     */
    public boolean createWorld(String worldName, World.Environment environment,
                               GameMode gamemode, boolean pvp) {
        // Don't create if already loaded
        if (Bukkit.getWorld(worldName) != null) return false;

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(environment);
        World world = Bukkit.createWorld(creator);

        if (world == null) {
            plugin.getLogger().warning("Failed to create world: " + worldName);
            return false;
        }

        WorldSettings settings = new WorldSettings(gamemode, pvp, environment);
        worldSettings.put(worldName, settings);
        applySettings(world, settings);

        // Persist to worlds.yml
        saveWorldToConfig(worldName, environment, gamemode, pvp);

        plugin.getLogger().info("Created and loaded world: " + worldName);
        return true;
    }

    private void saveWorldToConfig(String worldName, World.Environment environment,
                                   GameMode gamemode, boolean pvp) {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "worlds.yml");
        org.bukkit.configuration.file.FileConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        String path = "worlds." + worldName;
        config.set(path + ".load-on-startup", true);
        config.set(path + ".environment", environment.name());
        config.set(path + ".gamemode", gamemode.name());
        config.set(path + ".pvp", pvp);
        config.set(path + ".announce-join", false);

        try {
            config.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Could not save worlds.yml: " + e.getMessage());
        }

        // Reload config manager so new world is recognised immediately
        plugin.getConfigManager().reload();
    }

    public WorldSettings getSettings(String worldName) {
        return worldSettings.get(worldName);
    }

    public Set<String> getConfiguredWorldNames() {
        return worldSettings.keySet();
    }
}
