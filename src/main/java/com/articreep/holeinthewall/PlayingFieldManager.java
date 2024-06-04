package com.articreep.holeinthewall;

import com.articreep.holeinthewall.multiplayer.MultiplayerGame;
import com.articreep.holeinthewall.utils.WorldBoundingBox;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class PlayingFieldManager implements Listener {
    // todo may be better to just store the playing fields in a set instead of player -> field map
    public static Map<Player, PlayingField> activePlayingFields = new HashMap<>();
    public static Map<WorldBoundingBox, PlayingField> playingFieldLocations = new HashMap<>();
    public static MultiplayerGame game = null;

    @EventHandler
    public void onPlayerEnterField(PlayerMoveEvent event) {
        // One game per player.
        if (activePlayingFields.containsKey(event.getPlayer())) {
            PlayingField field = activePlayingFields.get(event.getPlayer());
            // If player is outside of bounding box and players are not bound to the field, remove the game.
            if (!field.getBoundingBox().isinBoundingBox(event.getPlayer().getLocation())
            && !field.isBindPlayers()) {
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

    // Managing games
    public static void newGame(Player player, WorldBoundingBox box) {
        PlayingField field = playingFieldLocations.get(box);
        // todo this is not consistent with the other bind check in the remove method
        if (field.isBindPlayers()) return;
        field.addNewPlayer(player);

        activePlayingFields.put(player, field);
    }

    public static void removeGame(Player player) {
        PlayingField field = activePlayingFields.get(player);
        if (field != null) {
            if (field.playerCount() == 1) {
                if (field.hasStarted()) {
                    field.stop();
                }
                else field.removeMenu();
            }
            field.removePlayer(player);
            activePlayingFields.remove(player);
        }
    }

    public static void removeAllGames() {
        for (Player player : activePlayingFields.keySet()) {
            removeGame(player);
        }
        activePlayingFields.clear();
    }

    public static void parseConfig(FileConfiguration config) {
        playingFieldLocations.clear();
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
            boolean hideBottomBorder = config.getBoolean(key + ".hide_bottom_border");
            String wallMaterialName = config.getString(key + ".wall_material");
            String playerMaterialName = config.getString(key + ".player_material");
            Material wallMaterial;
            Material playerMaterial;

            if (wallMaterialName == null || Material.getMaterial(wallMaterialName) == null) {
                wallMaterial = Material.BLUE_CONCRETE;
            } else {
                wallMaterial = Material.getMaterial(wallMaterialName);
            }

            if (playerMaterialName == null || Material.getMaterial(playerMaterialName) == null) {
                playerMaterial = Material.TINTED_GLASS;
            } else {
                playerMaterial = Material.getMaterial(playerMaterialName);
            }

            WorldBoundingBox box = playingFieldActivationBox(refPoint.clone().subtract(0, 1, 0), incomingDirection, fieldDirection, standingDistance, queueLength, fieldLength, fieldHeight);
            WorldBoundingBox effectBox = effectBox(refPoint, incomingDirection, fieldDirection, queueLength, fieldLength, fieldHeight);
            playingFieldLocations.put(box, new PlayingField(
                    refPoint, fieldDirection, incomingDirection, box, effectBox, environment, fieldLength, fieldHeight, wallMaterial, playerMaterial, hideBottomBorder));


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
