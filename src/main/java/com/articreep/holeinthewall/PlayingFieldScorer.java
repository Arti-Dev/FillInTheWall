package com.articreep.holeinthewall;

import com.articreep.holeinthewall.modifiers.ModifierEvent;
import com.articreep.holeinthewall.modifiers.Rush;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.javatuples.Pair;

import java.util.Map;

public class PlayingFieldScorer {
    PlayingField field;
    private int score = 0;
    private int finalScore = 0;
    private double meter = 0;
    private int wallsCleared = 0;
    // time in ticks
    private int time = 0;
    private Gamemode gamemode = Gamemode.INFINITE;

    // Levels (if enabled)
    boolean doLevels = false;
    private int level = 1;
    private int meterMax = 10;

    public PlayingFieldScorer(PlayingField field) {
        this.field = field;
    }

    public Judgement scoreWall(Wall wall, PlayingField field) {

        int score = calculateScore(wall, field);
        this.score += score;

        double percent = calculatePercent(wall, score);
        Judgement judgement = Judgement.MISS;

        // Determine judgement
        for (Judgement j : Judgement.values()) {
            if (percent >= j.getPercent()) {
                judgement = j;
                break;
            }
        }

        if (judgement == Judgement.PERFECT) wallsCleared++;

        boolean showScoreTitle = true;
        // Add/subtract to bonus
        if (percent >= Judgement.COOL.getPercent()) {
            meter += percent;
            if (meter >= meterMax) {
                meter = 0;
                showScoreTitle = false;

                if (doLevels) {
                    setLevel(level + 1);
                    for (Player player : field.getPlayers()) {
                        player.sendTitle("", ChatColor.GREEN + "Level up!", 0, 10, 5);
                    }
                // todo replace with attribute check
                } else if (gamemode == Gamemode.RAPID_SCORE_ATTACK || gamemode == Gamemode.INFINITE) {
                    // activate rush next tick
                    Bukkit.getScheduler().runTask(HoleInTheWall.getInstance(),
                            () -> field.activateEvent(new Rush(field)));
                }
            }
        } else {
            // You cannot lose progress if levels are enabled
            if (!doLevels) {
                meter -= 1;
            }

            if (meter < 0) meter = 0;
        }

        if (showScoreTitle) displayScoreTitle(judgement, score);

        return judgement;
    }

    public void displayScoreTitle(Judgement judgement, int score) {
        for (Player player : field.getPlayers()) {
            player.sendTitle(
                    judgement.getColor() + judgement.getText(),
                    judgement.getColor() + "+" + score + " points",
                    0, 10, 5);
            player.playSound(player, judgement.getSound(), 1, 1);
        }
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

    public double getMeter() {
        return meter;
    }

    public void reset() {
        score = 0;
        meter = 0;
        wallsCleared = 0;
        time = 0;
        gamemode = null;
        level = 1;
        doLevels = false;
    }

    public void resetFinalScore() {
        finalScore = 0;
    }

    public String getFormattedTime() {
        return String.format("%02d:%02d", (time/20) / 60, (time/20) % 60);
    }

    public int getRawTime() {
        return time;
    }

    public void tick() {
        // todo assign "attributes" to gamemodes, such as "singleplayer" and "multiplayer"
        // instead of checking each gamemode

        // if an timefreeze modifier event is active and we're in a singleplayer game, pause the timer
        if (field.eventActive() && field.getEvent().timeFreeze
                && gamemode == Gamemode.RAPID_SCORE_ATTACK) return;
        // if we're in a score attack game, decrement the time
        if (gamemode == Gamemode.SCORE_ATTACK || gamemode == Gamemode.RAPID_SCORE_ATTACK
                || gamemode == Gamemode.MULTIPLAYER_SCORE_ATTACK) time--;
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
            } else if (time <= 20 * 10 && time % 20 == 0) {
                for (Player player : field.getPlayers()) {
                    player.sendTitle("", ChatColor.RED + String.valueOf(time / 20), 0, 20, 5);
                }
            }
        }

