package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.Judgement;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.PlayingFieldScorer;
import com.articreep.fillinthewall.Wall;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.javatuples.Pair;

import java.util.HashMap;
import java.util.Map;


public class FireInTheHole extends ModifierEvent {
    public FireInTheHole(PlayingField field, int ticks) {
        super(field, ticks);
        overrideScoreCalculation = true;
        overrideBonusCalculation = true;
        overrideScoreTitle = true;
        overrideCorrectBlocksVisual = true;
    }

    @Override
    public void activate() {
        super.activate();
        field.sendTitleToPlayers(ChatColor.GREEN + "FIRE IN THE HOLE!", "Fill holes with " +
                ChatColor.RED + "fire" + ChatColor.RESET + " for bonus points!", 0, 40, 10);
        for (Player player : field.getPlayers()) {
            // todo maybe put this in a certain slot
            player.getInventory().addItem(new ItemStack(Material.FLINT_AND_STEEL));
        }
    }

    @Override
    public int calculateScore(Wall wall) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(field);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(field);


        for (Block block : correctBlocks.values()) {
            if (block.getType() == Material.FIRE) {
                Block supportingBlock = block.getRelative(BlockFace.DOWN);
                extraBlocks.remove(field.blockToCoordinates(supportingBlock));
            }
        }

        // Check score
        int points = correctBlocks.size() - extraBlocks.size();
        if (points < 0) points = 0;
        return points;
    }

    @Override
    public HashMap<PlayingFieldScorer.BonusType, Integer> evaluateBonus(double percent, Wall wall) {
        HashMap<PlayingFieldScorer.BonusType, Integer> bonusMap = super.evaluateBonus(percent, wall);

        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(field);
        int fireBonus = 0;
        for (Block block : correctBlocks.values()) {
            if (block.getType() == Material.FIRE) fireBonus++;
        }
        bonusMap.put(PlayingFieldScorer.BonusType.FIRE, fireBonus);
        return bonusMap;
    }

    @Override
    public void displayScoreTitle(Judgement judgement, int score, HashMap<PlayingFieldScorer.BonusType, Integer> bonus) {
        int fireBonus = bonus.get(PlayingFieldScorer.BonusType.FIRE);
        field.sendTitleToPlayers(
                judgement.getColor() + judgement.getText(),
                judgement.getColor() + "" + (score + bonus.get(PlayingFieldScorer.BonusType.PERFECT)) +
                        ChatColor.RED + "+" + fireBonus + judgement.getColor() + " points",
                0, 10, 5);
    }

    @Override
    public void correctBlocksVisual(Wall wall) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(field);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(field);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(field);

        for (Block block : correctBlocks.values()) {
            if (block.getType() == Material.FIRE) {
                Block supportingBlock = block.getRelative(BlockFace.DOWN);
                extraBlocks.remove(field.blockToCoordinates(supportingBlock));
            }
        }

        // Visually display what blocks were correct and what were wrong
        field.fillField(wall.getMaterial());
        for (Block block : extraBlocks.values()) {
            block.setType(Material.RED_WOOL);
        }
        for (Block block : correctBlocks.values()) {
            block.setType(Material.GREEN_WOOL);
        }
        for (Block block : missingBlocks.values()) {
            block.setType(Material.AIR);
        }
    }

    @Override
    public void end() {
        super.end();
        // todo remove flint and steel
        field.sendTitleToPlayers("", ChatColor.GREEN + "Fire no longer gives a point bonus!", 0, 20, 10);
    }
}
