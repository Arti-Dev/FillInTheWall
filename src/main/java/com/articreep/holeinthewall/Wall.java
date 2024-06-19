package com.articreep.holeinthewall;

import com.articreep.holeinthewall.utils.Utils;
import com.google.common.annotations.VisibleForTesting;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class Wall {
    // The walls will be 7x4 blocks
    private final int length;
    private final int height;
    private final HashSet<Pair<Integer, Integer>> holes;
    private WallState state = WallState.HIDDEN;
    private int maxTime = -1;
    private int teleportTo = -1;
    private int timeRemaining = -1;
    private Vector movementDirection = null;
    private final Set<BlockDisplay> entities = new HashSet<>();
    private final List<BlockDisplay> blocks = new ArrayList<>();
    private final List<BlockDisplay> border = new ArrayList<>();
    private final List<BlockDisplay> toRemove = new ArrayList<>();
    private Material material = null;
    private int tickCooldown = 0;
    private int teleportDuration = 7;

    public Wall(HashSet<Pair<Integer, Integer>> holes, int length, int height) {
        this.holes = holes;
        this.length = length;
        this.height = height;
    }

    public Wall(int length, int height) {
        this(new HashSet<>(), length, height);
    }

    public HashSet<Pair<Integer, Integer>> getHoles() {
        return holes;
    }

    public void setTimeRemaining(int timeRemaining) {
        this.timeRemaining = timeRemaining;
        this.teleportTo = timeRemaining;
        this.maxTime = timeRemaining;
    }

    public int getTimeRemaining() {
        return timeRemaining;
    }

    public WallState getWallState() {
        return state;
    }

    public void spawnWall(PlayingField field, WallQueue queue, boolean hideBottomBorder) {
        if (state != WallState.HIDDEN) return;
        // go to the end of the queue
        // spawn block display entities
        // break the holes open
        // store all entities in a list here
        // interpolate towards the playing field
        Location spawnReferencePoint = field.getReferencePoint();
        movementDirection = field.getIncomingDirection();
        // todo should be effective length, in the future
        spawnReferencePoint.subtract(movementDirection.clone().multiply(queue.getFullLength()));

        World world = spawnReferencePoint.getWorld();
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                Location loc = spawnReferencePoint.clone()
                        .add(field.getFieldDirection().multiply(x))
                        .add(0, y, 0);
                // spawn block display entity
                BlockDisplay display = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
                // make invisible for now
                display.setBlock(Material.AIR.createBlockData());
                display.setTransformation(new Transformation(
                        new Vector3f(-0.5f, -0.5f, -0.5f),
                        new AxisAngle4f(0, 0, 0, 1), new Vector3f(1, 1, 1),
                        new AxisAngle4f(0, 0, 0, 1)));

                if (holes.contains(Pair.with(x, y))) {
                    toRemove.add(display);
                }
                blocks.add(display);
                entities.add(display);
            }
        }

        // borders

        Location centerOfWall = field.getCenter().subtract(movementDirection.clone().multiply(queue.getFullLength()));

        // todo this code is ugly
        // horizontal ones

        // stretch block in field direction by a factor of its length
        // compress block in y direction by a factor of 10 (x0.1)
        // leave last direction as zero

        // Start with a vector with all 1s except for y direction
        Vector scaleVector = new Vector(0, 0.1, 0);
        // Subtract 1 from this to factor for the 1s in the existing vector
        scaleVector.add(field.getFieldDirection().multiply(length + 0.2 /*to fill in the corners */));
        scaleVector.add(field.getIncomingDirection());
        Utils.vectorAbs(scaleVector);

        BlockDisplay bottomBorder = null;
        if (!hideBottomBorder) {
            bottomBorder = (BlockDisplay) world.spawnEntity(centerOfWall.clone()
                    // dip down a little
                    .subtract(0, ((double) height /2)+0.05, 0),
                    EntityType.BLOCK_DISPLAY);
            bottomBorder.setTransformation(new Transformation(
                    // translation - half of the scale vectors and negative
                    scaleVector.clone().multiply(-0.5).toVector3f(),
                    new AxisAngle4f(0, 0, 0, 1), scaleVector.toVector3f(),
                    new AxisAngle4f(0, 0, 0, 1)));
            bottomBorder.setBlock(Material.IRON_BLOCK.createBlockData());
        }

        BlockDisplay topBorder = (BlockDisplay) world.spawnEntity(centerOfWall.clone()
                // middle of the wall
                .add(0, ((double) height/2)+0.05, 0),
                // go up and go up a little more
                EntityType.BLOCK_DISPLAY);
        topBorder.setTransformation(new Transformation(
                // translation - half of the scale vectors and negative
                scaleVector.clone().multiply(-0.5).toVector3f(),
                new AxisAngle4f(0, 0, 0, 1), scaleVector.toVector3f(),
                new AxisAngle4f(0, 0, 0, 1)));
        topBorder.setBlock(Material.IRON_BLOCK.createBlockData());

        // vertical ones
        scaleVector = new Vector(0, 0, 0);
        // Compress
        scaleVector.add(field.getFieldDirection().multiply(0.1));
        // Stretch in the y direction
        scaleVector.add(new Vector(0, height, 0));
        scaleVector.add(field.getIncomingDirection());
        Utils.vectorAbs(scaleVector);

        BlockDisplay leftBorder = (BlockDisplay) world.spawnEntity(centerOfWall.clone()
                // left a little;
                .subtract(field.getFieldDirection().multiply((double) length/2 + 0.05)),
                EntityType.BLOCK_DISPLAY);
        leftBorder.setTransformation(new Transformation(
                // translation - half of the scale vectors and negative
                scaleVector.clone().multiply(-0.5).toVector3f(),
                new AxisAngle4f(0, 0, 0, 1), scaleVector.toVector3f(),
                new AxisAngle4f(0, 0, 0, 1)));
        leftBorder.setBlock(Material.IRON_BLOCK.createBlockData());

        BlockDisplay rightBorder = (BlockDisplay) world.spawnEntity(centerOfWall.clone()
                // right a little;
                .add(field.getFieldDirection().multiply((double) length /2 + 0.05)),
                EntityType.BLOCK_DISPLAY);
        rightBorder.setTransformation(new Transformation(
                // translation - half of the scale vectors and negative
                scaleVector.clone().multiply(-0.5).toVector3f(),
                new AxisAngle4f(0, 0, 0, 1), scaleVector.toVector3f(),
                new AxisAngle4f(0, 0, 0, 1)));
        rightBorder.setBlock(Material.IRON_BLOCK.createBlockData());


        border.addAll(Arrays.asList(topBorder, leftBorder, rightBorder));
        if (!hideBottomBorder) border.add(bottomBorder);
        entities.addAll(border);

        for (BlockDisplay display : entities) {
            display.setTeleportDuration(teleportDuration);
            display.setInterpolationDuration(1);
        }

    }

    public void activateWall(Set<Player> players, Material defaultMaterial) {
        state = WallState.ANIMATING;
        // make them visible immediately
        if (this.material == null) {
            material = defaultMaterial;
        }
        for (BlockDisplay display : blocks) {
            display.setBlock(material.createBlockData());
        }

        for (BlockDisplay display : border) {
            display.setBlock(Material.IRON_BLOCK.createBlockData());
        }

        // Block break animation
        Material finalMaterial = material;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockDisplay display : toRemove) {
                    display.remove();
                    entities.remove(display);
                    blocks.remove(display);
                    for (Player player : players) {
                        player.getWorld().spawnParticle(Particle.BLOCK, display.getLocation(), 10,
                                0.5, 0.5, 0.5, 0.1, finalMaterial.createBlockData());
                        player.playSound(player, Sound.BLOCK_STONE_BREAK, 1, 1);
                    }
                }
            }
        }.runTaskLater(HoleInTheWall.getInstance(), 5);

        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> state = WallState.VISIBLE, 10);
    }

    /** Teleports the board and returns time remaining. */
    public int tick(WallQueue queue) {
        if (state != WallState.VISIBLE) return -1;

        int length = queue.getFullLength();

        if (tickCooldown == 0) {
            correct();
            for (BlockDisplay display : entities) {
                Location target = display.getLocation()
                        .add(movementDirection.clone().multiply(length * teleportDuration / (double) maxTime));
                display.teleport(target);
            }
            // Note where we're teleporting to in case we need to correct the wall's location during interpolation
            teleportTo = timeRemaining - teleportDuration;
            // Reset tick cooldown
            tickCooldown = teleportDuration;
        }
        tickCooldown--;

        if (timeRemaining == 100) {
            spin();
        }

        if (timeRemaining > 0) {
            timeRemaining--;
        }

        return timeRemaining;
    }

    public void despawn() {
        for (BlockDisplay entity : entities) {
            entity.remove();
        }
        blocks.clear();
        state = WallState.HIDDEN;
    }

    public void spin() {
        setTeleportDuration(1);
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                for (BlockDisplay display : blocks) {
                    Location loc = display.getLocation();
                    loc.setYaw(loc.getYaw() + 10);
                    display.teleport(loc);
                }
                i++;
                if (i >= 18) {
                    setTeleportDuration(50);
                    cancel();
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    /* SCORING */

    public boolean hasHole(Pair<Integer, Integer> hole) {
        for (Pair<Integer, Integer> h : holes) {
            if (h.equals(hole)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a set of coordinates where blocks are missing.
     */
    public Map<Pair<Integer, Integer>, Block> getMissingBlocks(PlayingField field) {
        HashMap<Pair<Integer, Integer>, Block> missingBlocks = new HashMap<>();
        Map<Pair<Integer, Integer>, Block> playingFieldBlocks = field.getPlayingFieldBlocks();

        for (Pair<Integer, Integer> hole : holes) {
            if (!playingFieldBlocks.containsKey(hole)) {
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

    /* HOLE MANIPULATION */

    @SafeVarargs
    public final void insertHoles(Pair<Integer, Integer>... holes) {
        for (Pair<Integer, Integer> hole : holes) {
            if (hole == null || this.holes.contains(hole)) continue;
            insertHole(hole);
        }
    }

    public void insertHole(Pair<Integer, Integer> hole) {
        if (hole == null || this.holes.contains(hole)) return;
        // out of bounds check
        if (hole.getValue0() < 0 || hole.getValue0() >= length || hole.getValue1() < 0 || hole.getValue1() >= height) {
            return;
        }
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

    public Pair<Integer, Integer> randomCoordinates() {
        return Pair.with((int) (Math.random() * length), (int) (Math.random() * height));
    }

    public Pair<Integer, Integer> randomCoordinatesConnected() {
        int attempts = 0;
        while (attempts < 10) {
            // Choose a random hole in the provided wall
            Pair<Integer, Integer> existingHole = randomHole();
            if (existingHole == null) {
                return randomCoordinates();
            }
            // Choose a random direction to spread the hole to
            Random random = new Random();
            int x = random.nextInt(-1, 2);
            int y = random.nextInt(-1, 2);
            Pair<Integer, Integer> newHole = Pair.with(existingHole.getValue0() + x, existingHole.getValue1() + y);
            // If the new hole is in bounds and is not already a hole, return it
            if (newHole.getValue0() >= 0 && newHole.getValue0() < length && newHole.getValue1() >= 0 && newHole.getValue1() < height
                    && !hasHole(newHole)) {
                return newHole;
            } else {
                attempts++;
            }
        }
        return null;
    }

    /**
     * Generates holes on the wall.
     * The algorithm works as follows:
     * Generate x random holes on the wall.
     * Generate y holes that are connected to existing holes, either horizontally, vertically, or diagonally.
     * @param random Number of random holes to generate
     * @param cluster Number of connected holes to generate. This can be random as well with the next argument.
     * @param randomizeFurther Whether to instead randomize the number of connected holes and use the provided
     *                         cluster argument as the maximum number of connected holes to generate.
     */
    public void generateHoles(int random, int cluster, boolean randomizeFurther) {
        for (int i = 0; i < random; i++) {
            Pair<Integer, Integer> hole = randomCoordinates();
            insertHole(hole);
        }

        if (randomizeFurther) {
            Random rng = new Random();
            cluster = rng.nextInt(0, cluster + 1);
        }
        for (int i = 0; i < cluster; i++) {
            Pair<Integer, Integer> hole = (randomCoordinatesConnected());
            if (hole != null) {
                insertHole(hole);
            }
        }
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public Material getMaterial() {
        return material;
    }

    /**
     * Returns an un-summoned copy of this wall and the holes in it.
     * @return A new wall with the same holes as this one.
     */
    public Wall copy() {
        Wall newWall = new Wall(length, height);
        for (Pair<Integer, Integer> hole : getHoles()) {
            newWall.insertHole(hole);
        }
        return newWall;
    }

    @VisibleForTesting
    public void setTeleportDuration(int ticks) {
        teleportDuration = ticks;
        for (BlockDisplay display : entities) {
            display.setTeleportDuration(ticks);
        }
        tickCooldown = 0;
    }

    // Snap the wall to where it should be at this instant.
    public void correct() {
        int lastTeleportDuration = teleportDuration;
        setTeleportDuration(0);
        for (BlockDisplay display : entities) {
            Location target = display.getLocation()
                    .subtract(movementDirection.clone().multiply(length * (maxTime - teleportTo) / (double) maxTime))
                    .add(movementDirection.clone().multiply(length * (maxTime - timeRemaining) / (double) maxTime));
            display.teleport(target);
        }
        setTeleportDuration(lastTeleportDuration);
    }
}
