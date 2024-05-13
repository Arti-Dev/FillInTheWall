package com.articreep.holeinthewall;

import com.articreep.holeinthewall.modifiers.ModifierEvent;
import com.articreep.holeinthewall.modifiers.Rush;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.javatuples.Pair;

import java.util.Map;

public class PlayingFieldScorer {
    Player player;
    PlayingField field;
    private int score = 0;
    private double bonus = 0;
    private int wallsCleared = 0;

    public PlayingFieldScorer(PlayingField field) {
        this.field = field;
        this.player = field.getPlayer();
    }

    public PlayingFieldState scoreWall(Wall wall, PlayingField field) {

        int score = calculateScore(wall, field);
        this.score += score;

        double percent = calculatePercent(wall, score);

        String title = "";
        ChatColor color = ChatColor.GREEN;
        Material border = field.getDefaultBorderMaterial();
        if (percent == 1) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            color = ChatColor.GOLD;
            title = ChatColor.BOLD + "PERFECT!";
            border = Material.GLOWSTONE;
            wallsCleared++;
        } else {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
        }
        if (percent < 1 && percent >= 0.5) {
            title = "Cool!";
            border = Material.LIME_CONCRETE;
        } else if (percent < 0.5) {
            title = "Meh..";
            color = ChatColor.RED;
            border = Material.REDSTONE_BLOCK;
        }

        // Add/subtract to bonus and maybe even trigger rush
        if (percent >= 0.5) {
            bonus += percent;
            if (bonus >= 10) {
                bonus = 0;
                // tell playingfield to not show title
                // todo temporary
                title = null;
                // activate rush next tick
                Bukkit.getScheduler().runTask(HoleInTheWall.getInstance(), () -> {
                    field.activateEvent(new Rush(field));
                });
            }
        } else {
            bonus -= 2;
            if (bonus < 0) bonus = 0;
        }

        return new PlayingFieldState(border, color, title, score);
    }

    public int calculateScore(Wall wall, PlayingField field) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(field);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(field);

        // Check score
        return correctBlocks.size() - extraBlocks.size();
    }

    public void scoreEvent(ModifierEvent event) {
        if (event instanceof Rush rush) {
            int rushResults = rush.getBoardsCleared() * 4;
            field.overrideScoreDisplay(80, ChatColor.RED + "+" + ChatColor.BOLD + rushResults + " points from Rush!!!");
            score += rushResults;
        }
    }

    public double calculatePercent(Wall wall, int score) {
        return (double) score / wall.getHoles().size();
    }

    public double calculatePercent(Wall wall, PlayingField field) {
        return (double) calculateScore(wall, field) / wall.getHoles().size();
    }

    public void addScore(int score) {
        this.score += score;
    }

    public int getWallsCleared() {
        return wallsCleared;
    }

    public int getScore() {
        return score;
    }

    public double getBonus() {
        return bonus;
    }
}
