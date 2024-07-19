package com.articreep.fillinthewall;

import org.bukkit.Bukkit;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.HashSet;

public class WallBundle {
    private final ArrayList<Wall> walls = new ArrayList<>();

    public WallBundle(Wall... walls) {
        for (Wall wall : walls) {
            wall = wall.copy();
            this.walls.add(wall);
        }
    }

    public static WallBundle importFromYAML(String path) {
        return null;
    }

    public static void exportToYAML(String path, WallBundle bundle) {

    }

    public static void exportToYAML(String path, Wall... walls) {
        exportToYAML(path, new WallBundle(walls));
    }
}
