package com.articreep.holeinthewall;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class WallQueue {

    /**
     * This is in ticks.
     */
    private final int timeToFill = 160;
    private PlayingField field = null;
    // todo this number is arbitrary
    private final int length = 20;

    /**
     * Walls that are spawned but are invisible.
     */
    protected List<Wall> hiddenWalls;
    protected Wall animatingWall;
    protected List<Wall> visibleWalls;
    private int pauseLoop = 0;

    public WallQueue(PlayingField field) {
        hiddenWalls = new LinkedList<>();
        animatingWall = null;
        visibleWalls = new ArrayList<>();
        this.field = field;
        this.field.setQueue(this);
    }

    public void addWall(Wall wall) {
        hiddenWalls.add(wall);
        if (wall.getTimeRemaining() == -1) {
            // default wall speed
            wall.setTimeRemaining(timeToFill);
        }
    }

    public void animateNextWall() {
        if (animatingWall != null) return;
        if (hiddenWalls.isEmpty()) return;
        animatingWall = hiddenWalls.removeFirst();
        animatingWall.spawnWall(field, this, field.getPlayer());
        animatingWall.animateWall(this, field.getPlayer());
        new BukkitRunnable() {
            @Override
            public void run() {
                // fix possible race condition: only add to visible walls if the wall has spawned
                // (finished spawn animation)
                // todo this task/conditional might not be necessary
                if (animatingWall == null) return;
                if (animatingWall.getWallState() == WallState.VISIBLE) {
                    visibleWalls.add(animatingWall);
                    animatingWall = null;
                    this.cancel();
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 10, 1);
    }

    public void tick() {
        if (pauseLoop > 0) {
            pauseLoop--;
            return;
        }

        if (hiddenWalls.isEmpty() && !field.eventActive()) {
            Random random = new Random();
            Wall newWall = new Wall();
            newWall.generateHoles(2, random.nextInt(1,5));
            addWall(newWall);
        }

        // Animate the next wall when possible
        if (visibleWalls.isEmpty() && !hiddenWalls.isEmpty()) {
            animateNextWall();
        } else if (!hiddenWalls.isEmpty() && field.eventActive() && field.getEvent().allowMultipleWalls) {
            // Multiple walls can be on if an event allows it
            // todo this does not allow for a cooldown between walls. subject to change
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
                Bukkit.broadcastMessage(ChatColor.RED + "Attempted to tick wall before spawned..");
            }
        }
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
}
