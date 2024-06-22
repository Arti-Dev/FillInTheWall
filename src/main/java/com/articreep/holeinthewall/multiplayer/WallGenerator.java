package com.articreep.holeinthewall.multiplayer;

import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.Wall;
import com.articreep.holeinthewall.WallQueue;
import org.bukkit.Bukkit;
import org.javatuples.Pair;

import java.util.HashSet;
import java.util.Set;

/**
 * Generates walls to feed into WallQueues.
 */
public class WallGenerator {
    private final Set<WallQueue> queues = new HashSet<>();

    // Settings
    private int wallLength;
    private int wallHeight;

    private int randomHoleCount;
    private int connectedHoleCount;
    private boolean randomizeFurther = true;
    private int wallActiveTime;

    public WallGenerator(int length, int height, int startingRandomHoleCount, int startingConnectedHoleCount, int wallActiveTime) {
        // todo can customize difficulty increases as well
        this.wallLength = length;
        this.wallHeight = height;
        this.randomHoleCount = startingRandomHoleCount;
        this.connectedHoleCount = startingConnectedHoleCount;
        this.wallActiveTime = wallActiveTime;
    }

    /**
     * Call this method whenever a queue runs out of walls.
     */
    public void addNewWallToQueues() {
        Wall wall = new Wall(wallLength, wallHeight);
        wall.generateHoles(randomHoleCount, connectedHoleCount, randomizeFurther);
        wall.setTimeRemaining(wallActiveTime);
        for (WallQueue queue : queues) {
            queue.addWall(wall.copy());
        }
    }

    public void addQueue(WallQueue queue) {
        queues.add(queue);
    }

    public void removeQueue(WallQueue queue) {
        queues.remove(queue);
    }

    public void setRandomHoleCount(int randomHoleCount) {
        this.randomHoleCount = randomHoleCount;
    }

    public void setWallActiveTime(int wallActiveTime) {
        this.wallActiveTime = wallActiveTime;
    }

    public void setConnectedHoleCount(int connectedHoleCount) {
        this.connectedHoleCount = connectedHoleCount;
    }

    public void setRandomizeFurther(boolean randomizeFurther) {
        this.randomizeFurther = randomizeFurther;
    }

    public int getLength() {
        return wallLength;
    }


    public int getHeight() {
        return wallHeight;
    }
}
