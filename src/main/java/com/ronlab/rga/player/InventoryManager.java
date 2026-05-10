package com.ronlab.rga.player;

import com.ronlab.rga.RGA;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class InventoryManager implements Listener {

    private final RGA plugin;
    private final File dataFolder;

    // Map of world name -> group name
    private final Map<String, String> worldToGroup = new HashMap<>();

    // Players whose next world change should be ignored (login teleport)
    private final Set<UUID> ignoreNextWorldChange = new HashSet<>();

    public InventoryManager(RGA plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "inventories");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        // Preserve temporary minigame groups across reloads
        Map<String, String> tempEntries = new HashMap<>();
        for (Map.Entry<String, String> entry : worldToGroup.entrySet()) {
            if (entry.getValue().startsWith("mg_")) {
                tempEntries.put(entry.getKey(), entry.getValue());
            }
        }

        worldToGroup.clear();
        worldToGroup.putAll(tempEntries);

        ConfigurationSection groups = plugin.getConfig().getConfigurationSection("inventory-groups");
        if (groups == null) {
            plugin.getLogger().warning("No inventory-groups section found in config.yml!");
            return;
        }
        for (String groupName : groups.getKeys(false)) {
            List<String> worlds = groups.getStringList(groupName + ".worlds");
            for (String world : worlds) {
                worldToGroup.put(world, groupName);
            }
        }
        plugin.getLogger().info("Loaded " + worldToGroup.size() + " world-to-group mappings.");
    }

    // ── Public API ───────────────────────────────────────────────

    public void markIgnoreNextWorldChange(UUID uuid) {
        ignoreNextWorldChange.add(uuid);
    }

    /**
     * Registers a temporary inventory group for a minigame instance.
     * All listed worlds share the same inventory during the game.
     */
    public void addTemporaryGroup(String groupName, String... worlds) {
        for (String world : worlds) {
            worldToGroup.put(world, groupName);
        }
    }

    /**
     * Removes a temporary inventory group when a minigame ends.
     */
    public void removeTemporaryGroup(String groupName) {
        worldToGroup.entrySet().removeIf(e -> e.getValue().equals(groupName));
    }

    // ── Events ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (ignoreNextWorldChange.remove(uuid)) return;

        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();
        String hubWorld = plugin.getConfigManager().getHubWorld();

        String fromGroup = getGroup(fromWorld);
        String toGroup = getGroup(toWorld);

        if (fromGroup.equals(toGroup)) return;

        if (!fromWorld.equalsIgnoreCase(hubWorld)) {
            saveInventory(player, fromGroup);
        }

        if (toWorld.equalsIgnoreCase(hubWorld)) {
            clearPlayer(player);
        } else {
            loadInventory(player, toGroup);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String currentWorld = player.getWorld().getName();
        String hubWorld = plugin.getConfigManager().getHubWorld();

        if (!currentWorld.equalsIgnoreCase(hubWorld)) {
            saveInventory(player, getGroup(currentWorld));
        }

        ignoreNextWorldChange.remove(player.getUniqueId());
    }

    // ── Save / Load ──────────────────────────────────────────────

    public void saveInventory(Player player, String group) {
        File file = getPlayerFile(player.getUniqueId());
        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        String path = group;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            data.set(path + ".inventory." + i, contents[i]);
        }

        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            data.set(path + ".armor." + i, armor[i]);
        }

        data.set(path + ".offhand", player.getInventory().getItemInOffHand());
        data.set(path + ".heldSlot", player.getInventory().getHeldItemSlot());
        data.set(path + ".exp", player.getExp());
        data.set(path + ".level", player.getLevel());
        data.set(path + ".totalExp", player.getTotalExperience());
        data.set(path + ".health", player.getHealth());
        data.set(path + ".foodLevel", player.getFoodLevel());
        data.set(path + ".saturation", player.getSaturation());

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save inventory for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void loadInventory(Player player, String group) {
        clearPlayer(player);

        File file = getPlayerFile(player.getUniqueId());
        if (!file.exists()) return;

        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        String path = group;
        if (!data.contains(path)) return;

        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            contents[i] = data.getItemStack(path + ".inventory." + i);
        }
        player.getInventory().setContents(contents);

        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = data.getItemStack(path + ".armor." + i);
        }
        player.getInventory().setArmorContents(armor);

        ItemStack offhand = data.getItemStack(path + ".offhand");
        if (offhand != null) player.getInventory().setItemInOffHand(offhand);

        player.getInventory().setHeldItemSlot(data.getInt(path + ".heldSlot", 0));
        player.setExp((float) data.getDouble(path + ".exp", 0));
        player.setLevel(data.getInt(path + ".level", 0));
        player.setTotalExperience(data.getInt(path + ".totalExp", 0));

        double health = data.getDouble(path + ".health", 20.0);
        player.setHealth(Math.min(health, player.getMaxHealth()));
        player.setFoodLevel(data.getInt(path + ".foodLevel", 20));
        player.setSaturation((float) data.getDouble(path + ".saturation", 5.0));
    }

    // ── Helpers ──────────────────────────────────────────────────

    public String getGroup(String worldName) {
        return worldToGroup.getOrDefault(worldName, worldName);
    }

    public void clearPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
    }

    // ── Temporary minigame groups ────────────────────────────────

    public void addTemporaryGroup(String groupName, List<String> worlds) {
        for (String world : worlds) {
            worldToGroup.put(world, "mg_" + groupName);
        }
    }

    public void removeTemporaryGroup(String groupName) {
        String key = "mg_" + groupName;
        worldToGroup.entrySet().removeIf(e -> e.getValue().equals(key));
    }

    private File getPlayerFile(UUID uuid) {
        return new File(dataFolder, uuid.toString() + ".yml");
    }
}
