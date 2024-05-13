package com.articreep.holeinthewall.utils;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

public class Utils {
    public static BoundingBox locationsToBoundingBox(Location corner1, Location corner2) {
        return new BoundingBox(
                Math.min(corner1.getX(), corner2.getX()),
                Math.min(corner1.getY(), corner2.getY()),
                Math.min(corner1.getZ(), corner2.getZ()),
                Math.max(corner1.getX(), corner2.getX()),
                Math.max(corner1.getY(), corner2.getY()),
                Math.max(corner1.getZ(), corner2.getZ())
        );
    }
}
