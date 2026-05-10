package com.ronlab.rga.minigame;

import com.ronlab.rga.RGA;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;

public class WorldCopyManager {

    private final RGA plugin;

    public WorldCopyManager(RGA plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a fresh vanilla world set (overworld + nether + end linked together)
     * Returns the overworld name, or null on failure.
     */
    public String createVanillaWorld(String minigameId) {
        String worldName = "minigame_" + minigameId + "_" + UUID.randomUUID().toString().substring(0, 8);

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(true);

        World world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().severe("Failed to create vanilla world for minigame: " + minigameId);
            return null;
        }

        // Apply standard survival settings
        world.setPVP(true);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);

        plugin.getLogger().info("Created vanilla minigame world: " + worldName);
        return worldName;
    }

    /**
     * Copies a template world to a new uniquely named world and loads it.
     * Returns the new world name, or null on failure.
     */
    public String copyTemplateWorld(String templateWorldName, String minigameId) {
        String newWorldName = "minigame_" + minigameId + "_" + UUID.randomUUID().toString().substring(0, 8);

        // Find the template world folder
        File templateFolder = findWorldFolder(templateWorldName);
        if (templateFolder == null || !templateFolder.exists()) {
            plugin.getLogger().severe("Template world folder not found: " + templateWorldName);
            return null;
        }

        // Determine destination — use same parent as template
        File destination = new File(templateFolder.getParentFile(), newWorldName);

        try {
            copyFolder(templateFolder.toPath(), destination.toPath());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to copy template world: " + e.getMessage());
            return null;
        }

        // Delete session.lock if present to prevent conflicts
        new File(destination, "session.lock").delete();

        // Load the copied world
        WorldCreator creator = new WorldCreator(newWorldName);
        creator.environment(World.Environment.NORMAL);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().severe("Failed to load copied world: " + newWorldName);
            deleteFolder(destination);
            return null;
        }

        world.setPVP(true);
        plugin.getLogger().info("Copied template '" + templateWorldName + "' to '" + newWorldName + "'.");
        return newWorldName;
    }

    /**
     * Unloads and deletes a minigame world (and its nether/end if vanilla type).
     */
    public void cleanupWorld(String worldName, boolean isVanilla) {
        // For vanilla worlds, also clean up linked nether and end
        if (isVanilla) {
            unloadAndDelete(worldName + "_nether");
            unloadAndDelete(worldName + "_the_end");
        }
        unloadAndDelete(worldName);
    }

    private void unloadAndDelete(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            // Kick any remaining players to hub just in case
            World hub = Bukkit.getWorld(plugin.getConfigManager().getHubWorld());
            if (hub != null) {
                for (Player p : world.getPlayers()) {
                    p.teleport(hub.getSpawnLocation());
                }
            }
            Bukkit.unloadWorld(world, false);
        }

        // Delete the folder
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
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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
