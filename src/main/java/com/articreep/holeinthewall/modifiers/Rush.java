package com.articreep.holeinthewall.modifiers;

import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.Wall;
import com.articreep.holeinthewall.environments.TheVoid;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class Rush extends ModifierEvent {
    /*
    A rush lasts for 30 seconds.
    Boards come at you in quick succession, however the blocks you place do not disappear
    The walls cascade into the floor when they hit your field.
    The differences from consecutive boards are minuscule (1-2 clicks) but do increase the further you get
     */
    private boolean firstWallCleared = false;
    private Wall nextWall;
    private int wallSpeed = 200;
    private int boardsCleared = 0;
    private boolean active = false;
    public Rush(PlayingField field) {
        super(field, 600);
        pauseTime = 5;
        overrideScoring = true;
        allowMultipleWalls = true;
        timeFreeze = true;

        nextWall = new Wall(field.getLength(), field.getHeight());
        nextWall.setMaterial(randomMaterial());
        nextWall.insertHoles(nextWall.randomCoordinates());
    }

    public void deploy() {
        Wall toReturn = nextWall;
        nextWall = generateNextWall();
        toReturn.setTimeRemaining(wallSpeed);
        wallSpeed -= 9;

        queue.addWall(toReturn);
    }

    private Wall generateNextWall() {
        // Copy data from old wall
        Wall wall = nextWall.copy();
        wall.setMaterial(randomMaterial());

        if (wall.getHoles().size() < 2 || (Math.random() < 0.75 && wall.getHoles().size() < 5)) {
            for (int i = 0; i < 1; i++) {
                // Add a new hole
                wall.insertHole(wall.randomCoordinatesConnected());
            }
        } else {
            for (int i = 0; i < 1; i++) {
                // Remove an existing hole
                wall.removeHole(wall.randomHole());
            }
        }
        return wall;
    }

    private static Material randomMaterial() {
        Material[] materials = {Material.BLUE_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA,
        Material.MAGENTA_GLAZED_TERRACOTTA, Material.WHITE_GLAZED_TERRACOTTA,
        Material.REDSTONE_BLOCK, Material.DIAMOND_BLOCK, Material.PURPUR_BLOCK, Material.EMERALD_BLOCK,
            Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.COAL_BLOCK, Material.LAPIS_BLOCK};
        return materials[(int) (Math.random() * materials.length)];
    }

    @Override
    public void tick() {
        super.tick();

        if (queue.countHiddenWalls() < 5) {
            deploy();
        }
    }

    public void increaseBoardsCleared() {
        boardsCleared++;
    }

    public int getBoardsCleared() {
        return boardsCleared;
    }

    @Override
    public void activate() {
        active = true;
        for (Player player : field.getPlayers()) {
            player.sendTitle(ChatColor.RED + "RUSH!", ChatColor.RED + "Clear as many walls as you can!", 0, 40, 10);
            player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5F, 1);
        }
        field.clearField();

        queue.clearAllWalls();
        queue.allowMultipleWalls(true);
        queue.setMaxSpawnCooldown(30);

        TheVoid.spawnRotatingBlocks(field, this);
    }

    @Override
    public void end() {
        active = false;
        field.clearField();
        queue.clearAllWalls();
        queue.setMaxSpawnCooldown(80);
        queue.allowMultipleWalls(false);
        field.getScorer().scoreEvent(this);
        if (field.getEnvironment().equalsIgnoreCase("VOID")) {
            TheVoid.resetTime(field);
        }
        for (Player player : field.getPlayers()) {
            player.playSound(player, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
            player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, player.getLocation(), 1);
            player.sendTitle(ChatColor.GREEN + "RUSH OVER!", ChatColor.GREEN + "" + boardsCleared + " walls cleared", 0, 40, 10);
        }
    }

    @Override
    public String actionBarOverride() {
        ChatColor color;
        int cleared = boardsCleared;
        if (cleared <= 3) {
            color = ChatColor.GRAY;
        } else if (cleared <= 7) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.GREEN;
        }
        return color + "" + ChatColor.BOLD + "Walls Cleared: " + cleared;
    }

    @Override
    public void score(Wall wall) {
        if (field.getScorer().calculatePercent(wall, field) == 1) {
            double pitch = Math.pow(2, (double) (getBoardsCleared() - 6) / 12);
            if (pitch > 2) pitch = 2;
            for (Player player : field.getPlayers()) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, (float) pitch);
            increaseBoardsCleared();
            field.critParticles();
            if (field.getEnvironment().equalsIgnoreCase("VOID")) {
                TheVoid.adjustTime(field, this);
            }
        } else {
            for (Player player : field.getPlayers()) player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 1, 1);
        }
    }

    public boolean hasFirstWallCleared() {
        return firstWallCleared;
    }

    public void setFirstWallCleared(boolean b) {
        firstWallCleared = b;
    }

    public boolean isActive() {
        return active;
    }
}
