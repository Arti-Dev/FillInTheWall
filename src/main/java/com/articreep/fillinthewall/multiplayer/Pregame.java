package com.articreep.fillinthewall.multiplayer;

import com.articreep.fillinthewall.NBSMusic;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.PlayingFieldManager;
import com.articreep.fillinthewall.display.ScoreboardEntry;
import com.articreep.fillinthewall.display.ScoreboardEntryType;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.utils.Utils;
import com.xxmicloxx.NoteBlockAPI.model.RepeatMode;
import com.xxmicloxx.NoteBlockAPI.songplayer.PositionSongPlayer;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
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

import java.util.*;

public class Pregame implements Listener {
    private final World world;
    private final int minPlayers;
    private int countdown;
    private final int countdownMax;
    private BukkitTask task = null;
    private final Gamemode gamemode;
    private GamemodeSettings settings;

    private Scoreboard scoreboard;
    private Objective objective;
    private final ArrayList<ScoreboardEntry> scoreboardEntries = new ArrayList<>();

    private final List<PlayingField> availablePlayingFields = new ArrayList<>();

    private PositionSongPlayer songPlayer;

    public Pregame(World world, Gamemode gamemode, int minPlayers, int countdownMax) {
        this.world = world;
        this.gamemode = gamemode;
        this.settings = gamemode.getDefaultSettings();
        this.minPlayers = minPlayers;
        countdown = -1;
        this.countdownMax = countdownMax;
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
    }