        if (gamemode == Gamemode.RAPID_SCORE_ATTACK /* todo temporary remove later */ || gamemode == Gamemode.MULTIPLAYER_SCORE_ATTACK) {
            if (time <= 0) {
                for (Player player : field.getPlayers()) {
                    player.sendMessage(ChatColor.RED + "Time's up!");
                }
                field.stop();
            }
            if (time == 20 * 20) {
                for (Player player : field.getPlayers()) {
                    player.sendTitle("", ChatColor.YELLOW + "20 seconds remaining!", 0, 40, 5);
                }
            } else if (time <= 20 * 10 && time % 20 == 0) {
                for (Player player : field.getPlayers()) {
                    player.sendTitle("", ChatColor.RED + String.valueOf(time / 20), 0, 20, 5);
                }
            }
        }
    }

    public void saveFinalScore() {
        finalScore = score;
    }

    public void announceFinalScore() {
        for (Player player : field.getPlayers()) {
            player.sendMessage(ChatColor.GREEN + "Your final score is " + ChatColor.BOLD + finalScore);
        }
    }

    public void setGamemode(Gamemode gamemode) {
        this.gamemode = gamemode;
        if (gamemode == Gamemode.SCORE_ATTACK) {
            time = 20 * 120;
            doLevels = true;
            field.getQueue().setRandomizeFurther(false);
            setLevel(1);
        } else if (gamemode == Gamemode.INFINITE) {
            doLevels = false;
            field.getQueue().setRandomizeFurther(true);
            field.getQueue().setRandomHoleCount(2);
            field.getQueue().setConnectedHoleCount(4);
            field.getQueue().setWallActiveTime(160);
            setMeterMax(10);
        } else if (gamemode == Gamemode.RAPID_SCORE_ATTACK) {
            time = 20 * 60;
            doLevels = false;
            field.getQueue().setRandomizeFurther(false);
            field.getQueue().setRandomHoleCount(1);
            field.getQueue().setConnectedHoleCount(2);
            field.getQueue().setWallActiveTime(160);
            setMeterMax(5);
        }
    }

    // levels
    public void setMeterMax(int meterMax) {
        this.meterMax = meterMax;
    }

    public String getFormattedMeter() {
        // todo add the type of meter to the string - such as "rush meter" or "freeze meter"
        double percentFilled = meter / meterMax;
        ChatColor color;
        if (percentFilled <= 0.3) {
            color = ChatColor.GRAY;
        } else if (percentFilled <= 0.7) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.GREEN;
        }
        return color + "Meter: " + String.format("%.2f", meter) + "/" + meterMax;
    }

    public void setLevel(int level) {
        this.level = level;
        setDifficulty(level);
        setMeterMax(level);
        // when we level up, delete all pending walls in the queue and force a new wall to be made.
        // todo subject to change
        field.getQueue().clearHiddenWalls();
    }

    private void setDifficulty(int level) {
        WallQueue queue = field.getQueue();
        queue.setWallActiveTime(Math.max(200 - level * 20, 60));

        if (level == 1) {
            queue.setRandomHoleCount(1);
            queue.setConnectedHoleCount(0);
        } else if (level == 2) {
            queue.setRandomHoleCount(1);
            queue.setConnectedHoleCount(1);
        } else {
            int remainingHoles = (level/2) + 1;
            if (level >= 5) {
                queue.setRandomHoleCount(2);
                remainingHoles -= 2;
            } else {
                queue.setRandomHoleCount(1);
                remainingHoles -= 1;
            }
            queue.setConnectedHoleCount(remainingHoles);
        }
    }

    public int getMeterMax() {
        return meterMax;
    }

    public int getLevel() {
        return level;
    }

    public Gamemode getGamemode() {
        return gamemode;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public int getFinalScore() {
        return finalScore;
    }
}
