package com.articreep.fillinthewall;

import com.articreep.fillinthewall.utils.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
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
    private String name;
    private final Set<Pair<Integer, Integer>> holes;
    private WallState state = WallState.HIDDEN;
    private boolean wasHardened = false;
    private int maxTime = -1;
    private int teleportTo = -1;
    private int timeRemaining = -1;
    private Vector movementDirection = null;
    private Vector horizontalDirection = null;
    private int distanceToTraverse = 0;
    // todo this reference entity sometimes will just be a hole so it won't move
    private BlockDisplay referenceEntity = null;
    private final Set<Display> entities = new HashSet<>();
    private final Map<BlockDisplay, Pair<Integer, Integer>> blocks = new HashMap<>();
    private final List<BlockDisplay> border = new ArrayList<>();
    private final List<BlockDisplay> toRemove = new ArrayList<>();
    private Material material = null;
    private int tickCooldown = 0;
    private boolean doSpin = false;
    private final int defaultTeleportDuration = 5;
    private int teleportDuration = defaultTeleportDuration;
    private boolean inverted = false;
    // todo hardness mechanic might be confusing in a vs match
    private int hardness = 0;

    // Stripe settings
    private boolean stripes = false;
    private Material altMaterial = null;

    public Wall(HashSet<Pair<Integer, Integer>> holes, int length, int height, String name) {
        this.holes = holes;
        this.length = length;
        this.height = height;
        this.name = name;
    }

    public Wall(HashSet<Pair<Integer, Integer>> holes, int length, int height) {
        this(holes, length, height, null);
    }

    public Wall(int length, int height) {
        this(new HashSet<>(), length, height, null);
    }

    public Wall(int length, int height, String name) {
        this(new HashSet<>(), length, height, name);
    }

    public Set<Pair<Integer, Integer>> getHoles() {
        return holes;
    }

    public int getLength() {
        return length;
    }

    public int getHeight() {
        return height;
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

    public void spawnWall(PlayingField field, WallQueue queue, WallState nextState, boolean hideBottomBorder) {
        if (state != WallState.HIDDEN) return;
        // go to the end of the queue
        // spawn block display entities
        // break the holes open
        // store all entities in a list here
        // interpolate towards the playing field
        Location spawnReferencePoint = field.getReferencePoint();
        movementDirection = field.getIncomingDirection();
        horizontalDirection = field.getFieldDirection();
        spawnReferencePoint.subtract(movementDirection.clone().multiply(queue.getEffectiveLength()));

        World world = spawnReferencePoint.getWorld();
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                Location loc = spawnReferencePoint.clone()
                        .add(field.getFieldDirection().multiply(x))
                        .add(0, y, 0);
                // spawn block display entity
                BlockDisplay display = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
                if (x == 0 && y == 0) referenceEntity = display;
                display.setTransformation(new Transformation(
                        new Vector3f(-0.5f, -0.5f, -0.5f),
                        new AxisAngle4f(0, 0, 0, 1), new Vector3f(1, 1, 1),
                        new AxisAngle4f(0, 0, 0, 1)));

                if (holes.contains(Pair.with(x, y))) {
                    if (nextState == WallState.HARDENED) {
                        display.remove();
                    } else {
                        toRemove.add(display);
                    }
                }
                blocks.put(display, Pair.with(x, y));
                entities.add(display);
            }
        }

        // borders

        Location centerOfWall = field.getCenter().subtract(movementDirection.clone().multiply(queue.getEffectiveLength()));

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

        // Name label (if added)
        if (name != null) {
            Location location = centerOfWall.clone().add(0, ((double) height/2)+0.5, 0);
            location.setDirection(movementDirection);
            TextDisplay nameDisplay = (TextDisplay) world.spawnEntity(location, EntityType.TEXT_DISPLAY);
            nameDisplay.setText(name);
            nameDisplay.setBillboard(Display.Billboard.HORIZONTAL);
            nameDisplay.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1), new Vector3f(2, 2, 2),
                    new AxisAngle4f(0, 0, 0, 1)));
            entities.add(nameDisplay);
        }

        for (Display display : entities) {
            display.setTeleportDuration(teleportDuration);
            display.setInterpolationDuration(1);
        }

        if (nextState == WallState.HARDENED) {
            for (BlockDisplay display : blocks.keySet()) {
                display.setBlock(Material.GRAY_WOOL.createBlockData());
            }
            for (BlockDisplay display : border) {
                display.setBlock(Material.STONE.createBlockData());
            }
            state = WallState.HARDENED;
            wasHardened = true;
        }

    }

    public void activateWall(Set<Player> players, Material defaultMaterial, Material defaultAltMaterial) {
        state = WallState.ANIMATING;
        // make them visible immediately
        if (this.material == null) {
            material = defaultMaterial;
        }
        if (this.altMaterial == null) {
            this.altMaterial = defaultAltMaterial;
        }
        if (stripes) {
            setStripes(true);
        } else {
            for (BlockDisplay display : blocks.keySet()) {
                display.setBlock(material.createBlockData());
            }
        }

        for (BlockDisplay display : border) {
            if (wasHardened) {
                // todo this border is barely visible. might want to indicate this some other way
                display.setBlock(Material.STONE.createBlockData());
            } else {
                display.setBlock(Material.IRON_BLOCK.createBlockData());
            }
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
        }.runTaskLater(FillInTheWall.getInstance(), 5);

        Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> state = WallState.VISIBLE, 10);
    }

    /** Teleports the board and returns time remaining. */
    public int tick(WallQueue queue) {
        if (state != WallState.VISIBLE) return -1;

        if (tickCooldown == 0) {
            correct();
            for (Display display : entities) {
                Location target = display.getLocation()
                        .add(movementDirection.clone().multiply(distanceToTraverse * teleportDuration / (double) maxTime));
                if (doSpin && display instanceof BlockDisplay && blocks.containsKey(display)) target.setYaw(target.getYaw() + 10);
                display.teleport(target);
            }
            // Note where we're teleporting to in case we need to correct the wall's location during interpolation
            teleportTo = timeRemaining - teleportDuration;
            // Reset tick cooldown
            tickCooldown = teleportDuration;
        }
        tickCooldown--;


        if (timeRemaining == 80) {
            spin();
        }

        if (timeRemaining > 0) {
            timeRemaining--;
        }

        return timeRemaining;
    }

    public void despawn() {
        for (Display entity : entities) {
            entity.remove();
        }
        blocks.clear();
        state = WallState.HIDDEN;
    }

    public void spin() {
        setTeleportDuration(1);
        doSpin = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                setTeleportDuration(defaultTeleportDuration);
                doSpin = false;
                cancel();
            }
        }.runTaskLater(FillInTheWall.getInstance(), 18);
    }

    /* SCORING */

    /**
     * Returns a set of coordinates where blocks are missing.
     */
    public Map<Pair<Integer, Integer>, Block> getMissingBlocks(PlayingField field) {
        HashMap<Pair<Integer, Integer>, Block> missingBlocks = new HashMap<>();
        Map<Pair<Integer, Integer>, Block> playingFieldBlocks = field.getPlayingFieldBlocks(false);

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
        Map<Pair<Integer, Integer>, Block> playingFieldBlocks = field.getPlayingFieldBlocks(false);

        for (Pair<Integer, Integer> blockCoords : playingFieldBlocks.keySet()) {
            if (!holes.contains(blockCoords)) {
                extraBlocks.put(blockCoords, playingFieldBlocks.get(blockCoords));
            }
        }
        return extraBlocks;
    }

    public Map<Pair<Integer, Integer>, Block> getCorrectBlocks(PlayingField field) {
        HashMap<Pair<Integer, Integer>, Block> correctBlocks = new HashMap<>();
        Map<Pair<Integer, Integer>, Block> playingFieldBlocks = field.getPlayingFieldBlocks(false);

        for (Pair<Integer, Integer> blockCoords : playingFieldBlocks.keySet()) {
            if (holes.contains(blockCoords)) {
                correctBlocks.put(blockCoords, playingFieldBlocks.get(blockCoords));
            }
        }
        return correctBlocks;
    }

    /* HOLE MANIPULATION */

    public boolean isInBounds(Pair<Integer, Integer> coords) {
        return coords.getValue0() >= 0 && coords.getValue0() < length
                && coords.getValue1() >= 0 && coords.getValue1() < height;
    }

    @SafeVarargs
    public final void insertHoles(Pair<Integer, Integer>... holes) {
        for (Pair<Integer, Integer> hole : holes) {
            if (hole == null || this.holes.contains(hole)) continue;
            insertHole(hole);
        }
    }

    public void invert() {
        HashSet<Pair<Integer, Integer>> newHoles = new HashSet<>();
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                Pair<Integer, Integer> hole = Pair.with(x, y);
                if (!holes.contains(hole)) {
                    newHoles.add(hole);
                }
            }
        }
        holes.clear();
        holes.addAll(newHoles);
        inverted = !inverted;
    }

    public void insertHole(Pair<Integer, Integer> hole) {
        if (hole == null || this.holes.contains(hole)) {
            if (hole != null) {
                Bukkit.getLogger().info("Hole already exists: (" + hole.getValue0() + ", " + hole.getValue1() + ")");
            }
            return;
        }
        // out of bounds check
        if (hole.getValue0() < 0 || hole.getValue0() >= length || hole.getValue1() < 0 || hole.getValue1() >= height) {
            Bukkit.getLogger().info("Hole is out of bounds: (" + hole.getValue0() + ", " + hole.getValue1() + ")");
            return;
        }
        this.holes.add(hole);
    }

    public void removeHole(Pair<Integer, Integer> hole) {
        holes.remove(hole);
    }

    public Pair<Integer, Integer> randomExistingHole() {
        if (holes.isEmpty()) return null;
        return Utils.randomSetElement(holes);
    }

    public void insertRandomNewHole(int count) {
        if (holes.size() >= length * height) return;
        Set<Pair<Integer, Integer>> possibleCoordinates = new HashSet<>();
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                possibleCoordinates.add(Pair.with(x, y));
            }
        }
        possibleCoordinates.removeAll(holes);
        for (int i = 0; i < count; i++) {
            if (possibleCoordinates.isEmpty()) {
                Bukkit.getLogger().info("Can't insert random hole");
                break;
            }
            Pair<Integer, Integer> hole = Utils.randomSetElement(possibleCoordinates);
            insertHole(hole);
            possibleCoordinates.remove(hole);
        }
    }

    public Pair<Integer, Integer> randomCoordinates() {
        return Pair.with((int) (Math.random() * length), (int) (Math.random() * height));
    }

    /**
     * Returns randomly generated coordinates directly near an existing hole that isn't already a hole.
     * Returns null if generation failed (wall is all holes)
     * @param diagonalsOK Whether diagonals are okay to return
     * @return The coordinates near a randomly selected existing hole
     */
    public Pair<Integer, Integer> randomCoordinatesConnected(boolean diagonalsOK) {
        if (holes.size() >= length * height) return null;
        Random random = new Random();
        // Dump all existing holes into an arraylist, and shuffle
        List<Pair<Integer, Integer>> holeList = new ArrayList<>();
        holeList.addAll(holes);
        Collections.shuffle(holeList);


        for (Pair<Integer, Integer> existingHole : holeList) {
            ArrayList<Pair<Integer, Integer>> possibleCoordinates = new ArrayList<>();

            // Find possible coordinates to choose
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    // If diagonals are not ok, filter out diagonals
                    if (!diagonalsOK && !((x != 0 && y == 0) || (x == 0 && y != 0))) {
                        continue;
                    }
                    Pair<Integer, Integer> newPair = Pair.with(existingHole.getValue0() + x, existingHole.getValue1() + y);
                    if (isInBounds(newPair) && !holes.contains(newPair)) {
                        possibleCoordinates.add(newPair);
                    }
                }
            }

            if (!possibleCoordinates.isEmpty()) {
                return possibleCoordinates.get(random.nextInt(possibleCoordinates.size()));
            }
        }
        Bukkit.getLogger().info("Failed to generate a connected hole");
        return null;
    }

    public Pair<Integer, Integer> randomCoordinatesConnected() {
        return randomCoordinatesConnected(true);
    }

    /**
     * Generates holes on the wall.
     * The algorithm works as follows:
     * Generate x randomCount holes on the wall.
     * Generate y holes that are connected to existing holes, either horizontally, vertically, or diagonally.
     * @param randomCount Number of random holes to generate
     * @param clusterCount Number of connected holes to generate. This can be random as well with the next argument.
     * @param randomizeFurther Whether to instead randomize the number of connected holes and use the given cluster
     *                         parameter as an upper bound.
     */
    public void generateHoles(int randomCount, int clusterCount, boolean randomizeFurther) {
        insertRandomNewHole(randomCount);

        if (randomizeFurther) {
            Random rng = new Random();
            clusterCount = rng.nextInt(0, clusterCount + 1);
        }
        for (int i = 0; i < clusterCount; i++) {
            Pair<Integer, Integer> hole = randomCoordinatesConnected();
            if (hole != null) {
                insertHole(hole);
            } else {
                break;
            }
        }
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public void setAltMaterial(Material material) {
        this.altMaterial = material;
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
        newWall.setTimeRemaining(maxTime);
        newWall.setName(name);
        return newWall;
    }

    public void setTeleportDuration(int ticks) {
        //correct();
        teleportDuration = ticks;
        for (Display display : entities) {
            display.setTeleportDuration(ticks);
        }
        tickCooldown = 0;
    }

    public void correct() {
        int lastTeleportDuration = teleportDuration;
        setTeleportDurationWithoutCorrection(0);
        for (Display display : entities) {
            Location target = display.getLocation()
                    // Go back to this entity's starting position
                    .subtract(movementDirection.clone().multiply(distanceToTraverse * (maxTime - teleportTo) / (double) maxTime))
                    // Add the distance it should have traveled by now
                    .add(movementDirection.clone().multiply(distanceToTraverse * (maxTime - timeRemaining) / (double) maxTime));
            display.teleport(target);
        }
        teleportTo = timeRemaining;
        setTeleportDurationWithoutCorrection(lastTeleportDuration);
    }

    // To prevent recursion when the correct() method is run after new teleport duration is set
    private void setTeleportDurationWithoutCorrection(int ticks) {
        teleportDuration = ticks;
        for (Display display : entities) {
            display.setTeleportDuration(ticks);
        }
        tickCooldown = 0;
      
    }
  
    public void frozenParticles() {
        World world = referenceEntity.getWorld();
        for (int i = 0; i < length; i++) {
            world.spawnParticle(Particle.SNOWFLAKE, referenceEntity.getLocation()
                            .add(horizontalDirection.clone().multiply(i))
                            .add(0, -0.5, 0),
                    5, 0.3, 0.3, 0.3, 0);
        }
    }

    public void setDistanceToTraverse(int distanceToTraverse) {
        this.distanceToTraverse = distanceToTraverse;
    }

    public void setHardness(int hardness) {
        this.hardness = hardness;
    }

    public int getHardness() {
        return hardness;
    }

    public void decreaseHardness(int amount) {
        hardness -= amount;
        if (hardness < 0) hardness = 0;
    }

    public boolean wasHardened() {
        return wasHardened;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static Wall parseWall(String string) {

        // Parse wall dimensions
        int xIndex = string.indexOf("x");
        int nameStart = string.indexOf("'") + 1;
        if (xIndex == -1) return null;

        int length = Integer.parseInt(string.substring(0, xIndex));
        int height = Integer.parseInt(string.substring(xIndex + 1, nameStart - 1));

        // Parse wall name
        int nameEnd = string.indexOf("'", nameStart);
        String name = string.substring(nameStart, nameEnd);

//        Bukkit.getLogger().info("Length: " + length);
//        Bukkit.getLogger().info("Height: " + height);
//        Bukkit.getLogger().info("Name: " + name);

        Wall wall = new Wall(length, height, name);
        int coordinateLength = Math.max((int) Math.log10(length) + 1, (int) Math.log10(height) + 1);

        for (int i = nameEnd + 1; i < string.length(); i += coordinateLength * 2) {
            int x = Integer.parseInt(string.substring(i, i + coordinateLength));
            int y = Integer.parseInt(string.substring(i + coordinateLength, i + coordinateLength * 2));
            wall.insertHole(Pair.with(x, y));
        }

        return wall;
    }

    public static Wall parseWall(String string, int length, int height, String name) {
        Wall wall = new Wall(length, height, name);

        int index = 0;
        while (index < string.length()) {
            // look for next comma
            int commaIndex = string.indexOf(",", index);
            if (commaIndex == -1) {
                break;
            }
            int x = Integer.parseInt(string.substring(index, commaIndex));

            index = commaIndex + 1;
            commaIndex = string.indexOf(",", index);
            if (commaIndex == -1) {
                commaIndex = string.length();
            }
            int y = Integer.parseInt(string.substring(index, commaIndex));
            wall.insertHole(Pair.with(x, y));
            index = commaIndex + 1;
        }

        return wall;
    }

    public boolean isInverted() {
        return inverted;
    }

    public boolean hasStripes() {
        return stripes;
    }

    public void setStripes(boolean stripes) {
        this.stripes = stripes;
        if (stripes) {
            for (BlockDisplay display : blocks.keySet()) {
                // todo hardcoded for now
                Pair<Integer, Integer> coordinates = blocks.get(display);
                if (coordinates.getValue1() % 2 == 0) {
                    display.setBlock(material.createBlockData());
                } else {
                    display.setBlock(altMaterial.createBlockData());
                }
            }
        } else {
            for (BlockDisplay display : blocks.keySet()) {
                display.setBlock(material.createBlockData());
            }
        }
    }

    public Material getAltMaterial() {
        return altMaterial;
    }
}
