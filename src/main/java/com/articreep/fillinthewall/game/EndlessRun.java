package com.articreep.fillinthewall.game;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.multiplayer.WallGenerator;

import java.util.Random;

public class EndlessRun {
    public EndlessRun(PlayingFieldScorer scorer) {
        if (scorer == null) throw new IllegalArgumentException("Scorer cannot be null");
        this.scorer = scorer;
    }

    int currentPhase = 1;

    public static final int earlyMinimumWallTime = 120;
    public static final int earlyMaxHoles = 7;

    public static final int maxHoles = 10;
    // Min holes is only relevant if randomizeFurther is enabled
    public static final int minHoles = 1;
    public static final int minimumWallTime = 80;
    public static final double randomizeFurtherChance = 0.2;
    private PlayingFieldScorer scorer;


    public void randomQueueDifficulty() {
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
                wallTime -= 20;
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

        queue.setMinimumHoleCount(minPhaseHoles);
        int randomHoleCount = rollRandomHoleCount(maxPhaseHoles);
        int connectedHoleCount = maxPhaseHoles - randomHoleCount;
        queue.setRandomHoleCount(randomHoleCount);
        queue.setConnectedHoleCount(connectedHoleCount);
        queue.setWallActiveTime(wallTime);
        queue.setRandomizeFurther(randomizeFurther);

        FillInTheWall.getInstance().getSLF4JLogger()
                .info("R{}C{} for total {}, WT{}, RandFurther{}, MinHoles{}",
                randomHoleCount, connectedHoleCount, maxPhaseHoles, wallTime, randomizeFurther, minPhaseHoles);
    }

    public int rollRandomHoleCount(int maxHoles) {
        Random random = new Random();
        // If below 4, choose randomly from 1-3. If not, 2-4.
        if (maxHoles <= 3) {
            return (int) (random.nextDouble() * maxHoles + 1);
        } else {
            return random.nextInt(2, 5);
        }
    }
}
