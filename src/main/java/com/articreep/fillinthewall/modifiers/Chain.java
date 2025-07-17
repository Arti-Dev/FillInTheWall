package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.game.Judgement;
import com.articreep.fillinthewall.game.PlayingFieldScorer;
import com.articreep.fillinthewall.game.Wall;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;

import java.util.HashMap;

public class Chain extends ModifierEvent {
    public Chain() {
        super();
        overrideBonusCalculation = true;
        overrideScoreTitle = true;
    }

    @Override
    public ModifierEvent copy() {
        return new Chain();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1, 1);
    }

    @Override
    public void playDeactivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_ALLAY_ITEM_TAKEN, 1, 1);
    }

    @Override
    public void activate() {
        super.activate();
        field.getScorer().breakPerfectWallChain();
        field.sendTitleToPlayers(miniMessage.deserialize("<dark_aqua>Chain!"),
                Component.text("Keep perfecting walls to rack up points!"), 0, 40, 10);
    }

    @Override
    public void end() {
        super.end();
        field.sendTitleToPlayers(Component.empty(), Component.text("Chains are over!"),
                0, 20, 10);
    }

    @Override
    public HashMap<PlayingFieldScorer.BonusType, Integer> evaluateBonus(double percent, Wall wall) {
        HashMap<PlayingFieldScorer.BonusType, Integer> map = super.evaluateBonus(percent, wall);
        // For a perfect wall chain, award chain-1 points, with a minimum of 0
        // This calculation always runs after the chain has been updated

        if (percent < 1) {
            map.put(PlayingFieldScorer.BonusType.CHAIN, 0);
        } else {
            int chain = field.getScorer().getPerfectWallChain() - 1;
            map.put(PlayingFieldScorer.BonusType.CHAIN, chain);
        }
        return map;
    }

    @Override
    public void displayScoreTitle(Judgement judgement, int score, HashMap<PlayingFieldScorer.BonusType, Integer> bonus) {
        int chainBonus = bonus.get(PlayingFieldScorer.BonusType.CHAIN);
        Title title = Title.title(judgement.getFormattedText(),
                Component.text(score + bonus.get(PlayingFieldScorer.BonusType.PERFECT), judgement.getColor())
                        .append(Component.text("+" + chainBonus, NamedTextColor.DARK_AQUA))
                        .append(Component.text(" points", judgement.getColor())),
                PlayingFieldScorer.getScoreTitleTimes());
        field.sendTitleToPlayers(title);

        // Play a sound as well
        if (judgement == Judgement.PERFECT && chainBonus > 1) {
            double pitch = Math.pow(2, (chainBonus - 1) / 12f);
            pitch = Math.min(2, pitch);
            field.playSoundToPlayers(Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1, (float) pitch);
        } else if (judgement != Judgement.PERFECT) {
            field.playSoundToPlayers(Sound.ENTITY_ALLAY_HURT, 1, 1);
        }
    }
}
