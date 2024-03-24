package com.articreep.holeinthewall;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

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
    private List<BlockDisplay> entities = new ArrayList<>();
    private List<BlockDisplay> toRemove = new ArrayList<>();

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

    public Wall(Material material) {
        this(new HashSet<>(), material);
    }

    public HashSet<Pair<Integer, Integer>> getHoles() {
        return holes;
    }

    public boolean hasHole(Pair<Integer, Integer> hole) {
        for (Pair<Integer, Integer> h : holes) {
            if (h.equals(hole)) {
                return true;
            }
        }
        return false;
    }

    public Material getMaterial() {
        return material;
    }

    /**
     * Returns a set of coordinates where blocks are missing.
     */
    public Map<Pair<Integer, Integer>, Block> getMissingBlocks(PlayingField field) {
        HashMap<Pair<Integer, Integer>, Block> missingBlocks = new HashMap<>();
        Map<Pair<Integer, Integer>, Block> playingFieldBlocks = field.getPlayingFieldBlocks();

        for (Pair<Integer, Integer> hole : holes) {
            if (!field.getPlayingFieldBlocks().containsKey(hole)) {
                missingBlocks.put(hole, field.coordinatesToBlock(hole));
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
        }
        return timeRemaining;
    }

    public void spawnWall(PlayingField field, WallQueue queue, Player player) {
        if (spawned) return;
        // go to the end of the queue
        // spawn block display entities
        // break the holes open
        // store all entities in a list here
        // interpolate towards the playing field
        Location spawnReferencePoint = field.getFieldReferencePoint();
        movementDirection = field.getIncomingDirection();
        spawnReferencePoint.subtract(movementDirection.clone().multiply(queue.getLength()));

        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                Location loc = spawnReferencePoint.clone().add(field.getFieldDirection().multiply(x)).add(0, y, 0);
                // spawn block display entity
                BlockDisplay display = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
                // make invisible for now
                display.setBlock(Material.AIR.createBlockData());
                if (holes.contains(Pair.with(x, y))) {
                    toRemove.add(display);
                }
                entities.add(display);
            }
        }
    }

    public void animateWall(WallQueue queue, Player player) {
        // Block break animation
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockDisplay display : toRemove) {
                    display.remove();
                    entities.remove(display);
                    player.getWorld().spawnParticle(Particle.BLOCK_CRACK, display.getLocation(), 10,
                            0.5, 0.5, 0.5, 0.1, material.createBlockData());
                    player.playSound(player, Sound.BLOCK_STONE_BREAK, 1, 1);
                }
            }
        }.runTaskLater(HoleInTheWall.getInstance(), 5);

        // interpolate towards the playing field
        for (BlockDisplay display : entities) {
            // make them visible
            display.setBlock(material.createBlockData());
            new BukkitRunnable() {
                @Override
                public void run() {
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(timeRemaining);
                    display.setTransformation(new Transformation(
                            movementDirection.clone().multiply(((double) queue.getLength())).toVector3f(),
                            new AxisAngle4f(0, 0, 0, 1), new Vector3f(1, 1, 1),
                            new AxisAngle4f(0, 0, 0, 1)));
                }
            }.runTaskLater(HoleInTheWall.getInstance(), 10);
        }

        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> spawned = true, 10);
    }

    public void despawn() {
        for (BlockDisplay entity : entities) {
            entity.remove();
        }
        entities.clear();
        spawned = false;
    }

    public void insertHoles(Pair<Integer, Integer>... holes) {
        for (Pair<Integer, Integer> hole : holes) {
            if (hole == null || this.holes.contains(hole)) continue;
            this.holes.add(hole);
        }
    }

    public void insertHole(Pair<Integer, Integer> hole) {
        if (hole == null || this.holes.contains(hole)) return;
        this.holes.add(hole);
    }

    public void removeHole(Pair<Integer, Integer> hole) {
        for (Pair<Integer, Integer> h : holes) {
            if (h.equals(hole)) {
                holes.remove(h);
                break;
            }
        }
    }

    /* Some code taken from https://www.baeldung.com/java-set-draw-sample */
    public Pair<Integer, Integer> randomHole() {
        if (holes.isEmpty()) return null;
        // Choose a random hole in the provided wall
        int randomIndex = (int) (Math.random() * getHoles().size());
        int i = 0;
        for (Pair<Integer, Integer> hole : getHoles()) {
            if (i == randomIndex) {
                return hole;
            }
            i++;
        }
        return null;
    }

    public int getTimeRemaining() {
        return timeRemaining;
    }

    public boolean hasSpawned() {
        return spawned;
    }

    public void generateHoles(int random, int cluster) {
        for (int i = 0; i < random; i++) {
            Pair<Integer, Integer> hole = Rush.randomHole();
            insertHole(hole);
        }

        for (int i = 0; i < cluster; i++) {
            Pair<Integer, Integer> hole = (Rush.randomConnectedHole(this));
            if (hole != null) {
                insertHole(hole);
            }
        }
    }
}
