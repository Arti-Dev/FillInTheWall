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

    public WorldBoundingBox(Location loc1, Location loc2, Particle particle) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            throw new IllegalArgumentException("Locations must be in the same world");
        }
        this.world = loc1.getWorld();
        this.boundingBox = locationsToBoundingBox(loc1, loc2);
        if (particle != null) {
            new BukkitRunnable() {

                @Override
                public void run() {
                    world.spawnParticle(particle, boundingBox.getMin().toLocation(world), 1);
                    world.spawnParticle(particle, boundingBox.getMax().toLocation(world), 1);
                }
            }.runTaskTimer(HoleInTheWall.getInstance(), 0, 20);
        }
    }

    public WorldBoundingBox(Location loc1, Location loc2) {
        this(loc1, loc2, null);
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

    public Location randomLocation() {
        double x = boundingBox.getMinX() + Math.random() * (boundingBox.getMaxX() - boundingBox.getMinX());
        double y = boundingBox.getMinY() + Math.random() * (boundingBox.getMaxY() - boundingBox.getMinY());
        double z = boundingBox.getMinZ() + Math.random() * (boundingBox.getMaxZ() - boundingBox.getMinZ());
        return new Location(world, x, y, z);
    }
}
