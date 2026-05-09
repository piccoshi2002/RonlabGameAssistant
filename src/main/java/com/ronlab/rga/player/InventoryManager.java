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
        worldToGroup.clear();
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

    /**
     * Called by HubListener when a player joins or teleports to Hub on login.
     * Marks them to ignore the next world change so the login teleport
     * doesn't corrupt their saved inventories.
     */
    public void markIgnoreNextWorldChange(UUID uuid) {
        ignoreNextWorldChange.add(uuid);
    }

    // ── Events ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Skip world changes caused by our login teleport
        if (ignoreNextWorldChange.remove(uuid)) return;

        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();
        String hubWorld = plugin.getConfigManager().getHubWorld();

        String fromGroup = getGroup(fromWorld);
        String toGroup = getGroup(toWorld);

        // If moving within the same group, do nothing
        if (fromGroup.equals(toGroup)) return;

        // Save inventory for the group they're leaving (unless leaving Hub)
        if (!fromWorld.equalsIgnoreCase(hubWorld)) {
            saveInventory(player, fromGroup);
        }

        // Load inventory for group they're entering, or clear for Hub
        if (toWorld.equalsIgnoreCase(hubWorld)) {
            // Clear inventory — HubListener will give the compass afterwards
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

        // Save inventory on logout unless in Hub
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

        // Inventory contents (slots 0-35)
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            data.set(path + ".inventory." + i, contents[i]);
        }

        // Armor
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            data.set(path + ".armor." + i, armor[i]);
        }

        // Offhand
        data.set(path + ".offhand", player.getInventory().getItemInOffHand());

        // Held item slot
        data.set(path + ".heldSlot", player.getInventory().getHeldItemSlot());

        // Experience
        data.set(path + ".exp", player.getExp());
        data.set(path + ".level", player.getLevel());
        data.set(path + ".totalExp", player.getTotalExperience());

        // Health and hunger
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
        // Clear first
        clearPlayer(player);

        File file = getPlayerFile(player.getUniqueId());
        if (!file.exists()) return;

        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        String path = group;
        if (!data.contains(path)) return;

        // Inventory contents
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            contents[i] = data.getItemStack(path + ".inventory." + i);
        }
        player.getInventory().setContents(contents);

        // Armor
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = data.getItemStack(path + ".armor." + i);
        }
        player.getInventory().setArmorContents(armor);

        // Offhand
        ItemStack offhand = data.getItemStack(path + ".offhand");
        if (offhand != null) player.getInventory().setItemInOffHand(offhand);

        // Held item slot
        player.getInventory().setHeldItemSlot(data.getInt(path + ".heldSlot", 0));

        // Experience
        player.setExp((float) data.getDouble(path + ".exp", 0));
        player.setLevel(data.getInt(path + ".level", 0));
        player.setTotalExperience(data.getInt(path + ".totalExp", 0));

        // Health and hunger
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

    private File getPlayerFile(UUID uuid) {
        return new File(dataFolder, uuid.toString() + ".yml");
    }
}
