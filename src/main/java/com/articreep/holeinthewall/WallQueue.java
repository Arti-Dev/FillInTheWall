package com.articreep.holeinthewall;

import com.articreep.holeinthewall.modifiers.Rush;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class WallQueue {

    /**
     * This is in ticks.
     */
    private int wallActiveTime = 160;
    private PlayingField field = null;
    private final int fullLength = 20;
    // todo this number will change if "garbage walls" accumulate in the queue
    private int effectiveLength = 20;

    /**
     * Walls that are spawned but are invisible.
     */
    protected List<Wall> hiddenWalls;
    // todo public for now
    public Wall animatingWall;
    public List<Wall> visibleWalls;
    private int pauseLoop = 0;

    // Wall generation settings
    int randomHoleCount = 2;
    int connectedHoleCount = 4;
    boolean randomizeFurther = true;
    boolean hideBottomBorder = false;

    public WallQueue(PlayingField field) {
        hiddenWalls = new LinkedList<>();
        animatingWall = null;
        visibleWalls = new ArrayList<>();
        this.field = field;
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
        animatingWall.animateWall(field.getPlayers());
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
        if (pauseLoop > 0) {
            pauseLoop--;
            return;
        }

        if (hiddenWalls.isEmpty() && !field.eventActive()) {
            Wall newWall = new Wall(field.getLength(), field.getHeight());
            newWall.generateHoles(randomHoleCount, connectedHoleCount, randomizeFurther);
            addWall(newWall);
        }

        // Animate the next wall when possible
        if (visibleWalls.isEmpty() && !hiddenWalls.isEmpty()) {
            animateNextWall();
        } else if (!hiddenWalls.isEmpty() && field.eventActive() && field.getEvent().allowMultipleWalls) {
            // Multiple walls can be on if an event allows it
            // todo this does not allow for a cooldown between walls and very rarely makes rush bug out
            animateNextWall();
        }

        // Tick all visible walls
        Iterator<Wall> it = visibleWalls.iterator();
        while (it.hasNext()) {
            Wall wall = it.next();
            int remaining = wall.tick(WallQueue.this);
            if (remaining <= 0 && wall.getWallState() == WallState.VISIBLE) {
                wall.despawn();
                field.matchAndScore(wall);
                it.remove();
                if (field.eventActive() && field.getEvent() instanceof Rush) {
                    field.endEvent();
                }
                pauseLoop = 10;
            } else if (wall.getWallState() != WallState.VISIBLE) {
                Bukkit.getLogger().severe(ChatColor.RED + "Attempted to tick wall before spawned..");
            }
        }
    }

    /**
     * Insta-sends the current wall to the playing field for matching.
     */
    public void instantSend() {
        // todo closest wall will ALWAYS be the first element for now.
        if (visibleWalls.isEmpty()) return;
        Wall wall = visibleWalls.getFirst();
        field.matchAndScore(wall);
        visibleWalls.remove(wall);
        wall.despawn();
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
        this.randomHoleCount = randomHoleCount;
    }

    public void setConnectedHoleCount(int connectedHoleCount) {
        this.connectedHoleCount = connectedHoleCount;
    }

    public void setRandomizeFurther(boolean randomizeFurther) {
        this.randomizeFurther = randomizeFurther;
    }

    public void clearHiddenWalls() {
        hiddenWalls.clear();
    }

    public void setHideBottomBorder(boolean hideBottomBorder) {
        this.hideBottomBorder = hideBottomBorder;
    }

    public boolean isHideBottomBorder() {
        return hideBottomBorder;
    }
}
