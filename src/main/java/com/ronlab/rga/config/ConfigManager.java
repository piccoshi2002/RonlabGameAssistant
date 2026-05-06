package com.ronlab.rga.config;

import com.ronlab.rga.RGA;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class ConfigManager {

    private final RGA plugin;

    private FileConfiguration worldsConfig;
    private FileConfiguration menusConfig;

    public ConfigManager(RGA plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        worldsConfig = loadConfig("worlds.yml");
        menusConfig = loadConfig("menus.yml");
    }

    private FileConfiguration loadConfig(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // ── Convenience getters ──────────────────────────────────────

    public String getHubWorld() {
        return plugin.getConfig().getString("hub-world", "Hub");
    }

    public List<String> getSmpWorlds() {
        return plugin.getConfig().getStringList("smp-worlds");
    }

    public String getMessage(String key) {
        String raw = plugin.getConfig().getString("messages." + key, "&cMessage not found: " + key);
        return ChatColor.translateAlternateColorCodes('&', raw).replace("{world}", "");
    }

    public String getMessage(String key, String worldName) {
        String raw = plugin.getConfig().getString("messages." + key, "&cMessage not found: " + key);
        return ChatColor.translateAlternateColorCodes('&', raw).replace("{world}", worldName);
    }

    public FileConfiguration getWorldsConfig() { return worldsConfig; }
    public FileConfiguration getMenusConfig() { return menusConfig; }
}
