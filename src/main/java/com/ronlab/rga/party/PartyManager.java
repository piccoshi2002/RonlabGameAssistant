package com.ronlab.rga.party;

import com.ronlab.rga.RGA;
import com.ronlab.rga.minigame.Minigame;
import com.ronlab.rga.minigame.WorldCopyManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class PartyManager implements Listener {

    private final RGA plugin;
    private final WorldCopyManager worldCopyManager;

    // One active party per minigame ID
    private final Map<String, Party> activeParties = new HashMap<>();

    // Player UUID -> party they're in
    private final Map<UUID, Party> playerParties = new HashMap<>();

    public PartyManager(RGA plugin) {
        this.plugin = plugin;
        this.worldCopyManager = new WorldCopyManager(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Disconnect handling ──────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Party party = playerParties.get(player.getUniqueId());
        if (party == null) return;

        // If in game, handle differently — treat as a leave
        if (party.getState() == Party.State.IN_GAME) {
            playerParties.remove(player.getUniqueId());
            party.removeMember(player.getUniqueId());

            // If no members left in game, conclude automatically
            if (party.getMemberCount() == 0 && party.getActiveWorldName() != null) {
                concludeGame(party.getActiveWorldName());
            }
            return;
        }

        // In lobby — handle leader transfer before removing
        if (player.getUniqueId().equals(party.getLeaderUuid())) {
            transferLeader(party, player.getUniqueId());
        }

        party.removeMember(player.getUniqueId());
        playerParties.remove(player.getUniqueId());

        if (party.getMemberCount() == 0) {
            activeParties.remove(party.getMinigameId());
            return;
        }

        broadcastToParty(party, "§e" + player.getName() + " §cdisconnected from the party. §7("
                + party.getMemberCount() + "/" + party.getMinigame().getMaxPlayers() + ")", null);
        refreshLobbyForAll(party);
    }

    // ── Join / Leave ─────────────────────────────────────────────

    public void joinMinigame(Player player, String minigameId) {
        Minigame minigame = plugin.getMinigameManager().getMinigame(minigameId);
        if (minigame == null) {
            player.sendMessage("§cUnknown minigame: " + minigameId);
            return;
        }

        // If player is already in a party for THIS minigame, just reopen the lobby
        Party existingParty = playerParties.get(player.getUniqueId());
        if (existingParty != null && existingParty.getMinigameId().equals(minigameId)) {
            plugin.getLobbyGui().openLobby(player, existingParty);
            return;
        }

        // If player is in a different party, leave it first
        if (existingParty != null) {
            leaveParty(player);
        }

        Party party = activeParties.get(minigameId);

        if (party == null || party.getState() == Party.State.IN_GAME) {
            // Create a new party
            party = new Party(player.getUniqueId(), minigame);
            activeParties.put(minigameId, party);
            playerParties.put(player.getUniqueId(), party);
            player.sendMessage("§aCreated a new party for §6" + minigame.getName() + "§a!");
        } else if (party.isFull()) {
            player.sendMessage("§cThe party for §6" + minigame.getName() + "§c is full!");
            return;
        } else {
            // Join existing party
            party.addMember(player.getUniqueId());
            playerParties.put(player.getUniqueId(), party);
            player.sendMessage("§aJoined the party for §6" + minigame.getName() + "§a!");
            broadcastToParty(party, "§e" + player.getName() + " §ajoined the party! §7("
                    + party.getMemberCount() + "/" + minigame.getMaxPlayers() + ")", player.getUniqueId());
        }

        refreshLobbyForAll(party);
    }

    public void leaveParty(Player player) {
        Party party = playerParties.remove(player.getUniqueId());
        if (party == null) return;

        boolean wasLeader = player.getUniqueId().equals(party.getLeaderUuid());

        // Transfer leader before removing
        if (wasLeader && party.getMemberCount() > 1) {
            transferLeader(party, player.getUniqueId());
        }

        party.removeMember(player.getUniqueId());
        player.sendMessage("§eYou left the party.");
        player.closeInventory();

        if (party.getMemberCount() == 0) {
            activeParties.remove(party.getMinigameId());
            return;
        }

        broadcastToParty(party, "§e" + player.getName() + " §cleft the party. §7("
                + party.getMemberCount() + "/" + party.getMinigame().getMaxPlayers() + ")", null);
        refreshLobbyForAll(party);
    }

    public void toggleReady(Player player) {
        Party party = playerParties.get(player.getUniqueId());
        if (party == null) return;
        if (party.getState() == Party.State.IN_GAME) return;

        boolean nowReady = !party.isReady(player.getUniqueId());
        party.setReady(player.getUniqueId(), nowReady);

        player.sendMessage(nowReady ? "§aYou are now ready!" : "§eYou are no longer ready.");
        broadcastToParty(party, "§e" + player.getName()
                + (nowReady ? " §ais ready!" : " §eis no longer ready."), null);

        refreshLobbyForAll(party);

        if (party.allReady()) {
            startGame(party);
        }
    }

    // ── Leader transfer ──────────────────────────────────────────

    private void transferLeader(Party party, UUID currentLeaderUuid) {
        // Find the next member who isn't the current leader
        for (UUID uuid : party.getMembers()) {
            if (!uuid.equals(currentLeaderUuid)) {
                party.setLeader(uuid);
                Player newLeader = Bukkit.getPlayer(uuid);
                String newLeaderName = newLeader != null ? newLeader.getName() : "Unknown";
                broadcastToParty(party,
                        "§6" + newLeaderName + " §eis now the party leader.", null);
                return;
            }
        }
    }

    // ── Game Start ───────────────────────────────────────────────

    private void startGame(Party party) {
        party.setState(Party.State.IN_GAME);
        Minigame minigame = party.getMinigame();

        broadcastToParty(party, "§a§lAll players ready! Starting §6§l"
                + minigame.getName() + "§a§l...", null);

        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.closeInventory();
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            String worldName = null;

            if (minigame.getWorldType() == Minigame.WorldType.VANILLA) {
                worldName = worldCopyManager.createVanillaWorld(minigame.getId());
            } else {
                worldName = worldCopyManager.copyTemplateWorld(
                        minigame.getTemplateWorld(), minigame.getId());
            }

            if (worldName == null) {
                broadcastToParty(party, "§cFailed to create game world. Please try again.", null);
                party.setState(Party.State.LOBBY);
                refreshLobbyForAll(party);
                return;
            }

            party.setActiveWorldName(worldName);

            if (minigame.getWorldType() == Minigame.WorldType.VANILLA) {
                plugin.getInventoryManager().addTemporaryGroup(worldName,
                        List.of(worldName, worldName + "_nether", worldName + "_the_end"));
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                broadcastToParty(party, "§cGame world failed to load. Please try again.", null);
                party.setState(Party.State.LOBBY);
                return;
            }

            for (UUID uuid : party.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.teleport(world.getSpawnLocation());
                    p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    p.sendMessage("§aThe game has started! Good luck!");
                }
            }

        }, 5L);
    }

    // ── Game End ─────────────────────────────────────────────────

    public void concludeGame(String worldName) {
        Party party = null;
        for (Party p : activeParties.values()) {
            if (worldName.equals(p.getActiveWorldName())) {
                party = p;
                break;
            }
        }

        if (party == null) {
            plugin.getLogger().warning("No party found for world: " + worldName);
            return;
        }

        Minigame minigame = party.getMinigame();
        World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());

        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (hub != null) p.teleport(hub.getSpawnLocation());
                p.sendMessage("§6The game has ended! You have been returned to Hub.");
            }
        }

        if (minigame.getWorldType() == Minigame.WorldType.VANILLA) {
            plugin.getInventoryManager().removeTemporaryGroup(worldName);
        }

        for (UUID uuid : party.getMembers()) {
            playerParties.remove(uuid);
        }
        activeParties.remove(party.getMinigameId());

        String finalWorldName = worldName;
        boolean isVanilla = minigame.getWorldType() == Minigame.WorldType.VANILLA;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            worldCopyManager.cleanupWorld(finalWorldName, isVanilla);
        }, 40L);

        plugin.getLogger().info("Concluded minigame '" + minigame.getName()
                + "' in world '" + worldName + "'.");
    }

    // ── Helpers ──────────────────────────────────────────────────

    public void refreshLobbyForAll(Party party) {
        for (UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                plugin.getLobbyGui().openLobby(p, party);
            }
        }
    }

    private void broadcastToParty(Party party, String message, UUID exclude) {
        for (UUID uuid : party.getMembers()) {
            if (uuid.equals(exclude)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }

    public Party getPartyForPlayer(UUID uuid) {
        return playerParties.get(uuid);
    }

    public Party getPartyForMinigame(String minigameId) {
        return activeParties.get(minigameId);
    }

    public Map<String, Party> getActiveParties() {
        return Collections.unmodifiableMap(activeParties);
    }
}
