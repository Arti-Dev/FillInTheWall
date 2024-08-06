package com.articreep.fillinthewall.environments;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.Judgement;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.modifiers.Rush;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class TheVoid implements Listener {

    private static final Set<Player> puddleCooldowns = new HashSet<>();

    @EventHandler
    public static void puddleParticles(PlayerMoveEvent event) {
        if (puddleCooldowns.contains(event.getPlayer())) {
            return;
        }
        Player player = event.getPlayer();
        Location location = player.getLocation();
        if (location.clone().add(0, -0.05, 0).getBlock().getType() != Material.BARRIER) return;

        // x = 0.5t-0.5sin(t/2) y = 0.25 - 0.25cos(t)
        // fan out in 360 degrees, 10 degree increments

        puddleCooldowns.add(player);
        Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(),
                () -> puddleCooldowns.remove(player), 20 * 2);

        Particle particle = Particle.DUST_PLUME;

        new BukkitRunnable() {
            double t = Math.PI / 3;
            double tMax = Math.PI * 2;
            double tIncrement = Math.PI / 3;

            @Override
            public void run() {
                for (int i = 0; i < 360; i += 10) {
                    Vector vector = new Vector(1, 0, 0);
                    vector.rotateAroundY(i);
                    double x = 0.5 * t - 0.5 * Math.sin(t / 2);
                    double y = -0.125 + 0.125 * Math.cos(t);
                    // spawn particle at x, y
                    player.getWorld().spawnParticle(particle, location.clone().add(vector.clone().multiply(x)).add(0, y, 0),
                            1, 0.1, 0, 0.1, 0);
                }

                t += tIncrement;
                if (t >= tMax) {
                    cancel();
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    private static void regularPolygon(int sides, int radius, Location location, Vector xVector, Color dustColor) {
        if (sides < 3) return;

        List<Location> locations = new ArrayList<>();
        for (int d = 0; d < 360; d += 360/sides) {
            Location corner = location.clone()
                    .add(xVector.clone().multiply(radius * Math.cos(Math.toRadians(d))))
                    .add(0, radius * Math.sin(Math.toRadians(d)), 0);
            locations.add(corner);
        }
        connectLocationsWithParticles(locations, dustColor);
    }

    private static void animateRegularPetals(int petals, int radius, Location location, Vector xVector, Color dustColor) {
        if (petals < 3) return;
        int coeff;
        int thetaMultplier;
        if (petals % 2 == 0) {
            coeff = petals / 2;
            thetaMultplier = 2;
        } else {
            coeff = petals;
            thetaMultplier = 1;
        }

        // with this system, each petal takes 20 ticks to draw
        new BukkitRunnable() {
            int theta = 0;
            @Override
            public void run() {
                spawnParticleAlongPetal(radius, location, xVector, Color.WHITE, coeff, theta, 1.5f);
                theta += 9 * thetaMultplier;
                if (theta >= 180 * thetaMultplier) {
                    regularPetals(petals, radius, location, xVector, dustColor);
                    this.cancel();
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    private static void regularPetals(int petals, int radius, Location location, Vector xVector, Color dustColor) {
        if (petals < 3) return;
        int coeff;
        int thetaMultplier;
        if (petals % 2 == 0) {
            coeff = petals / 2;
            thetaMultplier = 2;
        } else {
            coeff = petals;
            thetaMultplier = 1;
        }
        for (int theta = 0; theta < 180 * thetaMultplier; theta++) {
            spawnParticleAlongPetal(radius, location, xVector, dustColor, coeff, theta, 1.5f);
        }
    }

    private static void spawnParticleAlongPetal(int radius, Location location, Vector xVector, Color dustColor, int coeff, int theta, float size) {
        double r = radius * Math.sin(coeff * Math.toRadians(theta));
        Location spawnLocation = location.clone()
                .add(xVector.clone().multiply(r * Math.cos(Math.toRadians(theta))))
                .add(0, r * Math.sin(Math.toRadians(theta)), 0);
        location.getWorld().spawnParticle(Particle.DUST, spawnLocation,
                1, new Particle.DustOptions(dustColor, size));
    }

    public static void randomShape(PlayingField field) {
        Location location = field.getEffectBox().randomLocation();
        Random random = new Random();
        int shape = random.nextInt(3,8);
        int radius = random.nextInt(1, 5);
        Color dustColor = Color.fromRGB(random.nextInt(255), random.nextInt(255), random.nextInt(255));
        regularPolygon(shape, radius, location, field.getFieldDirection(), dustColor);
    }

    public static void randomPetal(PlayingField field) {
        Location location = field.getEffectBox().randomLocation();
        Random random = new Random();
        int petals = random.nextInt(3, 8);
        int radius = random.nextInt(1, 5);
        Color dustColor = Color.fromRGB(random.nextInt(255), random.nextInt(255), random.nextInt(255));
        animateRegularPetals(petals, radius, location, field.getFieldDirection(), dustColor);
    }

    // VOID_SONIC_BOOM
    public static void randomSonicBoomLine(PlayingField field) {
        // Go to a random location on the queue
        Random random = new Random();
        Location location = field.getReferencePoint()
                .add(field.getIncomingDirection().multiply(-1 * random.nextInt(field.getLength() - 1))).add(0, 1, 0);

        int length = field.getLength();
        Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
            for (int i = 0; i < length; i++) {
                location.getWorld().spawnParticle(Particle.SONIC_BOOM, location, 1, 0, 0, 0, 0);
                location.add(field.getFieldDirection());
            }
        }, field.getClearDelay());

    }

    private static final List<Material> possibleFallingBlocks = new ArrayList<>(Arrays.asList(
            Material.STONE, Material.SUSPICIOUS_SAND, Material.CHERRY_LEAVES, Material.SHROOMLIGHT, Material.SNOW_BLOCK,
            Material.SMOOTH_STONE, Material.POLISHED_ANDESITE, Material.POLISHED_DIORITE, Material.POLISHED_GRANITE,
            Material.COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.BEDROCK));
    // VOID_BLOCK_FALLING
    public static void randomFallingBlockDisplay(PlayingField field) {
        Random random = new Random();
        // arbitrarily add 10 blocks of height haha
        Location location = field.getEffectBox().randomLocation().add(0, 10, 0);
        Material material = possibleFallingBlocks.get(random.nextInt(possibleFallingBlocks.size()));
        BlockDisplay display = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        display.setBlock(material.createBlockData());
        display.setTeleportDuration(1);
        // Random orientation
        display.setRotation(random.nextInt(0, 360), random.nextInt(-90, 90));

        new BukkitRunnable() {
            double velo = 0;
            // acceleration is blocks per second^2
            final double accel = -0.5;
            @Override
            public void run() {
                velo += accel/20;
                display.teleport(display.getLocation().add(0, velo, 0));
                if (display.getLocation().getY() < -64) {
                    display.remove();
                    cancel();
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    // VOID_VERTICAL_LINES
    public static void animateVerticalLines(PlayingField field, double height, int amount) {
        Location location = field.getReferencePoint();
        location.add(field.getIncomingDirection().multiply(0.5));
        location.add(field.getFieldDirection().multiply(-0.5));
        location.add(0, -0.5, 0);
        int across = field.getLength();
        int queue_length = field.getQueue().getFullLength();
        Vector fieldDirection = field.getFieldDirection();
        // The direction the vertical lines will be moving in (away from the board)
        Vector movingDirection = field.getIncomingDirection().multiply(-1);

        new BukkitRunnable() {
            double offset = 0;
            @Override
            public void run() {
                offset += (double) queue_length / amount;
                if (offset >= queue_length) {
                    cancel();
                }
                Location bottomLeft = location.clone().add(movingDirection.clone().multiply(offset));
                connectLocationsWithParticles(bottomLeft, bottomLeft.clone().add(0, height, 0), 30, Particle.END_ROD);

                Location bottomRight = bottomLeft.clone().add(fieldDirection.clone().multiply(across));
                connectLocationsWithParticles(bottomRight, bottomRight.clone().add(0, height, 0), 30, Particle.END_ROD);
            }
        }.runTaskTimer(FillInTheWall.getInstance(), field.getClearDelay(), 5);
    }

    // VOID_BIG_PUDDLE
    public static void bigPuddle(PlayingField field) {
        Location location = field.getReferencePoint()
                .add(field.getFieldDirection().multiply((double) field.getLength() / 2))
                .add(0, -1.5, 0);

        Particle particle = Particle.DUST_PLUME;

        new BukkitRunnable() {
            double r = 0;
            final double rIncrement = 0.4;
            final double rMax = 15;

            @Override
            public void run() {
                for (int i = 0; i < 360; i += 2) {
                    Vector vector = new Vector(1, 0, 0);
                    vector.rotateAroundY(i);
                    location.getWorld().spawnParticle(particle, location.clone().add(vector.clone().multiply(r)),
                            2, 0.1, 0, 0.1, 0);
                }

                r += rIncrement;
                if (r >= rMax) {
                    cancel();
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    public static void spawnRotatingBlocks(PlayingField field, Rush rush) {
        // todo this is a mess
        final float size = 1.5F;
        Location leftLocation = field.getReferencePoint()
                .subtract(field.getFieldDirection().multiply(4))
                .subtract(field.getIncomingDirection().multiply(field.getQueue().getFullLength() / 2));
        Location rightLocation = field.getReferencePoint()
                .add(field.getFieldDirection().multiply(field.getLength() + 3))
                .subtract(field.getIncomingDirection().multiply(field.getQueue().getFullLength() / 2));
        BlockDisplay leftDisplay = (BlockDisplay) leftLocation.getWorld().spawnEntity(leftLocation, EntityType.BLOCK_DISPLAY);
        BlockDisplay rightDisplay = (BlockDisplay) rightLocation.getWorld().spawnEntity(rightLocation, EntityType.BLOCK_DISPLAY);

        List<BlockDisplay> displays = new ArrayList<>(Arrays.asList(leftDisplay, rightDisplay));
        for (BlockDisplay display : displays) {
            display.setGlowing(true);
            display.setBlock(Material.END_STONE.createBlockData());
            display.setTeleportDuration(1);
            display.setTransformation(new Transformation(
                    new Vector3f(-size/2, -size/2, -size/2),
                    new AxisAngle4f(0, 0, 0, 1), new Vector3f(size, size, size),
                    new AxisAngle4f(0, 0, 0, 1)));
        }

        new BukkitRunnable() {

            @Override
            public void run() {
                float oldYaw = leftDisplay.getLocation().getYaw();
                Location leftNewLocation = leftDisplay.getLocation();
                Location rightNewLocation = rightDisplay.getLocation();
                leftNewLocation.setYaw(oldYaw + rush.getBoardsCleared());
                rightNewLocation.setYaw(oldYaw + rush.getBoardsCleared());
                leftDisplay.teleport(leftNewLocation);
                rightDisplay.teleport(rightNewLocation);
                if (!rush.isActive()) {
                    leftDisplay.remove();
                    rightDisplay.remove();
                    cancel();
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    public static void adjustTime(PlayingField field, Rush rush) {
        World world = field.getReferencePoint().getWorld();
        world.setTime(22000 + rush.getBoardsCleared() * 200L);
    }

    public static void resetTime(PlayingField field) {
        World world = field.getReferencePoint().getWorld();
        long initialTime = world.getTime();
        if (initialTime < 18000) initialTime += 24000;

        long finalInitialTime = initialTime;
        new BukkitRunnable() {
            long currentTime = finalInitialTime;
            @Override
            public void run() {
                currentTime -= 50;
                world.setTime(currentTime);
                if (currentTime <= 18000) {
                    world.setTime(18000);
                    cancel();
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    public static void connectLocationsWithParticles(Location loc1, Location loc2, int amount, Color dustColor) {
        if (amount < 2) return;
        loc1 = loc1.clone();
        loc2 = loc2.clone();
        Vector v = loc2.toVector().subtract(loc1.toVector());
        v.multiply(1.0 / (amount - 1));
        for (int i = 0; i < amount; i++) {
            loc1.getWorld().spawnParticle(Particle.DUST, loc1, 1, new Particle.DustOptions(dustColor, 0.7F));
            loc1.add(v);
        }
    }

    public static void connectLocationsWithParticles(List<Location> locations, Color dustColor) {
        for (int i = 0; i < locations.size() - 1; i++) {
            connectLocationsWithParticles(locations.get(i), locations.get(i + 1), 30, dustColor);
        }
        connectLocationsWithParticles(locations.getLast(), locations.getFirst(), 30, dustColor);
    }

    public static void connectLocationsWithParticles(Location loc1, Location loc2, int amount, Particle particle) {
        loc1 = loc1.clone();
        loc2 = loc2.clone();
        Vector v = loc2.toVector().subtract(loc1.toVector());
        v.multiply(1.0 / (amount - 1));
        for (int i = 0; i < amount; i++) {
            loc1.getWorld().spawnParticle(particle, loc1, 1, 0, 0, 0, 0);
            loc1.add(v);
        }
    }

    public static void judgementEffect(PlayingField field, Judgement judgement) {
        Random random = new Random();
        // 20% chance of nothing happening
        if (random.nextDouble() > 0.8) return;
        List<EnvironmentEffect> effects = new ArrayList<>();
        switch (judgement) {
            case PERFECT: effects.add(EnvironmentEffect.VOID_VERTICAL_LINES);
            effects.add(EnvironmentEffect.VOID_BIG_PUDDLE);
            case COOL: effects.add(EnvironmentEffect.VOID_BLOCK_FALLING);
            effects.add(EnvironmentEffect.VOID_SONIC_BOOM);
        }
        if (effects.isEmpty()) return;
        switch (effects.get(random.nextInt(effects.size()))) {
            case VOID_BLOCK_FALLING:
                for (int i = 0; i < 3; i++) randomFallingBlockDisplay(field);
                break;
            case VOID_SONIC_BOOM:
                randomSonicBoomLine(field);
                break;
            case VOID_VERTICAL_LINES:
                animateVerticalLines(field, 6, 10);
                break;
            case VOID_BIG_PUDDLE:
                bigPuddle(field);
                break;
        }
    }
}
