package com.articreep.fillinthewall.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class Utils {
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

    public static void resetScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    public static String getFormattedTime(int ticks) {
        return String.format("%02d:%02d", (ticks/20) / 60, (ticks/20) % 60);
    }

    public static String getPreciseFormattedTime(int ticks) {
        return String.format("%02d:%02d.%02d", (ticks/20) / 60, (ticks/20) % 60, (ticks % 20) * 5);
    }

    /* Taken from https://www.baeldung.com/java-set-draw-sample */
    public static <T> T randomSetElement(Set<T> set) {
        if (set == null || set.isEmpty()) return null;
        int randomIndex = (int) (Math.random() * set.size());
        int i = 0;
        for (T element : set) {
            if (i == randomIndex) {
                return element;
            }
            i++;
        }
        return null;
    }

    public static Material getAlternateMaterial(Material material) {
        String string = material.toString();
        if (string.contains("CONCRETE")) {
            return Material.valueOf(string.replace("CONCRETE", "STAINED_GLASS"));
        } else if (string.contains("STAINED_GLASS")) {
            return Material.valueOf(string.replace("STAINED_GLASS", "CONCRETE"));
        } else {
            return material;
        }
    }

    // Nice little method to create a gui item with a custom name, and description
    public static ItemStack createGuiItem(final Material material, final Component name, final Component... lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();

        // Set the name of the item
        meta.displayName(name);

        // Set the lore of the item
        meta.lore(Arrays.asList(lore));

        item.setItemMeta(meta);

        return item;
    }
}
