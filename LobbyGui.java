package com.ronlab.rga.party;

import com.ronlab.rga.RGA;
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

    // Track which players have the lobby GUI open
    private final Set<UUID> openLobbyPlayers = new HashSet<>();

    public LobbyGui(RGA plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openLobby(Player player, Party party) {
        Minigame minigame = party.getMinigame();
        String title = LOBBY_TITLE_PREFIX + minigame.getName() + " Lobby";

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        // ── Border ───────────────────────────────────────────────
        ItemStack border = makeBorder();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9, border);
        inv.setItem(17, border);
        inv.setItem(18, border);
        inv.setItem(26, border);
        inv.setItem(27, border);
        inv.setItem(35, border);
        inv.setItem(36, border);
        inv.setItem(44, border);

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

        // ── Player slots (slots 10-17, 19-26, 28-35, 37-44) ─────
        int[] playerSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                             28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        // We only need 8 slots max
        int[] slots = Arrays.copyOf(playerSlots, minigame.getMaxPlayers());

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

                if (member != null) {
                    skullMeta.setOwningPlayer(member);
                }

                String readyColor = isReady ? "§a" : "§c";
                String readyStatus = isReady ? "✔ Ready" : "✘ Not Ready";
                String name = member != null ? member.getName() : "Unknown";

                skullMeta.setDisplayName(readyColor + name + (isLeader ? " §6★" : ""));

                List<String> lore = new ArrayList<>();
                lore.add(readyColor + readyStatus);
                if (isSelf) lore.add("§eClick to toggle ready");
                skullMeta.setLore(lore);
                head.setItemMeta(skullMeta);

                inv.setItem(slots[i], head);
            } else {
                // Empty slot
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

        // ── Status message (bottom left) ─────────────────────────
        ItemStack status = new ItemStack(
                party.allReady() ? Material.LIME_STAINED_GLASS_PANE
                        : party.getMemberCount() < minigame.getMinPlayers()
                        ? Material.RED_STAINED_GLASS_PANE
                        : Material.YELLOW_STAINED_GLASS_PANE
        );
        ItemMeta statusMeta = status.getItemMeta();
        if (party.getMemberCount() < minigame.getMinPlayers()) {
            statusMeta.setDisplayName("§cWaiting for more players...");
            statusMeta.setLore(List.of("§7Need at least §f" + minigame.getMinPlayers() + " §7to start."));
        } else if (party.allReady()) {
            statusMeta.setDisplayName("§a§lStarting game...");
        } else {
            int notReady = party.getMemberCount() - party.getReadyPlayers().size();
            statusMeta.setDisplayName("§eWaiting for players to ready up...");
            statusMeta.setLore(List.of("§f" + notReady + " §7player(s) not ready."));
        }
        status.setItemMeta(statusMeta);
        inv.setItem(45, status);

        openLobbyPlayers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    // ── Event Handlers ───────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openLobbyPlayers.contains(player.getUniqueId())) return;

        String title = event.getView().getTitle();
        if (!title.startsWith(LOBBY_TITLE_PREFIX)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType().isAir()) return;

        int slot = event.getSlot();

        // Leave party button
        if (slot == 49) {
            plugin.getPartyManager().leaveParty(player);
            return;
        }

        // Player head slots — clicking your own head toggles ready
        Party party = plugin.getPartyManager().getPartyForPlayer(player.getUniqueId());
        if (party == null) return;

        // Check if clicked slot is a player head
        if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            // Only let them toggle if it's their own head
            // We identify this by checking if the skull owner matches the player
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
        openLobbyPlayers.remove(player.getUniqueId());
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
