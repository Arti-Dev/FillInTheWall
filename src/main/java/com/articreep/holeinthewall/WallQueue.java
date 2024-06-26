package com.articreep.holeinthewall;

import com.articreep.holeinthewall.modifiers.Rush;
import com.articreep.holeinthewall.multiplayer.WallGenerator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class WallQueue {

    /**
     * This is in ticks.
     */
    private final int defaultWallActiveTime = 160;
    private final PlayingField field;
    private final int fullLength = 20;
    // todo this number will change if "garbage walls" accumulate in the queue
    private int effectiveLength = 20;

    /**
     * Walls that are spawned but are invisible.
     */
    private final List<Wall> hiddenWalls = new LinkedList<>();
    private Wall animatingWall = null;
    private final List<Wall> activeWalls = new ArrayList<>();
    private final Deque<Wall> hardenedWalls = new ArrayDeque<>();
    private int pauseTickLoop = 0;
    private boolean allowMultipleWalls = false;
    private int maxSpawnCooldown = 80;
    private int spawnCooldown = 80;

    // Wall generation settings
    private WallGenerator generator;
    boolean hideBottomBorder = false;
    private Material wallMaterial = Material.BLUE_CONCRETE;

    public WallQueue(PlayingField field, Material defaultWallMaterial, boolean hideBottomBorder) {
        setWallMaterial(defaultWallMaterial);
        setHideBottomBorder(hideBottomBorder);
        this.field = field;
    }

    public WallQueue(PlayingField field, Material defaultWallMaterial) {
        this(field, defaultWallMaterial, false);
    }

    public void addWall(Wall wall) {
        hiddenWalls.add(wall);
        if (wall.getTimeRemaining() == -1) {
            // default wall speed
            wall.setTimeRemaining(defaultWallActiveTime);
        }
    }

    public void animateNextWall() {
        if (animatingWall != null) return;
        if (hiddenWalls.isEmpty()) return;

        if (!hardenedWalls.isEmpty() && hardenedWalls.peek().getHardness() <= 0) {
            field.playSoundToPlayers(Sound.ENTITY_GOAT_HORN_BREAK, 0.5f, 0.5f);
            animatingWall = hardenedWalls.poll();
            updateEffectiveLength();
            animatingWall.setDistanceToTraverse(effectiveLength);
            animatingWall.activateWall(field.getPlayers(), wallMaterial);
        } else {
            animatingWall = hiddenWalls.removeFirst();
            // Recalculate wall time
            animatingWall.setTimeRemaining(calculateWallActiveTime(animatingWall.getTimeRemaining()));
            updateEffectiveLength();
            animatingWall.setDistanceToTraverse(effectiveLength);
            animatingWall.spawnWall(field, this, WallState.ANIMATING, hideBottomBorder);
            animatingWall.activateWall(field.getPlayers(), wallMaterial);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                // Wait for wall status to be visible
                if (animatingWall == null) return;
                if (animatingWall.getWallState() == WallState.VISIBLE) {
                    activeWalls.add(animatingWall);
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

        if (hiddenWalls.isEmpty()) {
            // If there's an event going on that overrides generation, don't generate a new wall
            if (!(field.eventActive() && field.getEvent().overrideGeneration)) {
                // Tell generator to add a new wall to all queues
                generator.addNewWallToQueues();
            }
        }

        // Animate the next wall when possible
        if (activeWalls.isEmpty() && !hiddenWalls.isEmpty()) {
            spawnCooldown = maxSpawnCooldown;
            animateNextWall();
        // only decrement spawnCooldown if allowMultipleWalls is true
        } else if (allowMultipleWalls && spawnCooldown-- <= 0) {
            spawnCooldown = maxSpawnCooldown;
            animateNextWall();
        }

        // If walls are frozen, make particles and return
        if (field.eventActive() && field.getEvent().wallFreeze) {
            for (Wall wall : activeWalls) {
                wall.frozenParticles();
            }
            return;
        }

        // Tick all visible walls
        Iterator<Wall> it = activeWalls.iterator();
        while (it.hasNext()) {
            Wall wall = it.next();
            int remaining = wall.tick(WallQueue.this);
            // If wall runs out of time -
            if (remaining <= 0 && wall.getWallState() == WallState.VISIBLE) {
                wall.despawn();
                /* todo concurrent modification exception happening here when game ends:
                - wall naturally comes to playing field
                - too many garbage walls so game ends, so something with updateEffectiveLength
                 */
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
        if (activeWalls.isEmpty()) return;
        // sort walls by time remaining
        sortActiveWalls();
        Wall wall = activeWalls.getFirst();
        field.matchAndScore(wall);
        activeWalls.remove(wall);
        wall.despawn();
    }

    public void sortActiveWalls() {
        activeWalls.sort(Comparator.comparingInt(Wall::getTimeRemaining));
    }

    public int getFullLength() {
        return fullLength;
    }

    public int getEffectiveLength() {
        return effectiveLength;
    }

    public void clearAllWalls() {
        for (Wall wall : activeWalls) {
            wall.despawn();
        }
        for (Wall wall : hardenedWalls) {
            wall.despawn();
        }
        activeWalls.clear();
        if (animatingWall != null) {
            animatingWall.despawn();
            animatingWall = null;
        }
        hiddenWalls.clear();
        hardenedWalls.clear();
    }

    public void setWallActiveTime(int wallActiveTime) {
        generator.setWallActiveTime(wallActiveTime);
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
        return activeWalls.size() + (animatingWall != null ? 1 : 0);
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
        this.generator = new WallGenerator(field.getLength(), field.getHeight(), 2, 4, 160);
        generator.addQueue(this);
    }

    public void allowMultipleWalls(boolean allow) {
        allowMultipleWalls = allow;
    }

    public void setMaxSpawnCooldown(int maxSpawnCooldown) {
        this.maxSpawnCooldown = maxSpawnCooldown;
        spawnCooldown = maxSpawnCooldown;
    }

    public void setWallMaterial(Material wallMaterial) {
        this.wallMaterial = wallMaterial;
    }

    public Material getWallMaterial() {
        return wallMaterial;
    }

    public void correctAllWalls() {
        for (Wall wall : activeWalls) {
            wall.correct();
        }
    }

    // Wall hardening

    /**
     * Takes a new wall and hardens it at the end of the queue.
     * @param wall Wall to harden
     * @param hardness Resistance to positive judgements (perfect = 2, cool = 1)
     */
    public void hardenWall(Wall wall, int hardness) {
        if (effectiveLength <= 0) return;
        if (wall.getWallState() != WallState.HIDDEN) {
            Bukkit.getLogger().severe(ChatColor.RED + "Attempted to harden wall that is not hidden/new..");
            return;
        }

        // Tell wall where to spawn
        // Set its wall active time ahead of time (it won't be changing)
        // Add to hardened walls list
        // Update effective length

        int baseTime = generator.getWallActiveTime();
        wall.setTimeRemaining(calculateWallActiveTime(baseTime));

        wall.spawnWall(field, this, WallState.HARDENED, hideBottomBorder);
        wall.setHardness(hardness);

        hardenedWalls.push(wall);

        updateEffectiveLength();
    }

    /**
     * Attempts to break the next hardened wall in the queue.
     * @param power How much to crack the wall by
     */
    public void crackHardenedWall(int power) {
        if (hardenedWalls.isEmpty()) return;
        Wall wall = hardenedWalls.getFirst();
        wall.decreaseHardness(power);
    }

    public int calculateWallActiveTime(int baseTime) {
        // todo could probably make this more lenient - it's very hard to keep up with a non-linear speed increase
        double ratio = (double) effectiveLength / fullLength;
        return (int) (baseTime * ratio);
    }

    public void updateEffectiveLength() {
        effectiveLength = fullLength - hardenedWalls.size();
        if (effectiveLength <= 0) {
            // End game
            field.stop();
        }
    }
}
