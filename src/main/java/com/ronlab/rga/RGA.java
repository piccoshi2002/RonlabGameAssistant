package com.ronlab.rga;

import com.ronlab.rga.command.HubCommand;
import com.ronlab.rga.command.RGACommand;
import com.ronlab.rga.compass.CompassListener;
import com.ronlab.rga.compass.HubListener;
import com.ronlab.rga.config.ConfigManager;
import com.ronlab.rga.gui.MenuListener;
import com.ronlab.rga.gui.MenuManager;
import com.ronlab.rga.player.InventoryManager;
import com.ronlab.rga.player.LocationTracker;
import com.ronlab.rga.world.WorldManager;
import org.bukkit.plugin.java.JavaPlugin;

public class RGA extends JavaPlugin {

    private static RGA instance;

    private ConfigManager configManager;
    private WorldManager worldManager;
    private MenuManager menuManager;
    private LocationTracker locationTracker;
    private InventoryManager inventoryManager;
    private HubListener hubListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("worlds.yml", false);
        saveResource("menus.yml", false);

        configManager = new ConfigManager(this);
        locationTracker = new LocationTracker(this);
        inventoryManager = new InventoryManager(this);
        worldManager = new WorldManager(this);
        menuManager = new MenuManager(this);

        worldManager.loadConfiguredWorlds();

        hubListener = new HubListener(this);
        getServer().getPluginManager().registerEvents(hubListener, this);
        getServer().getPluginManager().registerEvents(new CompassListener(this, menuManager, hubListener), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this, menuManager), this);

        getCommand("rga").setExecutor(new RGACommand(this));
        getCommand("hub").setExecutor(new HubCommand(this));

        getLogger().info("Ronlab Game Assistant enabled.");
    }

    @Override
    public void onDisable() {
        if (locationTracker != null) locationTracker.saveAll();
        getLogger().info("Ronlab Game Assistant disabled.");
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        menuManager.reload();
        inventoryManager.reload();
        worldManager.loadConfiguredWorlds();
        getLogger().info("Ronlab Game Assistant reloaded.");
    }

    public static RGA getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public MenuManager getMenuManager() { return menuManager; }
    public LocationTracker getLocationTracker() { return locationTracker; }
    public InventoryManager getInventoryManager() { return inventoryManager; }
    public HubListener getHubListener() { return hubListener; }
}
