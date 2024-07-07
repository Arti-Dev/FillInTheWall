package com.articreep.holeinthewall.multiplayer;

import com.articreep.holeinthewall.*;
import com.articreep.holeinthewall.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ScoreAttackGame extends MultiplayerGame {
    private final Gamemode gamemode = Gamemode.MULTIPLAYER_SCORE_ATTACK;
    private BukkitTask sortTask;

    public ScoreAttackGame(List<PlayingField> fields) {
        super(fields);
    }

    @Override
    protected void startGame() {
        super.startGame();
        time = (int) gamemode.getAttribute(GamemodeAttribute.TIME_LIMIT);
        sortTask = sortLoop();
    }

    @Override
    public Gamemode getGamemode() {
        return gamemode;
    }

    @Override
    protected BukkitTask tickLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayingField field : playingFields) {
                    if (field.hasStarted()) {
                        field.getScorer().setTime(time);
                        field.getScorer().tick();
                    } else if (gamemode.hasAttribute(GamemodeAttribute.DO_GARBAGE_ATTACK)) {
                        stop();
                    }
                }

                // todo possible race condition: we don't know if the board will stop itself due to the scorer, or if the multiplayer game will stop it
                if (time <= 0) stop();
                time--;
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    @Override
    public void stop() {
        super.stop();
        if (sortTask != null) {
            sortTask.cancel();
        }
    }

    private BukkitTask sortLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                rankPlayingFields();
                for (PlayingField field : rankings) {
                    sendRank(field);
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 20);
    }

    @Override
    protected void rankPlayingFields() {
        rankings.clear();
        rankings.addAll(playingFields);
        rankings.sort((a, b) -> b.getScorer().getScore() - a.getScorer().getScore());
    }

    public void sendRank(PlayingField field) {
        if (field == null || !rankings.contains(field)) return;
        int position = rankings.indexOf(field);
        field.getScorer().setPosition(position+1);
        if (position == 0) {
            field.getScorer().setPointsBehind(-1);
        } else {
            int pointsOfNextRank = rankings.get(position-1).getScorer().getScore();
            field.getScorer().setPointsBehind(pointsOfNextRank - field.getScorer().getScore());
        }
    }

    @Override
    protected void broadcastResults() {
        Bukkit.broadcastMessage(ChatColor.AQUA + "Hole In The Wall - Results");
        Bukkit.broadcastMessage("");
        for (int i = 0; i < rankings.size(); i++) {
            Bukkit.broadcastMessage("#" + (i+1) + " - " + ChatColor.GREEN + Utils.playersToString(rankings.get(i).getPlayers()) + " with " + rankings.get(i).getScorer().getScore() + " points");
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("---");
    }
}
