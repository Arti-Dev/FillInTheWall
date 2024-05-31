package com.articreep.holeinthewall.utils;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

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

    /**
     * Makes all components of this vector the absolute values of their current values.
     * @param vector Vector to evaluate
     * @return The same vector
     */
    public static Vector vectorAbs(Vector vector) {
        vector.setX(Math.abs(vector.getX()));
        vector.setY(Math.abs(vector.getY()));
        vector.setZ(Math.abs(vector.getZ()));
        return vector;
    }

    public static Location centralizeLocation(Location location) {
        // this creates a new location object that's situated at the corner of the block
        Location blockLocation = location.getBlock().getLocation();
        // centralize this location
        return blockLocation.add(0.5, 0.5, 0.5);
    }
}
