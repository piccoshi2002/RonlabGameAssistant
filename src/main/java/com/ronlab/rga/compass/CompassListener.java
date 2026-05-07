package com.ronlab.rga.compass;

import com.ronlab.rga.RGA;
import com.ronlab.rga.gui.MenuManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class CompassListener implements Listener {

    private final MenuManager menuManager;
    private final HubListener hubListener;

    public CompassListener(RGA plugin, MenuManager menuManager, HubListener hubListener) {
        this.menuManager = menuManager;
        this.hubListener = hubListener;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only fire once — ignore off-hand duplicate event
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();

        // If they're holding ANY compass and are in the hub, re-give the tagged one
        // This handles the case where they have an untagged compass from a previous plugin
        if (item.getType() == org.bukkit.Material.COMPASS && !hubListener.isNavigatorCompass(item)) {
            String hubWorld = hubListener.getPlugin().getConfigManager().getHubWorld();
            if (player.getWorld().getName().equalsIgnoreCase(hubWorld)) {
                // Remove untagged compass and give proper one
                player.getInventory().setItem(event.getPlayer().getInventory().getHeldItemSlot(), null);
                hubListener.giveCompass(player);
                player.sendMessage("§6Your navigator compass has been updated.");
                return;
            }
        }

        if (!hubListener.isNavigatorCompass(item)) return;

        event.setCancelled(true);
        menuManager.openMenu(player, "navigator");
    }
}
