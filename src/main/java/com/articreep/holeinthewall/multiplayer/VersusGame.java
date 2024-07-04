package com.articreep.holeinthewall.multiplayer;

import com.articreep.holeinthewall.Gamemode;
import com.articreep.holeinthewall.HoleInTheWall;
import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.List;

public class VersusGame extends MultiplayerGame {
    private final Gamemode gamemode = Gamemode.VERSUS;

    public VersusGame(List<PlayingField> fields) {
        super(fields);
    }

    @Override
    public Gamemode getGamemode() {
        return gamemode;
    }

    @Override
    protected void startGame() {
        // for now, there can only be two players
        if (playingFields.size() != 2) {
            Bukkit.getLogger().severe("Tried to start a versus game with more than two players");
            return;
        } else {
            // set each playing field opponent to be the other
            Iterator<PlayingField> iterator = playingFields.iterator();
            PlayingField field1 = iterator.next();
            PlayingField field2 = iterator.next();
            field1.getScorer().setOpponent(field2);
            field2.getScorer().setOpponent(field1);
        }
        super.startGame();
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
                    } else {
                        // If any game has stopped (due to garbage overflow), stop the entire game
                        stop();
                    }
                }

                time++;
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    @Override
    protected void rankPlayingFields() {

    }

    @Override
    protected void broadcastResults() {
        // todo using score attack results as a placeholder
        Bukkit.broadcastMessage(ChatColor.AQUA + "Hole In The Wall - Results");
        Bukkit.broadcastMessage("");
        for (int i = 0; i < rankings.size(); i++) {
            Bukkit.broadcastMessage("#" + (i+1) + " - " + ChatColor.GREEN + Utils.playersToString(rankings.get(i).getPlayers()) + " with " + rankings.get(i).getScorer().getScore() + " points");
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("---");
    }
}
