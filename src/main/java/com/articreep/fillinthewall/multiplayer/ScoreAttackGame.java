package com.articreep.fillinthewall.multiplayer;

import com.articreep.fillinthewall.*;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.game.PlayingFieldManager;
import com.articreep.fillinthewall.game.WallBundle;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.modifiers.ModifierEvent;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

public class ScoreAttackGame extends MultiplayerGame {
    private final Gamemode gamemode = Gamemode.MULTIPLAYER_SCORE_ATTACK;
    private BukkitTask sortTask;
    private Stage stage = Stage.QUALIFICATIONS;
    private final ArrayList<PlayingField> finalStageBoards;
    private final int eventTime0;
    private final int eventTime1;
    private final int eventTimeFinals;
    private final Sound[] possibleQualificationsMusic = {Sound.MUSIC_DISC_BLOCKS, Sound.MUSIC_DISC_CHIRP, Sound.MUSIC_DISC_FAR,
    Sound.MUSIC_DISC_STAL, Sound.MUSIC_DISC_WAIT};
    private final Sound[] possibleFinalsMusic = {Sound.MUSIC_DISC_PRECIPICE};
    private final WallBundle customWallBundle = WallBundle.getWallBundle("finals");
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();

    private final Map<Player, Integer> savedQualificationScores = new HashMap<>();

    public ScoreAttackGame(List<PlayingField> fields, ArrayList<PlayingField> finalStageBoards, GamemodeSettings settings) {
        super(fields, settings);
        this.finalStageBoards = finalStageBoards;

        Random random = new Random();
        eventTime0 = random.nextInt(20 * 80, 20 * 100);
        eventTime1 = random.nextInt(20 * 20, 20 * 60);
        eventTimeFinals = 20 * 90;
        this.settings = settings;
    }

