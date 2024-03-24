package com.articreep.holeinthewall;

import org.bukkit.Material;
import org.javatuples.Pair;

import java.util.Random;

public class Rush {
    /*
    A rush lasts for 30 seconds.
    Boards come at you in quick succession, however the blocks you place do not disappear
    The walls cascade into the floor when they hit your field.
    The differences from consecutive boards are miniscule (1-2 clicks) but do increase the further you get
     */

    private Wall nextWall;
    private int ticksRemaining = 600;
    private int wallSpeed = 200;
    private int spawnSpeed = 30;
    private int nextSpawn = spawnSpeed;
    private int boardsCleared = 0;

    public Rush() {
        nextWall = new Wall(randomMaterial());
        nextWall.insertHoles(randomHole());
    }

    public Wall deploy() {
        Wall toReturn = nextWall;
        nextWall = generateNextWall(1);
        toReturn.setTimeRemaining(wallSpeed);
        if (spawnSpeed > 0) {
            wallSpeed -= 4;
        }
        wallSpeed -= 5;

        nextSpawn = spawnSpeed;
        return toReturn;
    }

    private Wall generateNextWall(int diff) {
        // Copy data from old wall
        Wall wall = new Wall(randomMaterial());
        for (Pair<Integer, Integer> hole : nextWall.getHoles()) {
            wall.insertHole(hole);
        }

        if (wall.getHoles().size() < 2 || (Math.random() < 0.75 && wall.getHoles().size() < 5)) {
            for (int i = 0; i < diff; i++) {
                // Add a new hole
                wall.insertHole(randomConnectedHole(wall));
            }
        } else {
            for (int i = 0; i < diff; i++) {
                // Remove an existing hole
                wall.removeHole(wall.randomHole());
            }
        }
        return wall;
    }



    private Pair<Integer, Integer> randomHole() {
        return Pair.with((int) (Math.random() * 7), (int) (Math.random() * 4));
    }

    private Pair<Integer, Integer> randomConnectedHole(Wall wall) {
        int attempts = 0;
        while (attempts < 10) {
            // Choose a random hole in the provided wall
            Pair<Integer, Integer> existingHole = wall.randomHole();
            if (existingHole == null) {
                return randomHole();
            }
            // Choose a random direction to spread the hole to
            Random random = new Random();
            int x = random.nextInt(-1, 2);
            int y = random.nextInt(-1, 2);
            Pair<Integer, Integer> newHole = Pair.with(existingHole.getValue0() + x, existingHole.getValue1() + y);
            // If the new hole is in bounds and is not already a hole, return it
            if (newHole.getValue0() >= 0 && newHole.getValue0() < 7 && newHole.getValue1() >= 0 && newHole.getValue1() < 4
                    && !wall.hasHole(newHole)) {
                return newHole;
            } else {
                attempts++;
            }
        }
        return null;
    }

    private Material randomMaterial() {
        Material[] materials = {Material.BLUE_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA,
        Material.MAGENTA_GLAZED_TERRACOTTA, Material.WHITE_GLAZED_TERRACOTTA,
        Material.REDSTONE_BLOCK, Material.DIAMOND_BLOCK, Material.PURPUR_BLOCK, Material.EMERALD_BLOCK,
            Material.IRON_BLOCK, Material.GOLD_BLOCK, Material.COAL_BLOCK, Material.LAPIS_BLOCK};
        return materials[(int) (Math.random() * materials.length)];
    }

    public int getWallSpeed() {
        return wallSpeed;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public int getNextSpawn() {
        return nextSpawn;
    }

    public void tick() {
        ticksRemaining--;
        nextSpawn--;
    }

    public void increaseBoardsCleared() {
        boardsCleared++;
    }
}
