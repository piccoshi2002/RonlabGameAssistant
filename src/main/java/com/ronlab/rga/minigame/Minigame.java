package com.ronlab.rga.minigame;

import org.bukkit.Material;

import java.util.List;

public class Minigame {

    public enum WorldType { VANILLA, TEMPLATE }

    private final String id;
    private final String name;
    private final Material displayItem;
    private final List<String> displayLore;
    private final int maxPlayers;
    private final int minPlayers;
    private final WorldType worldType;
    private final String templateWorld;
    private final List<String> startCommands;

    public Minigame(String id, String name, Material displayItem, List<String> displayLore,
                    int maxPlayers, int minPlayers, WorldType worldType, String templateWorld,
                    List<String> startCommands) {
        this.id = id;
        this.name = name;
        this.displayItem = displayItem;
        this.displayLore = displayLore;
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
        this.worldType = worldType;
        this.templateWorld = templateWorld;
        this.startCommands = startCommands;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getDisplayItem() { return displayItem; }
    public List<String> getDisplayLore() { return displayLore; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getMinPlayers() { return minPlayers; }
    public WorldType getWorldType() { return worldType; }
    public String getTemplateWorld() { return templateWorld; }
    public List<String> getStartCommands() { return startCommands; }
}