    @Override
    protected void startGame() {
        super.startGame();
        if (stage == Stage.QUALIFICATIONS) {
            time = settings.getIntAttribute(GamemodeAttribute.TIME_LIMIT);
            Sound music = possibleQualificationsMusic[(int) (Math.random() * possibleQualificationsMusic.length)];
            for (PlayingField field : playingFields) {
                field.playMusic(music);
            }
        } else if (stage == Stage.FINALS) {
            Sound music = possibleFinalsMusic[(int) (Math.random() * possibleFinalsMusic.length)];
            for (PlayingField field : playingFields) {
                field.playMusic(music);
            }
            time = settings.getIntAttribute(GamemodeAttribute.FINALS_TIME_LIMIT);
        }
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
                    }
                }

                if (stage == Stage.QUALIFICATIONS && settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT)
                        == ModifierEvent.Type.NONE) {
                    if (time == eventTime0) {
                        deployEventWithSignals(settings.getModifierEventTypeAttribute(GamemodeAttribute.MULTI_EVENT_0));
                    } else if (time == eventTime1) {
                        deployEventWithSignals(settings.getModifierEventTypeAttribute(GamemodeAttribute.MULTI_EVENT_1));
                    }
                } else if (stage == Stage.FINALS && settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT)
                        == ModifierEvent.Type.NONE) {
                    if (time == eventTimeFinals) {
                        deployEventWithSignals(settings.getModifierEventTypeAttribute(GamemodeAttribute.MULTI_EVENT_FINALS));
                    }
                }

                // todo possible race condition: we don't know if the board will stop itself due to the scorer, or if the multiplayer game will stop it
                if (time <= 0) {
                    if (stage == Stage.QUALIFICATIONS && !finalStageBoards.isEmpty()) {
                        transitionToFinals();
                    } else {
                        stop();
                    }
                }
                time--;
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    @Override
    public void stop(boolean markAsEnded) {
        super.stop(markAsEnded);
        if (sortTask != null) {
            sortTask.cancel();
        }
        if (markAsEnded) {
            for (PlayingField field : playingFields) {
                submitScores(field);
            }
            PlayingFieldManager.pregame.startCountdown();
        }
    }

    private void transitionToFinals() {
        if (stage == Stage.QUALIFICATIONS && !finalStageBoards.isEmpty() && !playingFields.isEmpty()) {
            ArrayList<Set<Player>> qualifyingPlayers = new ArrayList<>();
            ArrayList<Set<Player>> eliminatedPlayers = new ArrayList<>();

            rankPlayingFields();

            // record top players and put them in the finals
            for (int i = 0; i < rankings.size(); i++) {
                if (i < finalStageBoards.size()) {
                    Set<Player> players = new HashSet<>(rankings.get(i).getPlayers());
                    qualifyingPlayers.add(players);
                    int score = rankings.get(i).getScorer().getScore();
                    players.forEach((player) -> savedQualificationScores.put(player, score));
                } else {
                    eliminatedPlayers.add(new HashSet<>(rankings.get(i).getPlayers()));
                    submitScores(rankings.get(i));
                }
            }

            stop(false);

            stage = Stage.FINALS;
            for (PlayingField field : playingFields) {
                field.sendTitleToPlayers(miniMessage.deserialize("<aqua>Qualifications over!"), miniMessage.deserialize("Next up: Finals"), 0, 60, 20);
            }

            playingFields.clear();

            // New generator
            PlayingField example = finalStageBoards.getFirst();
            generator = new WallGenerator(example.getLength(), example.getHeight(),
                    5, 10, 200);
            generator.setRandomizeFurther(false);
            generator.setCustomWallBundle(customWallBundle);
            if (settings.getBooleanAttribute(GamemodeAttribute.COOP)) {
                generator.setCoop(true);
            }

            // Move players from old playing field to final playing field
            for (Set<Player> set : qualifyingPlayers) {
                for (Player player : set) {
                    PlayingFieldManager.removeGame(player);
                }
            }

            playingFields.addAll(Pregame.assignPlayerSetsToPlayingFields(qualifyingPlayers, finalStageBoards));

            otherTasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    for (PlayingField field : playingFields) {
                        // Spawn location
                        for (Player player : field.getPlayers()) {
                            player.teleport(field.getSpawnLocation());
                            Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(3000), Duration.ofMillis(1000));
                            player.showTitle(Title.title(miniMessage.deserialize("<yellow>Welcome to the Finals!"),
                                    miniMessage.deserialize("Bigger board, bigger competition!"), times));
                        }
                    }
                    for (Set<Player> set : eliminatedPlayers) {
                        for (Player player : set) {
                            player.setGameMode(GameMode.SPECTATOR);
                            spectators.add(player);
                            player.teleport(FillInTheWall.getInstance().getSpectatorFinalsSpawn());
                            Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(3000), Duration.ofMillis(1000));
                            player.showTitle(Title.title(miniMessage.deserialize("<yellow>Welcome to the Finals!"),
                                    miniMessage.deserialize("Bigger board, bigger competition!"), times));
                        }
                    }
                }
            }.runTaskLater(FillInTheWall.getInstance(), 20 * 5));

            otherTasks.add(new BukkitRunnable() {

                @Override
                public void run() {
                    start();
                    // todo temporary
                    PlayingFieldManager.game = ScoreAttackGame.this;
                }
            }.runTaskLater(FillInTheWall.getInstance(), 20 * 10));
        } else {
            stop();
        }
    }

    private BukkitTask sortLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                rankPlayingFields();
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 20);
    }

    @Override
    protected void rankPlayingFields() {
        rankings.clear();
        rankings.addAll(playingFields);
        rankings.sort((a, b) -> b.getScorer().getScore() - a.getScorer().getScore());
    }

    @Override
    protected void broadcastResults() {
        Bukkit.broadcast(miniMessage.deserialize("<aqua>Fill In The Wall - " + stage.toString()));
        Bukkit.broadcast(Component.empty());
        for (int i = 0; i < rankings.size(); i++) {
            if (i == 0) rankings.get(i).fireworks();
            String message = "#" + (i+1) + " - <green>" +
                    Utils.playersToString(rankings.get(i).getPlayers()) + " with " + rankings.get(i).getScorer().getScore() + " points";
            if (rankings.get(i).getScorer().isGimmickless()) {
                message += " <dark_gray>(gimmickless)";
            }
            Bukkit.broadcast(miniMessage.deserialize(message));
        }
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("---"));
    }

    protected void submitScores(PlayingField field) {
        if (stage == Stage.QUALIFICATIONS) {
            int score = field.getScorer().getScore();
            for (Player player : field.getPlayers()) {
                if (!player.isOnline()) continue;
                try {
                    int best = Database.getMultiplayerScore(player.getUniqueId());
                    if (score > best) {
                        Database.updateMultiplayerScore(player.getUniqueId(), score);
                    }
                } catch (SQLException e) {
                    FillInTheWall.getInstance().getSLF4JLogger().error("Something went wrong while updating multiplayer score for player {}", player.getName(), e);
                }
            }
        } else if (stage == Stage.FINALS) {
            int score = field.getScorer().getScore();
            for (Player player : field.getPlayers()) {
                if (!player.isOnline()) continue;
                try {
                    int best = Database.getMultiplayerScore(player.getUniqueId());
                    int quali = savedQualificationScores.getOrDefault(player, 0);
                    if (score + quali > best) {
                        Database.updateMultiplayerScore(player.getUniqueId(), score + quali);
                    }
                } catch (SQLException e) {
                    FillInTheWall.getInstance().getSLF4JLogger().error("Something went wrong while updating multiplayer score for player {}", player.getName(), e);
                }
            }
        }
    }

    public Stage getStage() {
        return stage;
    }

    public enum Stage {
        QUALIFICATIONS("<aqua><bold>QUALIFICATIONS"),
        FINALS("<gold><bold>FINALS");

        final String string;
        Stage(String string) {
            this.string = string;
        }

        public Component getComponent() {
            return MiniMessage.miniMessage().deserialize(string);
        }
    }
}
