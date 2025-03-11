package com.articreep.fillinthewall.utils;

import com.articreep.fillinthewall.FillInTheWall;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.Objects;

public class WorldBoundingBox {
    private final World world;
    private final BoundingBox boundingBox;
    private BoundingBox exclusionBox;

    public WorldBoundingBox(Location loc1, Location loc2, Particle particle, Particle exclusionParticle) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            throw new IllegalArgumentException("Locations must be in the same world");
        }
        this.world = loc1.getWorld();
        this.boundingBox = locationsToBoundingBox(loc1, loc2);
        if (particle != null && exclusionParticle != null) {
            new BukkitRunnable() {

                @Override
                public void run() {
                    world.spawnParticle(particle, boundingBox.getMin().toLocation(world), 1);
                    world.spawnParticle(particle, boundingBox.getMax().toLocation(world), 1);
                    if (exclusionBox != null) {
                    world.spawnParticle(exclusionParticle, exclusionBox.getMin().toLocation(world), 1);
                    world.spawnParticle(exclusionParticle, exclusionBox.getMax().toLocation(world), 1);
                    }
                }
            }.runTaskTimer(FillInTheWall.getInstance(), 0, 20);
        }
    }

    public WorldBoundingBox(Location loc1, Location loc2) {
        this(loc1, loc2, null, null);
    }

    public boolean isinBoundingBox(Location loc) {
        return Objects.equals(loc.getWorld(), this.world) && boundingBox.contains(loc.toVector())
                && (exclusionBox == null || !exclusionBox.contains(loc.toVector()));
    }

    private static BoundingBox locationsToBoundingBox(Location corner1, Location corner2) {
        return new BoundingBox(
                Math.min(corner1.getX(), corner2.getX()),
                Math.min(corner1.getY(), corner2.getY()),
                Math.min(corner1.getZ(), corner2.getZ()),
                Math.max(corner1.getX(), corner2.getX()),
                Math.max(corner1.getY(), corner2.getY()),
                Math.max(corner1.getZ(), corner2.getZ())
        );
    }

    public void addExclusionBox(Location loc1, Location loc2) {
        exclusionBox = locationsToBoundingBox(loc1, loc2);
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public World getWorld() {
        return world;
    }

    // todo - this is with trial and error. a more mathematical approach should be used to directly calculate a point
    public Location randomLocation() {
        for (int i = 0; i < 10; i++) {
            double x = boundingBox.getMinX() + Math.random() * (boundingBox.getMaxX() - boundingBox.getMinX());
            double y = boundingBox.getMinY() + Math.random() * (boundingBox.getMaxY() - boundingBox.getMinY());
            double z = boundingBox.getMinZ() + Math.random() * (boundingBox.getMaxZ() - boundingBox.getMinZ());
            Location candidate = new Location(world, x, y, z);
            if (exclusionBox == null || !exclusionBox.contains(candidate.toVector())) {
                return candidate;
            }
        }
        return null;
    }

    public Location randomLocationIgnoreExclusion() {
        double x = boundingBox.getMinX() + Math.random() * (boundingBox.getMaxX() - boundingBox.getMinX());
        double y = boundingBox.getMinY() + Math.random() * (boundingBox.getMaxY() - boundingBox.getMinY());
        double z = boundingBox.getMinZ() + Math.random() * (boundingBox.getMaxZ() - boundingBox.getMinZ());
        return new Location(world, x, y, z);
    }
}
