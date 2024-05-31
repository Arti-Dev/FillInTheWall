package com.articreep.holeinthewall.multiplayer;

import com.articreep.holeinthewall.HoleInTheWall;
import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.PlayingFieldManager;
import com.articreep.holeinthewall.Gamemode;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

public class MultiplayerGame {
    private final Set<PlayingField> playingFields = new HashSet<>();
    private final WallGenerator generator;
    private int time;
    private BukkitTask task;

    public MultiplayerGame(PlayingField field) {
        playingFields.add(field);
        generator = new WallGenerator(field.getLength(), field.getHeight(), 2, 4);
    }

    public void start() {
        time = 20 * 60;
        for (PlayingField field : playingFields) {
            field.stop();
            field.doTickScorer(false);
            field.getQueue().setGenerator(generator);
            generator.addQueue(field.getQueue());
            field.start(Gamemode.MULTIPLAYER_SCORE_ATTACK);
        }
        generator.addNewWallToQueues();
        task = tickLoop();

    }

    private BukkitTask tickLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayingField field : playingFields) {
                    if (!field.hasStarted()) continue;
                    field.getScorer().setTime(time);
                    field.getScorer().tick();
                }

                if (time <= 0) stop();
                time--;
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }

        for (PlayingField field : playingFields) {
            // todo temporary broadcast
            Bukkit.broadcastMessage(field.getPlayers().toString() + "with " + field.getScorer().getFinalScore() + " score");
            field.stop();
            field.getQueue().resetGenerator();
            field.doTickScorer(true);
        }

        // todo temporary
        PlayingFieldManager.game = null;
    }

    private boolean verifyFieldDimensions() {
        int length = -1;
        int height = -1;
        for (PlayingField field : playingFields) {
            if (length == -1) {
                length = field.getLength();
                height = field.getHeight();
            } else if (length != field.getLength() || height != field.getHeight()) {
                return false;
            }
        }
        return true;
    }

    public boolean addPlayingField(PlayingField field) {
        if (field.getLength() == generator.getLength() && field.getHeight() == generator.getHeight()) {
            playingFields.add(field);
            return true;
        } else {
            return false;
        }
    }
}
