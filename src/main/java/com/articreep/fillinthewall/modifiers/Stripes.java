package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.game.Judgement;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.game.PlayingFieldScorer;
import com.articreep.fillinthewall.game.Wall;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.javatuples.Pair;

import java.util.HashMap;
import java.util.Map;

public class Stripes extends ModifierEvent {
    public Stripes() {
        super();
        modifyWalls = true;
        overrideBonusCalculation = true;
        overrideScoreTitle = true;
    }

    @Override
    public void activate() {
        super.activate();
        Wall wall = field.getQueue().getFrontmostWall();
        if (wall != null) wall.setStripes(true);
        addTemporaryItemToPlayers(altWallMaterial());
        field.sendTitleToPlayers(miniMessage.deserialize("<dark_aqua>Stripes!"),
                miniMessage.deserialize("Match colors for bonus points!"), 0, 40, 10);
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
        Title title = Title.title(judgement.getFormattedText(),
                Component.text(score + bonus.get(PlayingFieldScorer.BonusType.PERFECT), judgement.getColor())
                        .append(Component.text("+" + stripeBonus, NamedTextColor.DARK_PURPLE))
                        .append(Component.text(" points", judgement.getColor())),
                PlayingFieldScorer.getScoreTitleTimes());
        field.sendTitleToPlayers(title);
    }

    @Override
    public void end() {
        super.end();
        Wall wall = field.getQueue().getFrontmostWall();
        if (wall != null) wall.setStripes(false);
        field.sendTitleToPlayers(Component.empty(), Component.text("Stripes are gone!"), 0, 20, 10);
    }

    @Override
    public void modifyWall(Wall wall) {
        wall.setStripes(true);
    }

    private ItemStack altWallMaterial() {
        ItemStack item = new ItemStack(Utils.getAlternateMaterial(field.getWallMaterial()));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(PlayingField.variableKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(PlayingField.gameKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public Stripes copy() {
        return new Stripes();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
    }

    @Override
    public void playDeactivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_VILLAGER_WORK_LEATHERWORKER, 1, 1);
    }
}
