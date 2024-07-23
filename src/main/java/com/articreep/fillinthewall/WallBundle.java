package com.articreep.fillinthewall;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;

public class WallBundle {
    private final ArrayList<Wall> walls = new ArrayList<>();

    public WallBundle(Wall... walls) {
        for (Wall wall : walls) {
            wall = wall.copy();
            this.walls.add(wall);
        }
    }

    public static WallBundle importFromYAML(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        // Check for dimensions
        int length = config.getInt("dimensions.length");
        int height = config.getInt("dimensions.height");
        if (length == 0 || height == 0) {
            Bukkit.getLogger().severe("Invalid or missing dimensions in this YAML config!");
            return new WallBundle();
        }
        ConfigurationSection wallSection = config.getConfigurationSection("walls");
        if (wallSection == null) {
            Bukkit.getLogger().severe("No walls found in this YAML config!");
            return new WallBundle();
        }
        WallBundle bundle = new WallBundle();

        wallSection.getValues(false).forEach((key, value) -> {
            ConfigurationSection wallData = (ConfigurationSection) value;
            String string = wallData.getString("holes");
            int time = wallData.getInt("time");
            if (string == null) {
                return;
            }
            Wall wall = Wall.parseWall(string, length, height, key);
            if (time > 0) wall.setTimeRemaining(time);
            bundle.walls.add(wall);
        });
        return bundle;
    }

    public static void exportToYAML(String path, WallBundle bundle) {

    }

    public static void exportToYAML(String path, Wall... walls) {
        exportToYAML(path, new WallBundle(walls));
    }

    public ArrayList<Wall> getWalls() {
        ArrayList<Wall> list = new ArrayList<>();
        for (Wall wall : walls) {
            list.add(wall.copy());
        }
        return list;
    }

    public static WallBundle getWallBundle(String name) {
        File dataFolder = FillInTheWall.getInstance().getDataFolder();
        File customWalls = new File(dataFolder, "custom/" + name + ".yml");
        return WallBundle.importFromYAML(customWalls);
    }

    public int size() {
        return walls.size();
    }
}
