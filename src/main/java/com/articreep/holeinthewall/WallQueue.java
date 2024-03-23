package com.articreep.holeinthewall;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.javatuples.Pair;

import java.util.*;

public class WallQueue {

    /**
     * This is in ticks.
     */
    private final int timeToFill = 200;
    private BukkitTask task = null;
    private PlayingField field = null;
    // todo this number is arbitrary
    private int length = 20;

    private List<Wall> hiddenWalls;
    private Wall animatingWall;
    private List<Wall> visibleWalls;
    private boolean allowMultipleWalls = false;

    public WallQueue(PlayingField field) {
        hiddenWalls = new LinkedList<>();
        animatingWall = null;
        visibleWalls = new ArrayList<>();
        this.field = field;
        task = tickLoop();
    }

    public void addWall(Wall wall) {
        hiddenWalls.add(wall);
        Bukkit.broadcastMessage("Added wall to queue");
    }

    public void animateNextWall() {
        if (animatingWall != null) return;
        if (hiddenWalls.isEmpty()) return;
        animatingWall = hiddenWalls.remove(0);
        // todo animate wall
        new BukkitRunnable() {
            @Override
            public void run() {
                visibleWalls.add(animatingWall);
                animatingWall.setTimeRemaining(timeToFill);
                animatingWall.spawnWall(field, WallQueue.this);
                Bukkit.broadcastMessage("Wall is now visible");
                animatingWall = null;
            }
        }.runTaskLater(HoleInTheWall.getInstance(), 20);
    }

    public BukkitTask tickLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (visibleWalls.isEmpty() && !hiddenWalls.isEmpty()) {
                    animateNextWall();
                    Bukkit.broadcastMessage("Animating next wall");
                }
                for (Wall wall : visibleWalls) {
                    if (wall.tick(WallQueue.this) <= 0) {
                        Bukkit.broadcastMessage("Wall reached the end");
                        field.matchAndScore(wall);
                        visibleWalls.remove(wall);
                    }
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    /**
     * Insta-sends the current wall to the playing field for matching.
     * @return the score
     */
    public void instantSend() {
        // todo closest wall will ALWAYS be the first element for now.
        Wall wall = visibleWalls.get(0);
        wall.setTimeRemaining(0);
        field.matchAndScore(wall);
        visibleWalls.remove(wall);
    }

    public int getLength() {
        return length;
    }



}
