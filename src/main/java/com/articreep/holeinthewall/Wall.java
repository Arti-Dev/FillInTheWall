package com.articreep.holeinthewall;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import org.javatuples.Pair;

import java.util.*;

public class Wall {
    // The walls will be 7x4 blocks
    private HashSet<Pair<Integer, Integer>> holes;
    private Material material;
    private boolean spawned = false;
    private int maxTime = -1;
    private int timeRemaining = -1;
    private int length = 7;
    private int height = 4;
    private Vector movementDirection = null;
    private List<Entity> entities = new ArrayList<>();

    public Wall(HashSet<Pair<Integer, Integer>> holes, Material material) {
        this.holes = holes;
        this.material = material;
    }

    public Wall(HashSet<Pair<Integer, Integer>> holes) {
        this(holes, Material.BLUE_CONCRETE);
    }

    public Wall() {
        this(new HashSet<>(), Material.BLUE_CONCRETE);
    }

    public HashSet<Pair<Integer, Integer>> getHoles() {
        return holes;
    }

    public Material getMaterial() {
        return material;
    }

    /**
     * Returns a set of coordinates where blocks are missing.
     */
    public Set<Pair<Integer, Integer>> getMissingBlocks(PlayingField field) {
        HashSet<Pair<Integer, Integer>> missingBlocks = new HashSet<>();
        Map<Pair<Integer, Integer>, Block> playingFieldBlocks = field.getPlayingFieldBlocks();

        for (Pair<Integer, Integer> hole : holes) {
            if (!field.getPlayingFieldBlocks().containsKey(hole)) {
                missingBlocks.add(hole);
            }
        }
        return missingBlocks;
    }

    /**
     * Returns a mapping of coordinates to blocks such that these blocks aren't in the right place.
     * @param field Playing field to compare against
     * @return All blocks that shouldn't be on the playing field
     */
    public Map<Pair<Integer, Integer>, Block> getExtraBlocks(PlayingField field) {
        HashMap<Pair<Integer, Integer>, Block> extraBlocks = new HashMap<>();
        Map<Pair<Integer, Integer>, Block> playingFieldBlocks = field.getPlayingFieldBlocks();

        for (Pair<Integer, Integer> blockCoords : playingFieldBlocks.keySet()) {
            if (!holes.contains(blockCoords)) {
                extraBlocks.put(blockCoords, playingFieldBlocks.get(blockCoords));
            }
        }
        return extraBlocks;
    }

    public Map<Pair<Integer, Integer>, Block> getCorrectBlocks(PlayingField field) {
        HashMap<Pair<Integer, Integer>, Block> correctBlocks = new HashMap<>();
        Map<Pair<Integer, Integer>, Block> playingFieldBlocks = field.getPlayingFieldBlocks();

        for (Pair<Integer, Integer> blockCoords : playingFieldBlocks.keySet()) {
            if (holes.contains(blockCoords)) {
                correctBlocks.put(blockCoords, playingFieldBlocks.get(blockCoords));
            }
        }
        return correctBlocks;
    }

    public void setTimeRemaining(int timeRemaining) {
        this.timeRemaining = timeRemaining;
        this.maxTime = timeRemaining;
    }

    public int tick(WallQueue queue) {
        if (!spawned) return -1;
        if (timeRemaining > 0) {
            timeRemaining--;
            for (Entity entity : entities) {
                Bukkit.broadcastMessage("Moving entity");
                entity.teleport(entity.getLocation().add(movementDirection.clone()
                        .multiply((double) queue.getLength()/maxTime)));
            }
        }
        return timeRemaining;
    }

    public void spawnWall(PlayingField field, WallQueue queue) {
        if (spawned) return;
        // go to the end of the queue
        // spawn block display entities
        // store all entities in a list here
        Location spawnReferencePoint = field.getFieldReferencePoint();
        movementDirection = field.getIncomingDirection();
        spawnReferencePoint.subtract(movementDirection.clone().multiply(queue.getLength()));

        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                Location loc = spawnReferencePoint.clone().add(field.getFieldDirection().multiply(x)).add(0, y, 0);
                if (!holes.contains(Pair.with(x, y))) {
                    // spawn block display entity
                    BlockDisplay display = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
                    display.setBlock(material.createBlockData());
                    entities.add(display);
                }
            }
        }
        spawned = true;

    }

    public int getTimeRemaining() {
        return timeRemaining;
    }
}
