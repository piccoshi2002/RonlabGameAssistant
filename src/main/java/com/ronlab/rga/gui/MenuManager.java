package com.ronlab.rga.gui;

import com.ronlab.rga.RGA;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MenuManager {

    private final RGA plugin;
    private final Map<String, MenuDefinition> menus = new HashMap<>();
    private final Map<String, String> titleToMenuName = new HashMap<>();

    public MenuManager(RGA plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        menus.clear();
        titleToMenuName.clear();

        ConfigurationSection menusSection = plugin.getConfigManager()
                .getMenusConfig().getConfigurationSection("menus");
        if (menusSection == null) {
            plugin.getLogger().warning("No menus section found in menus.yml!");
            return;
        }

        for (String menuName : menusSection.getKeys(false)) {
            ConfigurationSection menuSection = menusSection.getConfigurationSection(menuName);
            if (menuSection == null) continue;

            String title = color(menuSection.getString("title", "&8Menu"));
            int size = menuSection.getInt("size", 27);
            List<MenuItemDefinition> items = new ArrayList<>();

            ConfigurationSection itemsSection = menuSection.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemKey : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                    if (itemSection == null) continue;

                    String materialName = itemSection.getString("material", "STONE").toUpperCase();
                    Material material = Material.matchMaterial(materialName);
                    if (material == null) {
                        plugin.getLogger().warning("Invalid material '" + materialName
                                + "' for item '" + itemKey + "' in menu '" + menuName + "'.");
                        material = Material.STONE;
                    }

                    String name = color(itemSection.getString("name", "&fItem"));
                    List<String> rawLore = itemSection.getStringList("lore");
                    List<String> lore = new ArrayList<>();
                    for (String line : rawLore) lore.add(color(line));

                    int slot = itemSection.getInt("slot", 0);
                    List<String> leftClick = itemSection.getStringList("left_click");
                    List<String> rightClick = itemSection.getStringList("right_click");
                    boolean showPlayerCount = itemSection.getBoolean("show-player-count", false);
                    String playerCountWorld = itemSection.getString("player-count-world", "");

                    items.add(new MenuItemDefinition(material, name, lore, slot,
                            leftClick, rightClick, showPlayerCount, playerCountWorld));
                }
            }

            MenuDefinition def = new MenuDefinition(menuName, title, size, items);
            menus.put(menuName, def);
            titleToMenuName.put(title, menuName);
        }

        plugin.getLogger().info("Loaded " + menus.size() + " menu(s) from menus.yml.");
    }

    public void openMenu(Player player, String menuName) {
        MenuDefinition def = menus.get(menuName);
        if (def == null) {
            plugin.getLogger().warning("Tried to open unknown menu: " + menuName);
            player.sendMessage(ChatColor.RED + "Menu not found: " + menuName);
            return;
        }

        Inventory inv = Bukkit.createInventory(null, def.getSize(), def.getTitle());

        for (MenuItemDefinition item : def.getItems()) {
            ItemStack stack = new ItemStack(item.getMaterial());
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName(item.getName());

            // Build lore, injecting live player count if configured
            List<String> lore = new ArrayList<>(item.getLore());
            if (item.isShowPlayerCount() && !item.getPlayerCountWorld().isEmpty()) {
                int count = getPlayerCount(item.getPlayerCountWorld());
                String playerWord = count == 1 ? "player" : "players";
                lore.add("");
                lore.add(ChatColor.YELLOW + "" + count + " " + playerWord + " online");
            }

            if (!lore.isEmpty()) meta.setLore(lore);
            stack.setItemMeta(meta);

            if (item.getSlot() >= 0 && item.getSlot() < def.getSize()) {
                inv.setItem(item.getSlot(), stack);
            }
        }

        player.openInventory(inv);
    }

    private int getPlayerCount(String worldName) {
        // Support comma-separated world names for grouped worlds like SMP
        String[] worlds = worldName.split(",");
        int count = 0;
        for (String w : worlds) {
            World world = Bukkit.getWorld(w.trim());
            if (world != null) count += world.getPlayers().size();
        }
        return count;
    }

    public MenuDefinition getMenuByTitle(String title) {
        String menuName = titleToMenuName.get(title);
        if (menuName == null) return null;
        return menus.get(menuName);
    }

    public boolean isRGAMenu(String title) {
        return titleToMenuName.containsKey(title);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
