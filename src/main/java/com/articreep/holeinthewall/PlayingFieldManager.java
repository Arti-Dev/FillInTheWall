package com.articreep.holeinthewall;

import com.articreep.holeinthewall.utils.WorldBoundingBox;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.javatuples.Pair;

import java.util.HashMap;
import java.util.Map;

public class PlayingFieldManager implements Listener {
    public static Map<Player, PlayingField> activePlayingFields = new HashMap<>();
    public static Map<WorldBoundingBox, PlayingField> playingFieldLocations = new HashMap<>();

    @EventHandler
    public void onLeverFlick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                event.getClickedBlock().getType() == Material.LEVER) {
            PlayingField field = activePlayingFields.get(event.getPlayer());
            if (field != null) {
                field.getQueue().instantSend();
            }
        }
    }

    @EventHandler
    public void onSwitchToOffhand(PlayerSwapHandItemsEvent event) {
        PlayingField field = activePlayingFields.get(event.getPlayer());
        if (field != null) {
            event.setCancelled(true);
            field.getQueue().instantSend();
            PlayerInventory inventory = event.getPlayer().getInventory();
            ItemStack mainHand = inventory.getItemInMainHand();
            inventory.setItemInMainHand(confirmItem());
            Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> {
                inventory.setItemInMainHand(mainHand);
            }, 10);
        }
    }

    @EventHandler
    public void onCrackedStoneClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getClickedBlock().getType() == Material.CRACKED_STONE_BRICKS) {
                PlayingField field = activePlayingFields.get(event.getPlayer());
                if (field != null) {
                    event.getPlayer().getWorld().spawnParticle(Particle.BLOCK,
                            event.getClickedBlock().getLocation(),
                            10, 0.5, 0.5, 0.5, 0.1,
                            Material.CRACKED_STONE_BRICKS.createBlockData());
                    Bukkit.getScheduler().runTask(HoleInTheWall.getInstance(),
                            () -> event.getClickedBlock().breakNaturally(new ItemStack(Material.LEAD)));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerEnterField(PlayerMoveEvent event) {
        // One game per player.
        if (activePlayingFields.containsKey(event.getPlayer())) {
            PlayingField field = activePlayingFields.get(event.getPlayer());
            if (!field.getBoundingBox().isinBoundingBox(event.getPlayer().getLocation())) {
                removeGame(event.getPlayer());
            }
        } else {
            for (WorldBoundingBox box : playingFieldLocations.keySet()) {
                if (box.isinBoundingBox(event.getPlayer().getLocation())) {
                    newGame(event.getPlayer(), box);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeGame(event.getPlayer());
    }

    public static ItemStack confirmItem() {
        ItemStack item = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRM");
        item.setItemMeta(meta);
        return item;
    }

    // Managing games
    public static void newGame(Player player, WorldBoundingBox box) {
        PlayingField field = playingFieldLocations.get(box);
        field.players.add(player);
        if (!field.hasStarted()) field.start();

        activePlayingFields.put(player, field);
    }

    public static void removeGame(Player player) {
        PlayingField field = activePlayingFields.get(player);
        if (field != null) {
            field.players.remove(player);
            activePlayingFields.remove(player);
            if (field.getPlayers().isEmpty()) {
                field.stop();
            }
        }
    }

    public static void parseConfig(FileConfiguration config) {
        Map<String, Object> map = config.getValues(false);
        for (String key : map.keySet()) {

            // Create a bounding box
            Location refPoint = config.getLocation(key + ".location");
            Vector incomingDirection = BlockFace.valueOf(config.getString(key + ".incoming_direction")).getDirection();
            Vector fieldDirection = BlockFace.valueOf(config.getString(key + ".field_direction")).getDirection();
            int standingDistance = config.getInt(key + ".standing_distance");
            int queueLength = config.getInt(key + ".queue_length");
            int fieldLength = config.getInt(key + ".field_length");
            int fieldHeight = config.getInt(key + ".field_height");
            String environment = config.getString(key + ".environment");

            WorldBoundingBox box = playingFieldActivationBox(refPoint, incomingDirection, fieldDirection, standingDistance, queueLength, fieldLength, fieldHeight);
            WorldBoundingBox effectBox = effectBox(refPoint, incomingDirection, fieldDirection, queueLength, fieldLength, fieldHeight);
            playingFieldLocations.put(box, new PlayingField(
                    refPoint, fieldDirection, incomingDirection, box, effectBox, environment, fieldLength, fieldHeight));


        }
    }

    public static WorldBoundingBox playingFieldActivationBox(Location refPoint,
                                                             Vector incomingDirection,
                                                             Vector fieldDirection,
                                                             int standingDistance,
                                                             int queueLength,
                                                             int fieldLength,
                                                             int fieldHeight) {
        // todo these bounding box coordinates are subject to change
        Location corner1 = refPoint.clone()
                .add(incomingDirection.clone().multiply(standingDistance));
                //.subtract(fieldDirection.clone().multiply(2));
        Location corner2 = refPoint.clone()
                .subtract(incomingDirection.clone().multiply(queueLength))
                .add(fieldDirection.clone().multiply(fieldLength))
                .add(new Vector(0, fieldHeight, 0));

        WorldBoundingBox box = new WorldBoundingBox(corner1, corner2, Particle.HEART);
        box.getBoundingBox().expand(fieldDirection, 2);
        box.getBoundingBox().expand(new Vector(0, fieldHeight, 0), 2);
        box.getBoundingBox().expand(fieldDirection.clone().multiply(-1), 2);

        return box;

    }

    public static WorldBoundingBox effectBox(Location refPoint, Vector incomingDirection, Vector fieldDirection,
                                             int queueLength, int fieldLength, int fieldHeight) {
        Location corner1 = refPoint.clone();
        Location corner2 = refPoint.clone()
                .subtract(incomingDirection.clone().multiply(queueLength))
                .add(fieldDirection.clone().multiply(fieldLength))
                .add(new Vector(0, fieldHeight * 2.5, 0));
        WorldBoundingBox box = new WorldBoundingBox(corner1, corner2, Particle.GLOW);
        box.getBoundingBox().expand(fieldDirection, 7);
        box.getBoundingBox().expand(fieldDirection.clone().multiply(-1), 7);

        return box;

    }

}
