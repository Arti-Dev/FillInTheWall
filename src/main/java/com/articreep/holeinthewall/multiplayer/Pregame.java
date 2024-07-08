package com.articreep.holeinthewall.multiplayer;

import com.articreep.holeinthewall.Gamemode;
import com.articreep.holeinthewall.HoleInTheWall;
import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.PlayingFieldManager;
import com.articreep.holeinthewall.display.ScoreboardEntry;
import com.articreep.holeinthewall.display.ScoreboardEntryType;
import com.articreep.holeinthewall.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Pregame implements Listener {
    private final World world;
    private final int minPlayers;
    private int countdown;
    private final int countdownMax;
    private BukkitTask task = null;
    private final Gamemode gamemode;

    private Scoreboard scoreboard;
    private Objective objective;
    private final ArrayList<ScoreboardEntry> scoreboardEntries = new ArrayList<>();

    private final List<PlayingField> availablePlayingFields = new ArrayList<>();

    public Pregame(World world, Gamemode gamemode, int minPlayers, int countdownMax) {
        this.world = world;
        this.gamemode = gamemode;
        this.minPlayers = minPlayers;
        countdown = -1;
        this.countdownMax = countdownMax;
        Bukkit.getPluginManager().registerEvents(this, HoleInTheWall.getInstance());
    }

    @EventHandler
    public void onPlayerLeaveWorld(PlayerChangedWorldEvent event) {
        if (event.getFrom().equals(world)) {
            Utils.resetScoreboard(event.getPlayer());
        }
    }

    public void unregisterEvents() {
        PlayerChangedWorldEvent.getHandlerList().unregister(this);
    }

    public boolean isActive() {
        return task != null;
    }

    public void startCountdown() {
        if (task != null) {
            task.cancel();
        }
        task = tickLoop();
    }

    public void cancelCountdown() {
        if (task != null) {
            // For some reason this doesn't call my version of the method, even though I overrided the cancel() method?
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        countdown = -1;
        removeScoreboard();
    }

    public void startGame() {
        if (PlayingFieldManager.game != null) {
            Bukkit.getLogger().severe("Tried to start game while another game is running");
            return;
        }

        cancelCountdown();

        List<Player> players = world.getPlayers();
        // Remove all players from any games
        for (Player player : players) {
            if (PlayingFieldManager.isInGame(player)) {
                PlayingFieldManager.removeGame(player);
            }
        }

        List<PlayingField> readyToGoPlayingFields = new ArrayList<>();
        Iterator<Player> playerIterator = players.iterator();
        // Assign players to playing fields
        for (PlayingField field : availablePlayingFields) {
            if (!playerIterator.hasNext()) {
                break;
            }
            Player player = playerIterator.next();
            if (field.playerCount() == 0 && !PlayingFieldManager.isInGame(player)) {
                field.stop();
                field.addPlayer(player, true);
                field.doTickScorer(false);
                // Spawn location
                Location spawn = field.getReferencePoint().subtract(0.5, 0.5, 0.5);
                spawn.add(field.getFieldDirection()
                        .multiply(field.getLength() / 2.0));
                spawn.add(field.getIncomingDirection().multiply(field.getStandingDistance() / 2.0));
                spawn.setDirection(field.getIncomingDirection().multiply(-1));
                player.teleport(spawn);
                readyToGoPlayingFields.add(field);
            }
        }

        if (gamemode == Gamemode.MULTIPLAYER_SCORE_ATTACK) {
            PlayingFieldManager.game = new ScoreAttackGame(readyToGoPlayingFields);
            PlayingFieldManager.game.start();
        } else if (gamemode == Gamemode.VERSUS) {
            PlayingFieldManager.vsGame = new VersusGame(readyToGoPlayingFields);
            PlayingFieldManager.vsGame.start();
        }
    }

    private BukkitTask tickLoop() {
        createScoreboard();
        return new BukkitRunnable() {

            @Override
            public void run() {
                if (countdown == 0) {
                    startGame();
                    return;
                }

                for (Player player : world.getPlayers()) {
                    player.setScoreboard(scoreboard);
                }

                if (world.getPlayers().size() < minPlayers) {
                    countdown = -1;
                } else {
                    if (countdown == -1) countdown = countdownMax;
                    else countdown--;
                }

                updateScoreboard();
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 20);
    }

    public void createScoreboard() {
        Bukkit.getPluginManager().registerEvents(this, HoleInTheWall.getInstance());
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();
        objective = scoreboard.registerNewObjective("holeinthewall", "dummy",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Hole in the Wall");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 1));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.PREGAME_PLAYERCOUNT, 2));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 3));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.START_TIMER, 4));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 5));

        for (Player player : world.getPlayers()) {
            player.setScoreboard(scoreboard);
        }
    }

    private void addScoreboardEntry(ScoreboardEntry entry) {
        scoreboardEntries.add(entry);
        entry.addToObjective(objective);
    }

    public void updateScoreboard() {
        if (scoreboard == null) return;
        for (ScoreboardEntry entry : scoreboardEntries) {
            switch (entry.getType()) {
                case START_TIMER -> {
                    if (countdown == -1) {
                        entry.forceUpdate(scoreboard, objective, "Waiting for players...");
                    } else {
                        entry.update(scoreboard, objective, countdown);
                    }
                }
                case PREGAME_PLAYERCOUNT -> entry.update(scoreboard, objective, world.getPlayers().size());
            }
        }

    }

    public void removeScoreboard() {
        for (Player player : world.getPlayers()) {
            Utils.resetScoreboard(player);
        }
        for (ScoreboardEntry entry : scoreboardEntries) {
            entry.destroy();
        }
        scoreboard = null;
        objective = null;
        scoreboardEntries.clear();
    }

    public void addAvailablePlayingField(PlayingField field) {
        availablePlayingFields.add(field);
    }

    public void clearAvailablePlayingFields() {
        availablePlayingFields.clear();
    }
}
