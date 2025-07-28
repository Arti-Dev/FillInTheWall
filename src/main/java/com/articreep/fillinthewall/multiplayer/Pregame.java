package com.articreep.fillinthewall.multiplayer;

import com.articreep.fillinthewall.commands.PairUp;
import com.articreep.fillinthewall.lobby.NBSMusic;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.game.PlayingFieldManager;
import com.articreep.fillinthewall.infodisplay.ScoreboardEntry;
import com.articreep.fillinthewall.infodisplay.ScoreboardEntryType;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.playerinfo.PlayerSettings;
import com.articreep.fillinthewall.utils.Utils;
import com.xxmicloxx.NoteBlockAPI.model.RepeatMode;
import com.xxmicloxx.NoteBlockAPI.songplayer.PositionSongPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public class Pregame implements Listener {
    private final World world;
    private final int minPlayers;
    private int countdown;
    private final int countdownMax;
    private BukkitTask task = null;
    private final Gamemode gamemode;
    private final GamemodeSettings settings;

    private Scoreboard scoreboard;
    private Objective objective;
    private final ArrayList<ScoreboardEntry> scoreboardEntries = new ArrayList<>();

    private final List<PlayingField> availablePlayingFields = new ArrayList<>();
    private final Set<Player> excludedPlayers = new HashSet<>();
    private static final Set<Player> gimmicklessPlayers = new HashSet<>();

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
            removeFromPregame(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        excludedPlayers.remove(event.getPlayer());
        gimmicklessPlayers.remove(event.getPlayer());
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

        if (NBSMusic.enabled && NBSMusic.getLobbyMusic() != null && NBSMusic.getLobbyMusicLocation() != null) {
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
            FillInTheWall.getInstance().getSLF4JLogger().error("Tried to start game while another game is running");
            return;
        }

        List<Player> players = getAvailablePlayers();
        if (players.isEmpty()) return;

        cancelCountdown();

        // Attempt to remove all players from any games
        for (Player player : players) {
            if (PlayingFieldManager.isInGame(player)) {
                PlayingFieldManager.removeGame(player);
            }
        }

        int playersPerField = 1;
        if (settings.getBooleanAttribute(GamemodeAttribute.COOP)) playersPerField = 2;
        List<PlayingField> readyToGoPlayingFields =
                assignPlayersToPlayingFields(getAvailablePlayers(), availablePlayingFields, playersPerField);

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
                FillInTheWall.getInstance().getSLF4JLogger().info("Field is not empty - skipping");
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
                    FillInTheWall.getInstance().getSLF4JLogger().info("{} is already in a game - skipping (remove them first!)", player.getName());
                    playerIterator.remove();
                }
            }

            if (currentPlayerSet.isEmpty()) {
                FillInTheWall.getInstance().getSLF4JLogger().info("No players left to add to field - skipping");
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
                    if (gimmicklessPlayers.contains(player)) currentPlayingField.getScorer().setGimmickless(true);
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
        // clone
        players = new ArrayList<>(players);
        Set<PairUp.PlayerPair> pairs = new HashSet<>();
        if (playersPerField < 1) {
            throw new IllegalArgumentException("Can't have less than 1 player per playing field!");
        }
        List<Set<Player>> playerSets = new ArrayList<>();
        Collections.shuffle(players);

        // Remove pairs from initial player list
        Iterator<Player> it = players.iterator();
        while (it.hasNext()) {
            Player player = it.next();
            if (PairUp.isPaired(player)) {
                pairs.add(PairUp.getPair(player));
                it.remove();
            }
        }

        // Add solo players
        for (int i = 0; i < players.size(); i += playersPerField) {
            Set<Player> playerSet = new HashSet<>();
            for (int j = 0; j < playersPerField && i+j < players.size(); j++) {
                playerSet.add(players.get(i+j));
            }
            playerSets.add(playerSet);
        }

        // Add paired players
        for (PairUp.PlayerPair pair : pairs) {
            Set<Player> playerSet = new HashSet<>();
            playerSet.add(pair.player1());
            playerSet.add(pair.player2());
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

                for (Player player : getAvailablePlayers()) {
                    player.setScoreboard(scoreboard);
                    Team team = scoreboard.getTeam(FillInTheWall.NO_COLLISION_TEAM_NAME);
                    if (team != null) {
                        team.addEntity(player);
                    }
                    if (songPlayer == null) continue;
                    asyncMusicSettingCheck(player);
                }

                if (getAvailablePlayers().size() < minPlayers) {
                    countdown = -1;
                } else {
                    if (countdown == -1) countdown = countdownMax;
                    else countdown--;
                }

                updateScoreboard();
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 20);
    }

    private void asyncMusicSettingCheck(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(FillInTheWall.getInstance(), () -> {

            if (!PlayerSettings.getBooleanSettingOrDefault(
                    player.getUniqueId(), PlayerSettings.BooleanSetting.MUSIC)) {
                Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
                    if (songPlayer != null) songPlayer.removePlayer(player);
                });
            } else if (!songPlayer.getPlayerUUIDs().contains(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
                    if (songPlayer != null) songPlayer.addPlayer(player);
                });
            }

        });
    }

    public void createScoreboard() {
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();
        Team team = scoreboard.registerNewTeam(FillInTheWall.NO_COLLISION_TEAM_NAME);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        objective = scoreboard.registerNewObjective("fillinthewall", Criteria.DUMMY,
                MiniMessage.miniMessage().deserialize("<yellow><bold>Fill in the Wall"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 1));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.PREGAME_PLAYERCOUNT, 2));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 3));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.START_TIMER, 4));
        addScoreboardEntry(new ScoreboardEntry(ScoreboardEntryType.EMPTY, 5));

        for (Player player : getAvailablePlayers()) {
            player.setScoreboard(scoreboard);
            team.addEntity(player);
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
                        entry.forceUpdate(scoreboard, objective, Component.text("Waiting for players..."));
                    } else {
                        entry.update(scoreboard, objective, Component.text(countdown));
                    }
                }
                case PREGAME_PLAYERCOUNT -> entry.update(scoreboard, objective, Component.text(getAvailablePlayers().size()));
            }
        }

    }

    public void removeScoreboard() {
        for (Player player : getAvailablePlayers()) {
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

    public void addExcludedPlayer(Player player) {
        excludedPlayers.add(player);
        removeFromPregame(player);
    }

    private void removeFromPregame(Player player) {
        Utils.resetScoreboard(player);
        if (songPlayer != null) songPlayer.removePlayer(player);
    }

    public void removeExcludedPlayer(Player player) {
        excludedPlayers.remove(player);
    }

    public boolean isExcludedPlayer(Player player) {
        return excludedPlayers.contains(player);
    }

    private List<Player> getAvailablePlayers() {
        List<Player> players = new ArrayList<>(world.getPlayers());
        players.removeIf(excludedPlayers::contains);
        return players;
    }

    public static void addGimmicklessPlayer(Player player) {
        gimmicklessPlayers.add(player);
    }

    public static void removeGimmicklessPlayer(Player player) {
        gimmicklessPlayers.remove(player);
    }

    public static boolean isGimmicklessPlayer(Player player) {
        return gimmicklessPlayers.contains(player);
    }
}
