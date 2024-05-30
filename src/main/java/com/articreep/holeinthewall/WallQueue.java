package com.articreep.holeinthewall;

import com.articreep.holeinthewall.modifiers.Rush;
import com.articreep.holeinthewall.multiplayer.WallGenerator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class WallQueue {

    /**
     * This is in ticks.
     */
    private int wallActiveTime = 160;
    private final PlayingField field;
    private final int fullLength = 20;
    // todo this number will change if "garbage walls" accumulate in the queue
    private int effectiveLength = 20;

    /**
     * Walls that are spawned but are invisible.
     */
    private final List<Wall> hiddenWalls;
    private Wall animatingWall;
    private final List<Wall> visibleWalls;
    private int pauseTickLoop = 0;
    private boolean allowMultipleWalls = false;
    private int maxSpawnCooldown = 80;
    private int spawnCooldown = 80;

    // Wall generation settings
    private WallGenerator generator;
    boolean hideBottomBorder = false;
    Material wallMaterial = Material.BLUE_CONCRETE;

    public WallQueue(PlayingField field) {
        hiddenWalls = new LinkedList<>();
        animatingWall = null;
        visibleWalls = new ArrayList<>();
        this.field = field;
        setGenerator(new WallGenerator(field.getLength(), field.getHeight(), 2, 4));
    }

    public void addWall(Wall wall) {
        hiddenWalls.add(wall);
        if (wall.getTimeRemaining() == -1) {
            // default wall speed
            wall.setTimeRemaining(wallActiveTime);
        }
    }

    public void animateNextWall() {
        if (animatingWall != null) return;
        if (hiddenWalls.isEmpty()) return;
        animatingWall = hiddenWalls.removeFirst();
        animatingWall.spawnWall(field, this, hideBottomBorder);
        animatingWall.animateWall(field.getPlayers(), wallMaterial);
        field.sendMessageToPlayers("Wall time: " + animatingWall.getTimeRemaining());
        new BukkitRunnable() {
            @Override
            public void run() {
                // Wait for wall status to be visible
                if (animatingWall == null) return;
                if (animatingWall.getWallState() == WallState.VISIBLE) {
                    visibleWalls.add(animatingWall);
                    animatingWall = null;
                    this.cancel();
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    public void tick() {
        if (pauseTickLoop > 0) {
            pauseTickLoop--;
            return;
        }

        if (hiddenWalls.isEmpty() && !field.eventActive()) {
            // Tell generator to add a new wall to all queues
            generator.addNewWallToQueues();
        }

        // Animate the next wall when possible
        if (visibleWalls.isEmpty() && !hiddenWalls.isEmpty()) {
            spawnCooldown = maxSpawnCooldown;
            animateNextWall();
        // only decrement spawnCooldown if allowMultipleWalls is true
        } else if (allowMultipleWalls && spawnCooldown-- <= 0) {
            spawnCooldown = maxSpawnCooldown;
            animateNextWall();
        }

        // Tick all visible walls
        Iterator<Wall> it = visibleWalls.iterator();
        while (it.hasNext()) {
            Wall wall = it.next();
            int remaining = wall.tick(WallQueue.this);
            // If wall runs out of time -
            if (remaining <= 0 && wall.getWallState() == WallState.VISIBLE) {
                wall.despawn();
                field.matchAndScore(wall);
                it.remove();
                if (field.eventActive() && field.getEvent() instanceof Rush) {
                    field.endEvent();
                }

                pauseTickLoop = field.getClearDelay();
            } else if (wall.getWallState() != WallState.VISIBLE) {
                Bukkit.getLogger().severe(ChatColor.RED + "Attempted to tick wall before spawned..");
            }
        }
    }

    /**
     * Insta-sends the current wall to the playing field for matching.
     */
    public void instantSend() {
        if (visibleWalls.isEmpty()) return;
        // sort walls by time remaining
        sortActiveWalls();
        Wall wall = visibleWalls.getFirst();
        field.matchAndScore(wall);
        visibleWalls.remove(wall);
        wall.despawn();
    }

    public void sortActiveWalls() {
        visibleWalls.sort(Comparator.comparingInt(Wall::getTimeRemaining));
    }

    public int getFullLength() {
        return fullLength;
    }

    public void clearAllWalls() {
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

    public void setWallActiveTime(int wallActiveTime) {
        this.wallActiveTime = wallActiveTime;
    }

    public void setRandomHoleCount(int randomHoleCount) {
        generator.setRandomHoleCount(randomHoleCount);
    }

    public void setConnectedHoleCount(int connectedHoleCount) {
        generator.setConnectedHoleCount(connectedHoleCount);
    }

    public void setRandomizeFurther(boolean randomizeFurther) {
        generator.setRandomizeFurther(randomizeFurther);
    }

    public void clearHiddenWalls() {
        hiddenWalls.clear();
    }

    /**
     * Counts all walls that are visible, including the wall currently being animated.
     * @return The number of visible walls.
     */
    public int countVisibleWalls() {
        return visibleWalls.size() + (animatingWall != null ? 1 : 0);
    }

    public int countHiddenWalls() {
        return hiddenWalls.size();
    }

    public void setHideBottomBorder(boolean hideBottomBorder) {
        this.hideBottomBorder = hideBottomBorder;
    }

    public void setGenerator(WallGenerator generator) {
        this.generator = generator;
        generator.addQueue(this);
    }

    public void resetGenerator() {
        this.generator = new WallGenerator(field.getLength(), field.getHeight(), 2, 4);
        generator.addQueue(this);
    }

    public void allowMultipleWalls(boolean allow) {
        allowMultipleWalls = allow;
    }

    public void setMaxSpawnCooldown(int maxSpawnCooldown) {
        this.maxSpawnCooldown = maxSpawnCooldown;
        spawnCooldown = maxSpawnCooldown;
    }
}
