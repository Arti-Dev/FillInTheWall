package com.articreep.holeinthewall.utils;

import com.articreep.holeinthewall.HoleInTheWall;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.Objects;

public class WorldBoundingBox {
    private final World world;
    private BoundingBox boundingBox;

    public WorldBoundingBox(World world, double x1, double y1, double z1, double x2, double y2, double z2) {
        this.boundingBox = new BoundingBox(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        this.world = world;
    }

    public WorldBoundingBox(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            throw new IllegalArgumentException("Locations must be in the same world");
        }
        this.world = loc1.getWorld();
        this.boundingBox = locationsToBoundingBox(loc1, loc2);

        new BukkitRunnable() {

            @Override
            public void run() {
                world.spawnParticle(Particle.HEART, loc1, 1);
                world.spawnParticle(Particle.HEART, loc2, 1);
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 20);
    }

    public boolean isinBoundingBox(Location loc) {
        return Objects.equals(loc.getWorld(), this.world) && boundingBox.contains(loc.toVector());
    }

    public static BoundingBox locationsToBoundingBox(Location corner1, Location corner2) {
        return new BoundingBox(
                Math.min(corner1.getX(), corner2.getX()),
                Math.min(corner1.getY(), corner2.getY()),
                Math.min(corner1.getZ(), corner2.getZ()),
                Math.max(corner1.getX(), corner2.getX()),
                Math.max(corner1.getY(), corner2.getY()),
                Math.max(corner1.getZ(), corner2.getZ())
        );
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public World getWorld() {
        return world;
    }
}
