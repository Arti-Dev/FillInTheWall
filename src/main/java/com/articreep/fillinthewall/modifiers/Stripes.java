package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.Judgement;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.PlayingFieldScorer;
import com.articreep.fillinthewall.Wall;
import com.articreep.fillinthewall.utils.Utils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.javatuples.Pair;

import java.util.HashMap;
import java.util.Map;

public class Stripes extends ModifierEvent {
    public Stripes(PlayingField field, int ticks) {
        super(field, ticks);
        modifyWalls = true;
        overrideBonusCalculation = true;
        overrideScoreTitle = true;
    }

    @Override
    public void activate() {
        super.activate();
        Wall wall = field.getQueue().getFrontmostWall();
        if (wall != null) wall.setStripes(true);
        for (Player player : field.getPlayers()) {
            player.getInventory().addItem(new ItemStack(Utils.getAlternateMaterial(field.getWallMaterial())));
        }
        field.sendTitleToPlayers("Stripes!", "Match colors for bonus points!", 0, 40, 10);
    }

    @Override
    public HashMap<PlayingFieldScorer.BonusType, Integer> evaluateBonus(double percent, Wall wall) {
        HashMap<PlayingFieldScorer.BonusType, Integer> bonusMap = super.evaluateBonus(percent, wall);
        if (!wall.hasStripes()) {
            bonusMap.put(PlayingFieldScorer.BonusType.STRIPE, 0);
            return bonusMap;
        }

        int stripeBonus = 0;
        Map<Pair<Integer, Integer>, Block> blockMap = wall.getCorrectBlocks(field);
        for (Pair<Integer, Integer> coords : blockMap.keySet()) {
            // todo hardcoded as well
            if (coords.getValue1() % 2 == 0) {
                if (blockMap.get(coords).getType().equals(Utils.getAlternateMaterial(wall.getMaterial()))) stripeBonus++;
            } else {
                if (blockMap.get(coords).getType().equals(Utils.getAlternateMaterial(wall.getAltMaterial()))) stripeBonus++;
            }
        }
        bonusMap.put(PlayingFieldScorer.BonusType.STRIPE, stripeBonus);
        return bonusMap;
    }

    @Override
    public void displayScoreTitle(Judgement judgement, int score, HashMap<PlayingFieldScorer.BonusType, Integer> bonus) {
        int stripeBonus = bonus.get(PlayingFieldScorer.BonusType.STRIPE);
        field.sendTitleToPlayers(
                judgement.getColor() + judgement.getText(),
                judgement.getColor() + "" + (score + bonus.get(PlayingFieldScorer.BonusType.PERFECT)) +
                        ChatColor.DARK_PURPLE + "+" + stripeBonus + judgement.getColor() + " points",
                0, 10, 5);
    }

    @Override
    public void end() {
        super.end();
        Wall wall = field.getQueue().getFrontmostWall();
        if (wall != null) wall.setStripes(false);
        field.sendTitleToPlayers("", "Stripes are gone!", 0, 20, 10);
    }

    @Override
    public void modifyWall(Wall wall) {
        wall.setStripes(true);
    }
}
