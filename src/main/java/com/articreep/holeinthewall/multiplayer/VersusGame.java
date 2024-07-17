package com.articreep.holeinthewall.multiplayer;

import com.articreep.holeinthewall.gamemode.Gamemode;
import com.articreep.holeinthewall.HoleInTheWall;
import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.utils.Utils;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class VersusGame extends MultiplayerGame {
    private final Gamemode gamemode = Gamemode.VERSUS;
    private final ArrayList<PlayingField> remainingFields = new ArrayList<>();
    private final HashMap<Set<Player>, Integer> timeKOed = new HashMap<>();
    // Rankings work differently here
    private final List<Set<Player>> rankings = new ArrayList<>();

    public VersusGame(List<PlayingField> fields) {
        super(fields);
    }

    @Override
    public Gamemode getGamemode() {
        return gamemode;
    }

    @Override
    protected void startGame() {
        super.startGame();
        // for now, there can only be two players
        if (playingFields.size() != 2) {
            Bukkit.getLogger().severe("Tried to start a versus game with more than two players");
        } else {
            // set each playing field opponent to be the other
            Iterator<PlayingField> iterator = playingFields.iterator();
            PlayingField field1 = iterator.next();
            PlayingField field2 = iterator.next();
            field1.getScorer().setOpponent(field2);
            field2.getScorer().setOpponent(field1);
            remainingFields.addAll(playingFields);
        }
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
                        // If this field has stopped, record the time when it stopped and place in rankings
                        timeKOed.put(field.getPlayers(), time);
                        remainingFields.remove(field);
                    }
                }

                if (remainingFields.size() <= 1) {
                    stop();
                }
                time++;
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    @Override
    protected void rankPlayingFields() {
        rankings.clear();
        rankings.addAll(timeKOed.keySet());
        rankings.sort((a, b) -> timeKOed.get(b) - timeKOed.get(a));
    }

    @Override
    protected void broadcastResults() {
        Bukkit.broadcastMessage(ChatColor.AQUA + "Hole In The Wall " + ChatColor.BLUE + "VERSUS" + ChatColor.AQUA + " - Results");
        Bukkit.broadcastMessage("");
        if (remainingFields.isEmpty()) {
            Bukkit.broadcastMessage("Victor: " + ChatColor.ITALIC + "It's a tie...?");
        } else {
            Bukkit.broadcastMessage("Victor: " + ChatColor.GREEN + Utils.playersToString(remainingFields.getFirst().getPlayers()));
        }
        Bukkit.broadcastMessage("");
        for (int i = 0; i < rankings.size(); i++) {
            time = timeKOed.get(rankings.get(i));
            Bukkit.broadcastMessage("#" + (i+2) + " - " + ChatColor.GREEN + Utils.playersToString(rankings.get(i)) + " survived for " + Utils.getFormattedTime(time));
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("---");
    }
}
