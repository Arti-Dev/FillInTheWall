package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.Wall;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.javatuples.Pair;

import java.util.Map;

public class Inverted extends ModifierEvent {
    public Inverted(PlayingField field, int ticks) {
        super(field, ticks);
        fillFieldAfterSubmission = true;
        modifyWalls = true;
        overrideScoreCalculation = true;
        overridePercentCalculation = true;
    }

    @Override
    public void activate() {
        field.getQueue().instantSend();
        super.activate();
        field.playSoundToPlayers(Sound.BLOCK_BEACON_ACTIVATE, 2f);
        field.sendTitleToPlayers(ChatColor.BLACK + "Inverted!", "Left-click to win..?", 0, 40, 10);
    }

    @Override
    public int calculateScore(Wall wall) {
        if (!wall.isInverted()) return super.calculateScore(wall);
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(field);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(field);

        // Amount of empty space
        int points = wall.getLength() * wall.getHeight() - wall.getHoles().size() - missingBlocks.size() - extraBlocks.size();
        if (points < 0) points = 0;
        return points;
    }

    @Override
    public double calculatePercent(Wall wall) {
        if (!wall.isInverted()) return super.calculatePercent(wall);
        return (double) calculateScore(wall) / (wall.getLength() * wall.getHeight() - wall.getHoles().size());
    }

    @Override
    public void end() {
        field.getQueue().instantSend();
        for (Wall wall : field.getQueue().getActiveWalls()) {
            if (wall.isInverted()) wall.invert();
        }
        field.clearField();
        super.end();
        field.playSoundToPlayers(Sound.BLOCK_BEACON_DEACTIVATE, 2f);
        field.sendTitleToPlayers("", "Walls are back to normal!", 0, 20, 10);
    }

    @Override
    public void modifyWall(Wall wall) {
        wall.invert();
    }
}
