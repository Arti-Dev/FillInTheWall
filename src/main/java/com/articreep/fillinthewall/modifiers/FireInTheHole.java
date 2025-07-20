package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.game.Judgement;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.game.PlayingFieldScorer;
import com.articreep.fillinthewall.game.Wall;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.javatuples.Pair;

import java.time.Duration;
import java.util.*;


public class FireInTheHole extends ModifierEvent {
    public FireInTheHole() {
        super();
        overrideScoreCalculation = true;
        overrideBonusCalculation = true;
        overrideScoreTitle = true;
        overrideCorrectBlocksVisual = true;
    }

    @Override
    public void activate() {
        super.activate();
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(500));
        field.sendTitleToPlayers(Title.title(miniMessage.deserialize("<green>FIRE IN THE HOLE"),
                        miniMessage.deserialize( "Fill holes with <red>fire</red> for bonus points!"),
                        times));
        addTemporaryItemToPlayers(flintAndSteel());
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
        Title title = Title.title(judgement.getFormattedText(),
                Component.text(score + bonus.get(PlayingFieldScorer.BonusType.PERFECT), judgement.getColor())
                        .append(Component.text("+" + fireBonus, NamedTextColor.RED))
                        .append(Component.text(" points", judgement.getColor())),
                PlayingFieldScorer.getScoreTitleTimes());
        field.sendTitleToPlayers(title);
    }

    @Override
    public void correctBlocksVisual(Wall wall) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(field);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(field);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(field);
        Set<Block> fireBlocks = new HashSet<>();

        for (Block block : correctBlocks.values()) {
            if (block.getType() == Material.FIRE) {
                Block supportingBlock = block.getRelative(BlockFace.DOWN);
                extraBlocks.remove(field.blockToCoordinates(supportingBlock));
                fireBlocks.add(block);
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
        for (Block block : fireBlocks) {
            block.setType(Material.FIRE);
        }
    }

    @Override
    public void end() {
        super.end();
        field.sendTitleToPlayers(Component.empty(), Component.text("Fire no longer gives a point bonus!"), 0, 20, 10);
    }

    private static ItemStack flintAndSteel() {
        ItemStack item = new ItemStack(Material.FLINT_AND_STEEL);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(PlayingField.variableKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(PlayingField.gameKey, PersistentDataType.BOOLEAN, true);
        meta.lore(Collections.singletonList(miniMessage.deserialize("<gray>Temporary item")));
        item.setItemMeta(meta);
        return item;
    }

    public FireInTheHole copy() {
        return new FireInTheHole();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_GHAST_SCREAM, 1, 1);
    }

    @Override
    public void playDeactivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_BLAZE_SHOOT, 1);
    }
}
