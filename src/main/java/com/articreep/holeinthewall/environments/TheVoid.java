package com.articreep.holeinthewall.environments;

import com.articreep.holeinthewall.HoleInTheWall;
import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.utils.WorldBoundingBox;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

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
        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(),
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
                    // todo we could follow the player.. should experiment
                    player.spawnParticle(particle, location.clone().add(vector.clone().multiply(x)).add(0, y, 0),
                            1, 0.1, 0, 0.1, 0);
                }

                t += tIncrement;
                if (t >= tMax) {
                    cancel();
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }
    // todo this is hard-coded to display on the x-y plane only as of right now
    private static void regularPolygon(int sides, int radius, Location location, Color dustColor) {
        if (sides < 3) return;

        List<Location> locations = new ArrayList<>();
        for (int d = 0; d < 360; d += 360/sides) {
            Location corner = location.clone().add(radius * Math.cos(Math.toRadians(d)), radius * Math.sin(Math.toRadians(d)), 0);
            locations.add(corner);
        }
        connectLocations(locations, dustColor);
    }

    private static void animateRegularPetals(int petals, int radius, Location location, Color dustColor) {
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

        // todo each will take 20 ticks to draw. subject to change
        new BukkitRunnable() {
            int theta = 0;
            @Override
            public void run() {
                petalPolarEquation(radius, location, Color.WHITE, coeff, theta, 1.5F);
                theta += 9 * thetaMultplier;
                if (theta >= 180 * thetaMultplier) {
                    regularPetals(petals, radius, location, dustColor);
                    this.cancel();
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    private static void regularPetals(int petals, int radius, Location location, Color dustColor) {
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
            petalPolarEquation(radius, location, dustColor, coeff, theta, 1.5F);
        }
    }

    private static void petalPolarEquation(int radius, Location location, Color dustColor, int coeff, int theta, float size) {
        double r = radius * Math.sin(coeff * Math.toRadians(theta));
        location.getWorld().spawnParticle(Particle.DUST, location.clone().add(
                r * Math.cos(Math.toRadians(theta)), r * Math.sin(Math.toRadians(theta)), 0),
                1, new Particle.DustOptions(dustColor, size));
    }

    public static void randomShape(PlayingField field) {
        Location location = field.getEffectBox().randomLocation();
        Random random = new Random();
        int shape = random.nextInt(3,8);
        int radius = random.nextInt(1, 5);
        Color dustColor = Color.fromRGB(random.nextInt(255), random.nextInt(255), random.nextInt(255));
        regularPolygon(shape, radius, location, dustColor);
    }

    public static void randomPetal(PlayingField field) {
        Location location = field.getEffectBox().randomLocation();
        Random random = new Random();
        int petals = random.nextInt(3, 8);
        int radius = random.nextInt(1, 5);
        Color dustColor = Color.fromRGB(random.nextInt(255), random.nextInt(255), random.nextInt(255));
        animateRegularPetals(petals, radius, location, dustColor);
    }

    private static final List<Material> possibleFallingBlocks = new ArrayList<>(Arrays.asList(
            Material.STONE, Material.SUSPICIOUS_SAND, Material.CHERRY_LEAVES, Material.SHROOMLIGHT, Material.SNOW_BLOCK,
            Material.SMOOTH_STONE, Material.POLISHED_ANDESITE, Material.POLISHED_DIORITE, Material.POLISHED_GRANITE));
    public static void randomFallingBlockDisplay(PlayingField field) {
        Random random = new Random();
        // arbitrarily add 10 blocks of height haha
        Location location = field.getEffectBox().randomLocation().add(0, 10, 0);
        Material material = possibleFallingBlocks.get(random.nextInt(possibleFallingBlocks.size()));
        BlockDisplay display = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        display.setBlock(material.createBlockData());
        // Random orientation
        display.setRotation(random.nextInt(0, 360), random.nextInt(-90, 90));

        new BukkitRunnable() {
            double velo = 0;
            // acceleration is blocks per second^2
            final double accel = -0.5;
            @Override
            public void run() {
                display.setVelocity(new Vector(0, velo, 0));
                velo += accel/20;
                if (display.getLocation().getY() < -64) {
                    display.remove();
                    cancel();
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    public static void connectLocations(Location loc1, Location loc2, int amount, Color dustColor) {
        loc1 = loc1.clone();
        loc2 = loc2.clone();
        Vector v = loc2.toVector().subtract(loc1.toVector());
        v.multiply(1.0 / (amount - 1));
        for (int i = 0; i < amount; i++) {
            loc1.getWorld().spawnParticle(Particle.DUST, loc1, 1, new Particle.DustOptions(dustColor, 0.7F));
            loc1.add(v);
        }
    }

    public static void connectLocations(List<Location> locations, Color dustColor) {
        for (int i = 0; i < locations.size() - 1; i++) {
            connectLocations(locations.get(i), locations.get(i + 1), 30, dustColor);
        }
        connectLocations(locations.getLast(), locations.getFirst(), 30, dustColor);
    }
}
