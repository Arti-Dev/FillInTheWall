package com.articreep.fillinthewall.multiplayer;

import com.articreep.fillinthewall.*;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.modifiers.ModifierEvent;
import com.articreep.fillinthewall.utils.Utils;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ScoreAttackGame extends MultiplayerGame {
    private final Gamemode gamemode = Gamemode.MULTIPLAYER_SCORE_ATTACK;
    private BukkitTask sortTask;
    private Stage stage = Stage.QUALIFICATIONS;
    private final ArrayList<PlayingField> finalStageBoards;
    private int eventTime0;
    private int eventTime1;

    public ScoreAttackGame(List<PlayingField> fields, ArrayList<PlayingField> finalStageBoards, GamemodeSettings settings) {
        super(fields, settings);
        this.finalStageBoards = finalStageBoards;

        Random random = new Random();
        eventTime0 = random.nextInt(20 * 80, 20 * 100);
        eventTime1 = random.nextInt(20 * 20, 20 * 60);
        this.settings = settings;
    }

    @Override
    protected void startGame() {
        super.startGame();
        if (stage == Stage.QUALIFICATIONS) {
            time = settings.getIntAttribute(GamemodeAttribute.TIME_LIMIT);
        } else if (stage == Stage.FINALS) {
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
                        for (PlayingField field : playingFields) {
                            field.getScorer().activateEvent(settings.getModifierEventTypeAttribute(GamemodeAttribute.MULTI_EVENT_0));
                        }
                    } else if (time == eventTime1) {
                        for (PlayingField field : playingFields) {
                            field.getScorer().activateEvent(settings.getModifierEventTypeAttribute(GamemodeAttribute.MULTI_EVENT_1));
                        }
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
    }

    private void transitionToFinals() {
        if (stage == Stage.QUALIFICATIONS && !finalStageBoards.isEmpty()) {
            ArrayList<Set<Player>> qualifyingPlayers = new ArrayList<>();
            ArrayList<Set<Player>> eliminatedPlayers = new ArrayList<>();

            rankPlayingFields();

            // record top players and put them in the finals
            for (int i = 0; i < rankings.size(); i++) {
                if (i < finalStageBoards.size()) {
                    qualifyingPlayers.add(new HashSet<>(rankings.get(i).getPlayers()));
                } else {
                    eliminatedPlayers.add(new HashSet<>(rankings.get(i).getPlayers()));
                }
            }

            stop(false);

            stage = Stage.FINALS;
            for (PlayingField field : playingFields) {
                field.sendTitleToPlayers(ChatColor.AQUA + "Qualifications over!", "Next up: Finals", 0, 60, 20);
            }

            playingFields.clear();

            // New generator
            PlayingField example = finalStageBoards.getFirst();
            generator = new WallGenerator(example.getLength(), example.getHeight(),
                    5, 10, 200);
            generator.setRandomizeFurther(false);

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
                            player.sendTitle(ChatColor.YELLOW + "Welcome to the Finals!", "Bigger board, bigger competition!", 10, 60, 20);
                        }
                    }
                    for (Set<Player> set : eliminatedPlayers) {
                        for (Player player : set) {
                            player.setGameMode(GameMode.SPECTATOR);
                            spectators.add(player);
                            player.teleport(FillInTheWall.getInstance().getSpectatorFinalsSpawn());
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
        Bukkit.broadcastMessage(ChatColor.AQUA + "Fill In The Wall - " + stage.toString());
        Bukkit.broadcastMessage("");
        for (int i = 0; i < rankings.size(); i++) {
            Bukkit.broadcastMessage("#" + (i+1) + " - " + ChatColor.GREEN + Utils.playersToString(rankings.get(i).getPlayers()) + " with " + rankings.get(i).getScorer().getScore() + " points");
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("---");
    }

    public Stage getStage() {
        return stage;
    }

    public enum Stage {
        QUALIFICATIONS(ChatColor.AQUA + "" + ChatColor.BOLD + "QUALIFICATIONS"),
        FINALS(ChatColor.GOLD + "" + ChatColor.BOLD + "FINALS");

        final String string;
        Stage(String string) {
            this.string = string;
        }

        public String getString() {
            return string;
        }
    }
}
