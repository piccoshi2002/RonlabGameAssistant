package com.ronlab.rga.compass;

import com.ronlab.rga.RGA;
import com.ronlab.rga.gui.MenuManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;
        if (!hubListener.isNavigatorCompass(item)) return;

        event.setCancelled(true);
        menuManager.openMenu(event.getPlayer(), "navigator");
    }
}
