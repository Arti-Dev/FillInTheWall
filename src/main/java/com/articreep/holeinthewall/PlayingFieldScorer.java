package com.articreep.holeinthewall;

import com.articreep.holeinthewall.menu.Gamemode;
import com.articreep.holeinthewall.modifiers.ModifierEvent;
import com.articreep.holeinthewall.modifiers.Rush;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;

public class PlayingFieldScorer {
    PlayingField field;
    private int score = 0;
    private double bonus = 0;
    private int wallsCleared = 0;
    // time in ticks
    private int time = 0;
    private Gamemode gamemode;

    public PlayingFieldScorer(PlayingField field) {
        this.field = field;
    }

    public PlayingFieldState scoreWall(Wall wall, PlayingField field) {

        int score = calculateScore(wall, field);
        this.score += score;

        double percent = calculatePercent(wall, score);
        // todo this basically serves the same purpose as percent and should be tweaked
        Judgement judgement = Judgement.MISS;

        String title = "";
        ChatColor color = ChatColor.GREEN;
        Material border = field.getDefaultBorderMaterial();
        if (percent == 1) {
            for (Player player : field.getPlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            }
            color = ChatColor.GOLD;
            title = ChatColor.BOLD + "PERFECT!";
            border = Material.GLOWSTONE;
            judgement = Judgement.PERFECT;
            wallsCleared++;
        } else {
            for (Player player : field.getPlayers()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
            }
        }
        if (percent < 1 && percent >= 0.5) {
            title = "Cool!";
            border = Material.LIME_CONCRETE;
            judgement = Judgement.COOL;
        } else if (percent < 0.5) {
            title = "Miss..";
            color = ChatColor.RED;
            border = Material.REDSTONE_BLOCK;
            judgement = Judgement.MISS;
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

        return new PlayingFieldState(border, color, title, score, judgement);
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

    public void reset() {
        score = 0;
        bonus = 0;
        wallsCleared = 0;
        time = 0;
        gamemode = null;
    }

    public String getFormattedTime() {
        return String.format("%02d:%02d", (time/20) / 60, (time/20) % 60);
    }

    public int getRawTime() {
        return time;
    }

    public void tick() {
        if (gamemode == Gamemode.SCORE_ATTACK) time--;
        else time++;

        if (gamemode == Gamemode.SCORE_ATTACK) {
            if (time <= 0) {
                for (Player player : field.getPlayers()) {
                    player.sendMessage(ChatColor.RED + "Time's up!");
                }
                field.stop();
            } else if (time == 20 * 60) {
                for (Player player : field.getPlayers()) {
                    player.sendTitle("",ChatColor.YELLOW + "1 minute remaining!", 0, 40, 5);
                }
            } else if (time == 20 * 30) {
                for (Player player : field.getPlayers()) {
                    player.sendTitle("", ChatColor.YELLOW + "30 seconds remaining!", 0, 40, 5);
                }
            } else if (time < 20 * 10 && time % 20 == 0) {
                for (Player player : field.getPlayers()) {
                    player.sendTitle("", ChatColor.RED + String.valueOf(time / 20), 0, 20, 5);
                }
            }
        }
    }

    public void announceFinalScore() {
        for (Player player : field.getPlayers()) {
            player.sendMessage(ChatColor.GREEN + "Your final score was " + ChatColor.BOLD + score);
        }

    }

    public void setGamemode(Gamemode gamemode) {
        this.gamemode = gamemode;
        if (gamemode == Gamemode.SCORE_ATTACK) {
            time = 20 * 120;
        }
    }
}
