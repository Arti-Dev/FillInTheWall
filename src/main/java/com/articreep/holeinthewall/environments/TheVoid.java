package com.articreep.holeinthewall.environments;

import com.articreep.holeinthewall.HoleInTheWall;
import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.utils.WorldBoundingBox;
import org.bukkit.*;
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

    private static void regularPolygon(int sides, int radius, Location location, Color dustColor) {
        if (sides <= 0) return;

        List<Location> locations = new ArrayList<>();
        for (int d = 0; d < 360; d += 360/sides) {
            Location corner = location.clone().add(radius * Math.cos(Math.toRadians(d)), radius * Math.sin(Math.toRadians(d)), 0);
            locations.add(corner);
        }
        connectLocations(locations, dustColor);
    }

    public static void randomShape(PlayingField field) {
        Location location = field.getEffectBox().randomLocation();
        Random random = new Random();
        int shape = random.nextInt(3,8);
        int radius = random.nextInt(1, 5);
        Color dustColor = Color.fromRGB(random.nextInt(255), random.nextInt(255), random.nextInt(255));
        regularPolygon(shape, radius, location, dustColor);

    }

    public static void connectLocations(Location loc1, Location loc2, int amount, Color dustColor) {
        loc1 = loc1.clone();
        loc2 = loc2.clone();
        Vector v = loc2.toVector().subtract(loc1.toVector());
        v.multiply(1.0 / (amount - 1));
        for (int i = 0; i < amount; i++) {
            loc1.getWorld().spawnParticle(Particle.DUST, loc1, 1, new Particle.DustOptions(Color.RED, 0.7F));
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
