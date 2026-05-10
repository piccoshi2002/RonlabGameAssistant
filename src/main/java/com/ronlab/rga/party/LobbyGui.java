package com.ronlab.rga.party;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class LobbyGui implements Listener {

    private final RGA plugin;
    private static final String LOBBY_TITLE_PREFIX = "§8§l";
    private static final int GUI_SIZE = 54;

    // Players currently in the lobby GUI
    private final Set<UUID> openLobbyPlayers = new HashSet<>();

    // Players being refreshed — suppress close removal during refresh
    private final Set<UUID> refreshing = new HashSet<>();

    public LobbyGui(RGA plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openLobby(Player player, Party party) {
        // Mark as refreshing so onInventoryClose doesn't remove from openLobbyPlayers
        refreshing.add(player.getUniqueId());

        Minigame minigame = party.getMinigame();
        String title = LOBBY_TITLE_PREFIX + minigame.getName() + " Lobby";
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        // ── Border ───────────────────────────────────────────────
        ItemStack border = makeBorder();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9, border);  inv.setItem(17, border);
        inv.setItem(18, border); inv.setItem(26, border);
        inv.setItem(27, border); inv.setItem(35, border);
        inv.setItem(36, border); inv.setItem(44, border);

        // ── Game info item (top center) ──────────────────────────
        ItemStack info = new ItemStack(minigame.getDisplayItem());
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§l" + minigame.getName());
        List<String> infoLore = new ArrayList<>(minigame.getDisplayLore());
        infoLore.add("");
        infoLore.add("§7Players: §f" + party.getMemberCount() + "§7/§f" + minigame.getMaxPlayers());
        infoLore.add("§7Min to start: §f" + minigame.getMinPlayers());
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // ── Player slots ─────────────────────────────────────────
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19};
        List<UUID> members = party.getMembers();

        for (int i = 0; i < slots.length; i++) {
            if (i < members.size()) {
                UUID memberUuid = members.get(i);
                Player member = Bukkit.getPlayer(memberUuid);
                boolean isReady = party.isReady(memberUuid);
                boolean isLeader = memberUuid.equals(party.getLeaderUuid());
                boolean isSelf = memberUuid.equals(player.getUniqueId());

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
                if (member != null) skullMeta.setOwningPlayer(member);

                String readyColor = isReady ? "§a" : "§c";
                String name = member != null ? member.getName() : "Unknown";

                skullMeta.setDisplayName(readyColor + name + (isLeader ? " §6★" : ""));

                List<String> lore = new ArrayList<>();
                lore.add(readyColor + (isReady ? "✔ Ready" : "✘ Not Ready"));
                if (isSelf) {
                    lore.add("");
                    lore.add(isReady ? "§eClick to unready" : "§eClick to ready up");
                }
                skullMeta.setLore(lore);
                head.setItemMeta(skullMeta);
                inv.setItem(slots[i], head);

            } else {
                ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta emptyMeta = empty.getItemMeta();
                emptyMeta.setDisplayName("§7Empty Slot");
                empty.setItemMeta(emptyMeta);
                inv.setItem(slots[i], empty);
            }
        }

        // ── Leave party button ────────────────────────────────────
        ItemStack leave = new ItemStack(Material.RED_BED);
        ItemMeta leaveMeta = leave.getItemMeta();
        leaveMeta.setDisplayName("§c§lLeave Party");
        leaveMeta.setLore(List.of("§7Click to leave this party."));
        leave.setItemMeta(leaveMeta);
        inv.setItem(49, leave);

        // ── Status bar ───────────────────────────────────────────
        ItemStack status;
        ItemMeta statusMeta;

        if (party.getMemberCount() < minigame.getMinPlayers()) {
            status = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            statusMeta = status.getItemMeta();
            statusMeta.setDisplayName("§cWaiting for more players...");
            statusMeta.setLore(List.of("§7Need at least §f" + minigame.getMinPlayers() + " §7to start."));
        } else if (party.allReady()) {
            status = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            statusMeta = status.getItemMeta();
            statusMeta.setDisplayName("§a§lStarting game...");
            statusMeta.setLore(List.of());
        } else {
            int notReady = party.getMemberCount() - party.getReadyPlayers().size();
            status = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
            statusMeta = status.getItemMeta();
            statusMeta.setDisplayName("§eWaiting for players to ready up...");
            statusMeta.setLore(List.of("§f" + notReady + " §7player(s) not ready."));
        }
        status.setItemMeta(statusMeta);
        inv.setItem(45, status);

        openLobbyPlayers.add(player.getUniqueId());
        player.openInventory(inv);

        // Done refreshing — remove flag on next tick after inventory opens
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> refreshing.remove(player.getUniqueId()), 1L);
    }

    // ── Event Handlers ───────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openLobbyPlayers.contains(player.getUniqueId())) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType().isAir()) return;

        int slot = event.getSlot();

        // Leave party button
        if (slot == 49) {
            plugin.getPartyManager().leaveParty(player);
            return;
        }

        // Clicking a player head — toggle ready if it's the player's own head
        if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta instanceof SkullMeta skullMeta) {
                if (skullMeta.getOwningPlayer() != null &&
                        skullMeta.getOwningPlayer().getUniqueId().equals(player.getUniqueId())) {
                    plugin.getPartyManager().toggleReady(player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // Don't remove from set if we're refreshing the lobby
        if (!refreshing.contains(player.getUniqueId())) {
            openLobbyPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (openLobbyPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private ItemStack makeBorder() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