    @EventHandler
    public void onPlayerLeaveWorld(PlayerChangedWorldEvent event) {
        if (event.getFrom().equals(world)) {
            Utils.resetScoreboard(event.getPlayer());
            if (songPlayer != null) songPlayer.removePlayer(event.getPlayer());
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
        if (songPlayer != null) {
            songPlayer.destroy();
        }

        if (NBSMusic.enabled && NBSMusic.getLobbyMusic() != null) {
            songPlayer = new PositionSongPlayer(NBSMusic.getLobbyMusic());
            songPlayer.setTargetLocation(NBSMusic.getLobbyMusicLocation());
            songPlayer.setRepeatMode(RepeatMode.ALL);
            songPlayer.setPlaying(true);
        }
        task = tickLoop();
    }

    public void cancelCountdown() {
        if (task != null) {
            // For some reason this doesn't call my version of the method, even though I overrided the cancel() method?
            task.cancel();
            task = null;
        }
        if (songPlayer != null) {
            songPlayer.destroy();
            songPlayer = null;
        }
        HandlerList.unregisterAll(this);
        countdown = -1;
        removeScoreboard();
    }

    /**
     * Assigns playing fields to players, puts them in multiplayer mode, and creates a new game
     */
    public void startGame() {
        if (PlayingFieldManager.game != null) {
            Bukkit.getLogger().severe("Tried to start game while another game is running");
            return;
        }

        cancelCountdown();

        List<Player> players = world.getPlayers();
        // Attempt to remove all players from any games
        for (Player player : players) {
            if (PlayingFieldManager.isInGame(player)) {
                PlayingFieldManager.removeGame(player);
            }
        }

        int playersPerField = 1;
        if (settings.getBooleanAttribute(GamemodeAttribute.COOP)) playersPerField = 2;
        List<PlayingField> readyToGoPlayingFields =
                assignPlayersToPlayingFields(new ArrayList<>(world.getPlayers()), availablePlayingFields, playersPerField);

        for (PlayingField field : readyToGoPlayingFields) {
            for (Player player : field.getPlayers()) {
                player.teleport(field.getSpawnLocation());
            }
        }

        if (gamemode == Gamemode.MULTIPLAYER_SCORE_ATTACK) {
            PlayingFieldManager.game = new ScoreAttackGame(readyToGoPlayingFields, PlayingFieldManager.finalStageBoards,
                    settings.copy());
            PlayingFieldManager.game.start();
        } else if (gamemode == Gamemode.VERSUS) {
            PlayingFieldManager.vsGame = new VersusGame(readyToGoPlayingFields);
            PlayingFieldManager.vsGame.start();
        }
    }

    // Supports multiple players on one playing field!
    public static List<PlayingField> assignPlayerSetsToPlayingFields(List<Set<Player>> players, List<PlayingField> availablePlayingFields) {
        List<PlayingField> readyToGoPlayingFields = new ArrayList<>();

        Collections.shuffle(players);
        Iterator<Set<Player>> playerSetIterator = players.iterator();
        Set<Player> currentPlayerSet = playerSetIterator.next();
        Iterator<PlayingField> fieldIterator = availablePlayingFields.iterator();
        PlayingField currentPlayingField = fieldIterator.next();

        // while true statement with iterator.hasNext checks
        while (true) {
            if (currentPlayingField.playerCount() != 0) {
                Bukkit.getLogger().info("Field is not empty - skipping");
                if (fieldIterator.hasNext()) {
                    currentPlayingField = fieldIterator.next();
                } else {
                    break;
                }
                continue;
            }

            // Filter out players already in games
            Iterator<Player> playerIterator = currentPlayerSet.iterator();
            while (playerIterator.hasNext()) {
                Player player = playerIterator.next();
                if (PlayingFieldManager.isInGame(player)) {
                    Bukkit.getLogger().info(player.getName() + " is already in a game - skipping (remove them first!)");
                    playerIterator.remove();
                }
            }

            if (currentPlayerSet.isEmpty()) {
                Bukkit.getLogger().info("No players left to add to field - skipping");
                if (playerSetIterator.hasNext()) {
                    currentPlayerSet = playerSetIterator.next();
                } else {
                    break;
                }
                continue;
            }

            // Add remaining players to the field
            if (currentPlayingField.playerCount() == 0 || !currentPlayerSet.isEmpty()) {
                currentPlayingField.stop();
                currentPlayingField.reset();
                currentPlayingField.setMultiplayerMode(true);
                for (Player player : currentPlayerSet) {
                    currentPlayingField.addPlayer(player, PlayingField.AddReason.MULTIPLAYER);
                }
                readyToGoPlayingFields.add(currentPlayingField);

                if (fieldIterator.hasNext()) {
                    currentPlayingField = fieldIterator.next();
                } else {
                    break;
                }

                if (playerSetIterator.hasNext()) {
                    currentPlayerSet = playerSetIterator.next();
                } else {
                    break;
                }
            }

        }
        return readyToGoPlayingFields;
    }

    public static List<PlayingField> assignPlayersToPlayingFields(List<Player> players, List<PlayingField> availablePlayingFields, int playersPerField) {
        if (playersPerField < 1) {
            throw new IllegalArgumentException("Can't have less than 1 player per playing field!");
        }
        List<Set<Player>> playerSets = new ArrayList<>();
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i += playersPerField) {
            Set<Player> playerSet = new HashSet<>();
            for (int j = 0; j < playersPerField && i+j < players.size(); j++) {
                playerSet.add(players.get(i+j));
            }
            playerSets.add(playerSet);
        }
        return assignPlayerSetsToPlayingFields(playerSets, availablePlayingFields);
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
                    if (songPlayer != null && !songPlayer.getPlayerUUIDs().contains(player.getUniqueId())) {
                        songPlayer.addPlayer(player);
                    }
                }

                if (world.getPlayers().size() < minPlayers) {
                    countdown = -1;
                } else {
                    if (countdown == -1) countdown = countdownMax;
                    else countdown--;
                }

                updateScoreboard();
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 20);
    }

    public void createScoreboard() {
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();
        objective = scoreboard.registerNewObjective("fillinthewall", "dummy",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Fill in the Wall");
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

    public World getWorld() {
        return world;
    }

    public GamemodeSettings getSettings() {
        return settings;
    }

    public Gamemode getGamemode() {
        return gamemode;
    }
}
