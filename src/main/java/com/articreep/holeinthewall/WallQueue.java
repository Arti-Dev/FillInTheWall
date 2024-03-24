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
    private final int timeToFill = 160;
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
    private boolean rushEnabled = false;
    private Rush rush = null;
    private int pauseLoop = 0;

    public WallQueue(PlayingField field) {
        hiddenWalls = new LinkedList<>();
        animatingWall = null;
        visibleWalls = new ArrayList<>();
        this.field = field;
        this.field.setQueue(this);
        task = tickLoop();
    }

    public void addWall(Wall wall) {
        hiddenWalls.add(wall);
        wall.spawnWall(field, this, field.getPlayer());
        if (wall.getTimeRemaining() == -1) {
            // default wall speed
            wall.setTimeRemaining(timeToFill);
        }
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
                if (animatingWall == null) return;
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
            @Override
            public void run() {
                if (pauseLoop > 0) {
                    pauseLoop--;
                    return;
                }

                if (hiddenWalls.isEmpty() && !rushEnabled) {
                    // todo messy way to make a new wall but whatevs
                    Random random = new Random();
                    Wall newWall = new Wall();
                    newWall.generateHoles(2, random.nextInt(1,5));
                    addWall(newWall);
                }

                // Animate the next wall when possible
                if (visibleWalls.isEmpty() && !hiddenWalls.isEmpty()) {
                    animateNextWall();
                } else if (!hiddenWalls.isEmpty() && rushEnabled) {
                    // Multiple walls can be on if rush is active
                    animateNextWall();
                }

                // Tick rush (if active)
                if (rush != null) {
                    if (rush.getTicksRemaining() <= 0) {
                        endRush();
                    } else {
                        rush.tick();
                        if (visibleWalls.isEmpty()) {
                            if (animatingWall == null) {
                                addWall(rush.deploy());
                            }
                        } else if (rush.getNextSpawn() == 0) {
                            addWall(rush.deploy());
                        }
                    }
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
                        if (rushEnabled) {
                            endRush();
                        }
                        pauseLoop = 10;
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
        if (visibleWalls.isEmpty()) return;
        Wall wall = visibleWalls.get(0);
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

    public void stop() {
        task.cancel();
    }

    public void activateRush() {
        rushEnabled = true;
        rush = new Rush();
        for (Wall wall : visibleWalls) {
            wall.despawn();
        }
        visibleWalls.clear();
        if (animatingWall != null) {
            animatingWall.despawn();
            animatingWall = null;
        }
        hiddenWalls.clear();
    }

    public void endRush() {
        field.endRush();
        rushEnabled = false;
        rush = null;
        for (Wall wall : visibleWalls) {
            wall.despawn();
        }
        visibleWalls.clear();
        if (animatingWall != null) {
            animatingWall.despawn();
            animatingWall = null;
        }
        hiddenWalls.clear();
    }

    public Rush getRush() {
        return rush;
    }

    public boolean isRushEnabled() {
        return rushEnabled;
    }
}
