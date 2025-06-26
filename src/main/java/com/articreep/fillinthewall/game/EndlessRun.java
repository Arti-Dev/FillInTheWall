package com.articreep.fillinthewall.game;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.modifiers.ModifierEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

import java.util.Random;

public class EndlessRun {
    public EndlessRun(PlayingFieldScorer scorer) {
        if (scorer == null) throw new IllegalArgumentException("Scorer cannot be null");
        this.scorer = scorer;
        increaseScoreToNextLevel();
    }

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    int currentPhase = 0;
    public int scoreToNextLevel = 0;

    public static final int earlyMinimumWallTime = 120;
    public static final int earlyMaxHoles = 7;

    public static final int maxHoles = 10;
    // Min holes is only relevant if randomizeFurther is enabled
    public static final int minHoles = 1;
    public static final int minimumWallTime = 40;
    public static final double randomizeFurtherChance = 0.3;
    private final PlayingFieldScorer scorer;

    private int eventDrought = 0;
    public static final int eventLength = 60 * 20;

    // Transitions the game into the next phase.
    // The queue/active walls will be frozen for a short time
    // The current phase will be incremented and the queue difficulty randomized
    // todo The current island build will be changed to a random one
    // todo A title will be sent to the player with a random symbol
    // todo A random sound will be played
    public void nextPhase() {
        int pauseTicks = 40;

        scorer.field.endEvent();
        WallQueue queue = scorer.field.getQueue();
        queue.pauseTicking(pauseTicks);

        increaseScoreToNextLevel();
        randomQueueDifficulty();
        scorer.field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize(
                "<gradient:#5e4fa2:#f79459:red>placeholder</gradient>"),
                0, 5, 15);

        boolean doRandomEvent = rollEventProbability();
        if (doRandomEvent) {
            Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
                ModifierEvent event = ModifierEvent.Type.RANDOM_ENDLESS.createEvent();
                event.setPlayingField(scorer.field);
                event.setTicksRemaining(eventLength);
                event.activate();
            }, pauseTicks - 5);
        }
    }

    private void increaseScoreToNextLevel() {
        currentPhase++;
        if (currentPhase == 1) scoreToNextLevel += 25;
        else if (currentPhase == 2) scoreToNextLevel += 50;
        else if (currentPhase <= 10) scoreToNextLevel += 75;
        else if (currentPhase <= 20) scoreToNextLevel += 100;
        else scoreToNextLevel += 125;
    }

    private void randomQueueDifficulty() {
        WallQueue queue = scorer.field.getQueue();

        GamemodeSettings settings = scorer.getSettings();
        int maxPhaseHoles;
        // Min holes is only relevant if randomizeFurther is enabled
        int minPhaseHoles;
        int wallTime;
        boolean randomizeFurther;
        Random random = new Random();

        if (currentPhase <= 2) {
            maxPhaseHoles = settings.getIntAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT) +
                    settings.getIntAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT);
            wallTime = settings.getIntAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME);
            randomizeFurther = !settings.getBooleanAttribute(GamemodeAttribute.CONSISTENT_HOLE_COUNT);
            minPhaseHoles = 3;
            if (currentPhase == 2) {
                maxPhaseHoles += 1;
                wallTime -= 40;
            }
        } else if (currentPhase <= 4) {
            maxPhaseHoles = random.nextInt(3, earlyMaxHoles + 1);
            wallTime = random.nextInt(earlyMinimumWallTime, 200);
            randomizeFurther = random.nextDouble() < randomizeFurtherChance;
            minPhaseHoles = 3;
        } else {
            maxPhaseHoles = random.nextInt(1, maxHoles + 1);
            wallTime = random.nextInt(minimumWallTime + (maxPhaseHoles * 4), 200);
            randomizeFurther = random.nextDouble() < randomizeFurtherChance;
            minPhaseHoles = minHoles;
        }

        queue.clearAllWalls();
        queue.setMinimumHoleCount(minPhaseHoles);
        int randomHoleCount = rollRandomHoleCount(maxPhaseHoles);
        int connectedHoleCount = maxPhaseHoles - randomHoleCount;
        queue.setRandomHoleCount(randomHoleCount);
        queue.setConnectedHoleCount(connectedHoleCount);
        queue.setWallActiveTime(wallTime);
        queue.setRandomizeFurther(randomizeFurther);

        // debug
        FillInTheWall.getInstance().getSLF4JLogger()
                .info("R{}C{} for total {}, WT{}, RandFurther{}, MinHoles{}",
                randomHoleCount, connectedHoleCount, maxPhaseHoles, wallTime, randomizeFurther, minPhaseHoles);
    }

    private int rollRandomHoleCount(int maxHoles) {
        Random random = new Random();
        // If below 4, choose randomly from 1-3. If not, 2-4.
        if (maxHoles <= 3) {
            return (int) (random.nextDouble() * maxHoles + 1);
        } else {
            return random.nextInt(2, 5);
        }
    }

    private boolean rollEventProbability() {
        if (currentPhase < 3) return false;
        Random random = new Random();
        boolean result = (eventDrought * (1/3f)) > random.nextDouble();
        if (!result) eventDrought++;
        else eventDrought = 0;
        return result;
    }
}
