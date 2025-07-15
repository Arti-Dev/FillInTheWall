package com.articreep.fillinthewall.utils;

import com.articreep.fillinthewall.FillInTheWall;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class Utils {
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();

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
        player.setScoreboard(FillInTheWall.getBlankScoreboard());
        Team team = FillInTheWall.getBlankScoreboard().getTeam(FillInTheWall.NO_COLLISION_TEAM_NAME);
        if (team != null) {
            team.addEntity(player);
        }
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

    /**
     * Transforms the given display up to the provided scale and keeps all other transformation
     * vectors the same
     * @param display The display to scale
     * @param scale How much to scale by
     */
    public static void scaleDisplay(Display display, float scale) {
        Transformation trans = display.getTransformation();
        display.setTransformation(new Transformation(trans.getTranslation(),
                trans.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                trans.getRightRotation()));
    }

    public static Component statusComponent(boolean enabled) {
        if (enabled) return miniMessage.deserialize("<green>ENABLED");
        else return miniMessage.deserialize("<red>DISABLED");
    }

    public static void fillEmptySpace(Inventory inventory, ItemStack border) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, border);
            }
        }
    }

    public static String secondsTohms(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%02dh%02dm%02ds", hours, minutes, secs);
    }
}
