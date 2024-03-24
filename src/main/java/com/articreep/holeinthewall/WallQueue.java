package com.articreep.holeinthewall;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class WallQueue {

    /**
     * This is in ticks.
     */
    private final int timeToFill = 80;
    private BukkitTask task = null;
    private PlayingField field = null;
    // todo this number is arbitrary
    private final int length = 20;

    /**
     * Walls that are spawned but are invisible.
     */
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
        wall.spawnWall(field, this, field.getPlayer());
        wall.setTimeRemaining(timeToFill);
    }

    public void animateNextWall() {
        if (animatingWall != null) return;
        if (hiddenWalls.isEmpty()) return;
        animatingWall = hiddenWalls.remove(0);
        animatingWall.animateWall(this, field.getPlayer());
        new BukkitRunnable() {
            @Override
            public void run() {
                // fix possible race condition: only add to visible walls if the wall has spawned
                // (finished spawn animation)
                // todo this task/conditional might not be necessary
                if (animatingWall.hasSpawned()) {
                    visibleWalls.add(animatingWall);
                    animatingWall = null;
                    this.cancel();
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 10, 1);
    }

    public BukkitTask tickLoop() {
        return new BukkitRunnable() {
            int pause = 0;
            @Override
            public void run() {
                if (pause > 0) {
                    pause--;
                    return;
                }

                // Animate the next wall when possible
                if (visibleWalls.isEmpty() && !hiddenWalls.isEmpty()) {
                    animateNextWall();
                }

                // Tick all visible walls
                Iterator<Wall> it = visibleWalls.iterator();
                while (it.hasNext()) {
                    Wall wall = it.next();
                    int remaining = wall.tick(WallQueue.this);
                    if (remaining <= 0 && wall.hasSpawned()) {
                        wall.despawn();
                        field.matchAndScore(wall);
                        it.remove();
                        pause = 10;
                    } else if (!wall.hasSpawned()) {
                        Bukkit.broadcastMessage(ChatColor.RED + "Attempted to tick wall before spawned..");
                        this.cancel();
                    }
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    /**
     * Insta-sends the current wall to the playing field for matching.
     */
    public void instantSend() {
        // todo closest wall will ALWAYS be the first element for now.
        Wall wall = visibleWalls.get(0);
        wall.setTimeRemaining(0);
        field.matchAndScore(wall);
        visibleWalls.remove(wall);
        wall.despawn();
    }

    public int getLength() {
        return length;
    }

    public PlayingField getField() {
        return field;
    }
}
