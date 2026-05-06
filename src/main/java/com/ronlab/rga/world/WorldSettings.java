package com.ronlab.rga.world;

import org.bukkit.GameMode;
import org.bukkit.World;

public class WorldSettings {

    private final GameMode gamemode;
    private final boolean pvp;
    private final World.Environment environment;

    public WorldSettings(GameMode gamemode, boolean pvp, World.Environment environment) {
        this.gamemode = gamemode;
        this.pvp = pvp;
        this.environment = environment;
    }

    public GameMode getGamemode() { return gamemode; }
    public boolean isPvp() { return pvp; }
    public World.Environment getEnvironment() { return environment; }
}
