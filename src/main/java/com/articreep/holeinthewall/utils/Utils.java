package com.articreep.holeinthewall.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.Collection;

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

    // Maximum is exclusive.
    // If the bounds are equal, just check if the value is equal to the bound.
    public static boolean withinBounds(double bound1, double bound2, double value) {
        return value >= Math.min(bound1, bound2) && value <= Math.max(bound1, bound2);
    }

    public static String playersToString(Collection<Player> players) {
        if (players.isEmpty()) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        for (Player player : players) {
            builder.append(player.getName()).append(", ");
        }
        // remove extra commas
        builder.delete(builder.length() - 2, builder.length());
        return builder.toString();
    }

    public static Object getPrivateField(String fieldName, Class clazz, Object object) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(object);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
}
