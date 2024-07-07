package com.articreep.holeinthewall.multiplayer;

import com.articreep.holeinthewall.Gamemode;
import com.articreep.holeinthewall.HoleInTheWall;
import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.PlayingFieldManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public abstract class MultiplayerGame {
    protected final Set<PlayingField> playingFields = new HashSet<>();
    protected final List<PlayingField> rankings = new ArrayList<>();
    protected final WallGenerator generator;
    protected int time;
    protected BukkitTask task;

    public MultiplayerGame(List<PlayingField> fields) {
        if (fields.isEmpty()) {
            Bukkit.getLogger().severe("Tried to create multiplayer game with no playing fields");
        }
        playingFields.addAll(fields);
        // Use the first field to make the generator
        PlayingField field = fields.getFirst();
        generator = new WallGenerator(field.getLength(), field.getHeight(), 3, 0, 160);
        generator.setRandomizeFurther(false);
        generator.setWallHolesMax(8);
        generator.setWallTimeDecrease(10);
        generator.setWallTimeDecreaseInterval(2);
        generator.setWallHolesIncreaseInterval(2);
    }

    public void start() {
        if (playingFields.isEmpty()) {
            Bukkit.getLogger().severe("Tried to start multiplayer game with no playing fields");
            return;
        }

        if (!verifyFieldDimensions()) {
            Bukkit.getLogger().severe("Not all playing fields have the same dimensions!");
            return;
        }

        // Make sure all games here have stopped completely
        for (PlayingField field : playingFields) {
            field.stop();
            field.doTickScorer(false);
            field.setLocked(true);
        }

        new BukkitRunnable() {
            int i = 3;
            @Override
            public void run() {
                for (PlayingField field : playingFields) {
                    if (i == 3) {
                        field.sendTitleToPlayers(ChatColor.BLUE + "\uD83D\uDC65", ChatColor.GREEN + "Multiplayer game starting in 3", 0, 30, 0);
                    } else if (i == 2) {
                        field.sendTitleToPlayers(ChatColor.BLUE + "\uD83D\uDC65", ChatColor.YELLOW + "Multiplayer game starting in 2", 0, 30, 0);
                    } else if (i == 1) {
                        field.sendTitleToPlayers(ChatColor.BLUE + "\uD83D\uDC65", ChatColor.RED + "Multiplayer game starting in 1", 0, 30, 0);
                    } else if (i == 0) {
                        field.sendTitleToPlayers(ChatColor.GREEN + "GO!", "", 0, 5, 3);
                        field.playSoundToPlayers(Sound.BLOCK_BELL_USE, 0.5f);
                    }
                }

                if (i == 0) {
                    startGame();
                    cancel();
                }
                i--;
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 20);
    }

    protected boolean verifyFieldDimensions() {
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

    protected void startGame() {
        if (task != null) {
            Bukkit.getLogger().severe("Tried to start multiplayer game that's already been started");
            return;
        }
        for (PlayingField field : playingFields) {
            try {
                field.start(getGamemode(), generator);
                field.getScorer().setPlayerCount(playingFields.size());
                generator.addQueue(field.getQueue());
            } catch (IllegalStateException e) {
                removePlayingfield(field);
            }
        }
        generator.addNewWallToQueues();
        task = tickLoop();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }

        rankPlayingFields();
        broadcastResults();

        for (PlayingField field : playingFields) {
            field.stop();
            field.getQueue().resetGenerator();
            field.doTickScorer(true);
        }

        // todo temporary
        PlayingFieldManager.game = null;
    }

    public abstract Gamemode getGamemode();

    protected abstract BukkitTask tickLoop();

    protected abstract void rankPlayingFields();

    protected abstract void broadcastResults();

    public boolean addPlayingField(PlayingField field) {
        if (field.getLength() == generator.getLength() && field.getHeight() == generator.getHeight()) {
            playingFields.add(field);
            return true;
        } else {
            return false;
        }
    }

    public void removePlayingfield(PlayingField field) {
        if (playingFields.contains(field)) {
            playingFields.remove(field);
            field.setLocked(false);
            field.getQueue().resetGenerator();
            field.doTickScorer(true);
        }
    }

}
