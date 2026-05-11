package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.UUID;

public class WorldCopyManager {

    private final RGA plugin;

    public WorldCopyManager(RGA plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a fresh vanilla world set with settings from the minigame config.
     * Returns the overworld name, or null on failure.
     */
    public String createVanillaWorld(Minigame minigame) {
        String worldName = "minigame_" + minigame.getId() + "_"
                + UUID.randomUUID().toString().substring(0, 8);

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(true);

        World world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().severe("Failed to create vanilla world for minigame: "
                    + minigame.getId());
            return null;
        }

        applyMinigameSettings(world, minigame);
        plugin.getLogger().info("Created vanilla minigame world: " + worldName);
        return worldName;
    }

    /**
     * Copies a template world and applies minigame settings.
     * Returns the new world name, or null on failure.
     */
    public String copyTemplateWorld(Minigame minigame) {
        String templateWorldName = minigame.getTemplateWorld();
        String newWorldName = "minigame_" + minigame.getId() + "_"
                + UUID.randomUUID().toString().substring(0, 8);

        File templateFolder = findWorldFolder(templateWorldName);
        if (templateFolder == null || !templateFolder.exists()) {
            plugin.getLogger().severe("Template world folder not found: " + templateWorldName);
            return null;
        }

        File destination = new File(templateFolder.getParentFile(), newWorldName);

        try {
            copyFolder(templateFolder.toPath(), destination.toPath());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to copy template world: " + e.getMessage());
            return null;
        }

        new File(destination, "session.lock").delete();

        WorldCreator creator = new WorldCreator(newWorldName);
        creator.environment(World.Environment.NORMAL);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().severe("Failed to load copied world: " + newWorldName);
            deleteFolder(destination);
            return null;
        }

        applyMinigameSettings(world, minigame);
        plugin.getLogger().info("Copied template '" + templateWorldName
                + "' to '" + newWorldName + "'.");
        return newWorldName;
    }

    /**
     * Applies world settings defined in the minigame config to a world.
     */
    @SuppressWarnings("unchecked")
    private void applyMinigameSettings(World world, Minigame minigame) {
        world.setPVP(minigame.isPvp());
        world.setDifficulty(minigame.getDifficulty());
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);

        // Apply configured gamerules
        for (Map.Entry<String, String> entry : minigame.getGamerules().entrySet()) {
            GameRule<?> rule = GameRule.getByName(entry.getKey());
            if (rule == null) {
                plugin.getLogger().warning("Unknown gamerule '" + entry.getKey()
                        + "' in minigame " + minigame.getId() + ". Skipping.");
                continue;
            }

            String value = entry.getValue();
            if (rule.getType() == Boolean.class) {
                world.setGameRule((GameRule<Boolean>) rule, Boolean.parseBoolean(value));
            } else if (rule.getType() == Integer.class) {
                try {
                    world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid value '" + value
                            + "' for gamerule '" + entry.getKey() + "'. Skipping.");
                }
            }
        }
    }

    /**
     * Unloads and deletes a minigame world and its nether/end if vanilla type.
     */
    public void cleanupWorld(String worldName, boolean isVanilla) {
        if (isVanilla) {
            unloadAndDelete(worldName + "_nether");
            unloadAndDelete(worldName + "_the_end");
        }
        unloadAndDelete(worldName);
    }

    private void unloadAndDelete(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
            if (hub != null) {
                for (Player p : world.getPlayers()) {
                    p.teleport(hub.getSpawnLocation());
                }
            }
            Bukkit.unloadWorld(world, false);
        }

        File folder = findWorldFolder(worldName);
        if (folder != null && folder.exists()) {
            deleteFolder(folder);
            plugin.getLogger().info("Deleted minigame world: " + worldName);
        }
    }

    // ── Folder utilities ─────────────────────────────────────────

    private File findWorldFolder(String worldName) {
        File topLevel = new File(Bukkit.getWorldContainer(), worldName);
        if (topLevel.exists()) return topLevel;

        File[] topFolders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (topFolders == null) return null;
        for (File worldFolder : topFolders) {
            File dimensionsDir = new File(worldFolder, "dimensions");
            if (!dimensionsDir.exists()) continue;
            File[] namespaceDirs = dimensionsDir.listFiles(File::isDirectory);
            if (namespaceDirs == null) continue;
            for (File nsDir : namespaceDirs) {
                File candidate = new File(nsDir, worldName);
                if (candidate.exists()) return candidate;
            }
        }
        return null;
    }

    private void copyFolder(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) deleteFolder(file);
                else file.delete();
            }
        }
        folder.delete();
    }
}
