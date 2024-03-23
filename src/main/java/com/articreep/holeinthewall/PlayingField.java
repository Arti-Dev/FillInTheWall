package com.articreep.holeinthewall;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.javatuples.Pair;

import java.util.HashMap;
import java.util.Map;

public class PlayingField {
    Player player;
    /**
     * Must be the bottom left corner of the playing field (NOT including the border blocks)
     */
    Location fieldReferencePoint;
    Vector fieldDirection;
    Vector incomingDirection;
    int score = 0;
    final int height = 4;

    public PlayingField(Player player, Location referencePoint, Vector direction, Vector incomingDirection) {
        // define playing field in a very scuffed way
        this.fieldReferencePoint = referencePoint;
        this.fieldDirection = direction;
        this.incomingDirection = incomingDirection;
        this.player = player;
    }

    /**
     * Returns all blocks in the playing field excluding air blocks.
     * @return
     */
    public Map<Pair<Integer, Integer>, Block> getPlayingFieldBlocks() {
        HashMap<Pair<Integer, Integer>, Block> blocks = new HashMap<>();
        // y direction loop
        for (int y = 0; y < height; y++) {
            Location loc = getFieldReferencePoint().add(0, y, 0);
            for (int x = 0; x < 4; x++) {
                loc.add(fieldDirection);
                if (!loc.getBlock().isEmpty()) {
                    blocks.put(Pair.with(x, y), loc.getBlock());
                }
            }
        }
        return blocks;
    }

    public Location getFieldReferencePoint() {
        return fieldReferencePoint.clone();
    }

    public void matchAndScore(Wall wall) {
        int extraBlocks = wall.getExtraBlocks(this).size();
        int correctBlocks = wall.getCorrectBlocks(this).size();
        int score = correctBlocks - extraBlocks;
        addScore(score);
    }

    private void addScore(int score) {
        this.score += score;
        player.sendMessage("Current score: " + this.score);
    }

    public Vector getIncomingDirection() {
        return incomingDirection.clone();
    }

    public Vector getFieldDirection() {
        return fieldDirection.clone();
    }
}
