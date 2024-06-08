package com.articreep.holeinthewall;

import com.articreep.holeinthewall.modifiers.Freeze;
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
    private double meter = 0;
    private int wallsCleared = 0;
    // time in ticks
    private int time = 0;
    private Gamemode gamemode = Gamemode.INFINITE;

    // Levels (if enabled)
    boolean doLevels = false;
    private int level = 1;
    private int meterMax = 10;

    // Multiplayer variables
    private int position;
    private int pointsBehind;

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
        if (percent >= Judgement.COOL.getPercent() && !field.eventActive()) {
            meter += percent;
            if (meter > meterMax) meter = meterMax;

            if (meter >= meterMax && !((boolean) gamemode.getAttribute(GamemodeAttribute.MANUAL_METER))) {
                showScoreTitle = false;

                if (doLevels) {
                    setLevel(level + 1);
                    field.sendTitleToPlayers("", ChatColor.GREEN + "Level up!", 0, 10, 5);
                } else {
                    activateEvent();
                }
            }
        } else if (!field.eventActive()) {
            // You cannot lose progress if levels are enabled
            if (!doLevels) {
                meter -= 1;
            }

            if (meter < 0) meter = 0;
        }

        if (showScoreTitle) displayScoreTitle(judgement, score);
        playJudgementSound(judgement);

        return judgement;
    }

    /**
     * Attempts to activate the event associated with the current gamemode.
     * Will return 0 if meter is not full enough, -1 if there is no event to activate
     */
    public int activateEvent() {
        if (gamemode.getModifier() == null) return -1;
        double percent = meter / meterMax;

        // todo could use reflection
        if (gamemode.getModifier() == Rush.class && percent >= 1) {
            // activate rush next tick
            Bukkit.getScheduler().runTask(HoleInTheWall.getInstance(),
                    () -> field.activateEvent(new Rush(field)));

        } else if (gamemode.getModifier() == Freeze.class && percent >= 0.2) {
            Bukkit.getScheduler().runTask(HoleInTheWall.getInstance(),
                    () -> field.activateEvent(new Freeze(field, (int) (20*10*percent))));
        } else {
            return 0;
        }
        meter = 0;
        return 1;
    }

    public void displayScoreTitle(Judgement judgement, int score) {
        field.sendTitleToPlayers(
                judgement.getColor() + judgement.getText(),
                judgement.getColor() + "+" + score + " points",
                0, 10, 5);
    }

    public void playJudgementSound(Judgement judgement) {
        for (Player player : field.getPlayers()) {
            player.playSound(player.getLocation(), judgement.getSound(), 1, 1);
        }
    }

    public int calculateScore(Wall wall, PlayingField field) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(field);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(field);

        // Check score
        int points = correctBlocks.size() - extraBlocks.size();
        if (points < 0) points = 0;
        return points;
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

    public String getFormattedTime() {
        return String.format("%02d:%02d", (time/20) / 60, (time/20) % 60);
    }

    public int getRawTime() {
        return time;
    }

    public void tick() {

        // if a timefreeze modifier event is active and we're in a singleplayer game, pause the timer
        if (field.eventActive() && field.getEvent().timeFreeze
                && gamemode.hasAttribute(GamemodeAttribute.SINGLEPLAYER)) return;
        // if we're in a score attack game, decrement the time
        if (gamemode.hasAttribute(GamemodeAttribute.TIME_LIMIT)) time--;
        else time++;

        if (gamemode.hasAttribute(GamemodeAttribute.TIME_LIMIT)) {
            if ((int) gamemode.getAttribute(GamemodeAttribute.TIME_LIMIT) >= 120 * 20) {
                if (time <= 0) {
                    field.sendMessageToPlayers(ChatColor.RED + "Time's up!");
                    field.stop();
                } else if (time == 20 * 60) {
                    field.sendTitleToPlayers("", ChatColor.YELLOW + "1 minute remaining!", 0, 40, 5);
                } else if (time == 20 * 30) {
                    field.sendTitleToPlayers("", ChatColor.YELLOW + "30 seconds remaining!", 0, 40, 5);
                } else if (time <= 20 * 10 && time % 20 == 0) {
                    field.sendTitleToPlayers("", ChatColor.RED + String.valueOf(time / 20), 0, 20, 5);
                }
            } else {
                if (time <= 0) {
                    field.sendMessageToPlayers(ChatColor.RED + "Time's up!");
                    field.stop();
                }
                if (time == 20 * 20) {
                    field.sendTitleToPlayers("", ChatColor.YELLOW + "20 seconds remaining!", 0, 40, 5);
                } else if (time <= 20 * 10 && time % 20 == 0) {
                    field.sendTitleToPlayers("", ChatColor.RED + String.valueOf(time / 20), 0, 20, 5);
                }
            }
        }
    }

    public void announceFinalScore() {
        field.sendMessageToPlayers(ChatColor.GREEN + "Your final score is " + ChatColor.BOLD + score);
    }

    public void setGamemode(Gamemode gamemode) {
        for (GamemodeAttribute attribute : GamemodeAttribute.values()) {
            Object value = gamemode.getAttribute(attribute);
            if (value == null) continue;

            switch (attribute) {
                case TIME_LIMIT -> setTime((int) value);
                case DO_LEVELS -> {
                    doLevels = (boolean) value;
                    if (doLevels) setLevel(1);
                }
                case CONSISTENT_HOLE_COUNT -> {
                    if (!doLevels) field.getQueue().setRandomizeFurther(!(boolean) value);
                }
                case RANDOM_HOLE_COUNT -> {
                    if (!doLevels) field.getQueue().setRandomHoleCount((int) value);
                }
                case CONNECTED_HOLE_COUNT -> {
                    if (!doLevels) field.getQueue().setConnectedHoleCount((int) value);
                }
                case STARTING_WALL_ACTIVE_TIME -> {
                    if (!doLevels) field.getQueue().setWallActiveTime((int) value);
                }
                case METER_MAX -> {
                    if (!doLevels) setMeterMax((int) value);
                }
            }
        }
        this.gamemode = gamemode;
    }

    // levels
    public void setMeterMax(int meterMax) {
        this.meterMax = meterMax;
    }

    public String getFormattedMeter() {
        double percentFilled = meter / meterMax;
        ChatColor color;
        String modifier = "";
        if (gamemode.getModifier() != null) modifier = gamemode.getModifier().getSimpleName() + " ";
        if (percentFilled <= 0.3) {
            color = ChatColor.GRAY;
        } else if (percentFilled <= 0.7) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.GREEN;
        }
        return color + modifier + "Meter: " + String.format("%.2f", meter) + "/" + meterMax;
    }

    public void setLevel(int level) {
        field.getQueue().setRandomizeFurther(false);
        this.level = level;
        setDifficulty(level);
        setMeterMax(level);
        // when we level up, delete all pending walls in the queue which forces a new wall to be made.
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

    public int getLevel() {
        return level;
    }

    public Gamemode getGamemode() {
        return gamemode;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    public void setPointsBehind(int pointsBehind) {
        this.pointsBehind = pointsBehind;
    }

    public int getPointsBehind() {
        return pointsBehind;
    }
}
