package com.articreep.holeinthewall.multiplayer;

import com.articreep.holeinthewall.*;
import com.articreep.holeinthewall.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ScoreAttackGame extends MultiplayerGame {
    private final Gamemode gamemode = Gamemode.MULTIPLAYER_SCORE_ATTACK;
    private BukkitTask sortTask;
    private Stage stage = Stage.QUALIFICATIONS;
    private ArrayList<PlayingField> finalStageBoards;

    public ScoreAttackGame(List<PlayingField> fields, ArrayList<PlayingField> finalStageBoards) {
        super(fields);
        this.finalStageBoards = finalStageBoards;
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
                    }
                }

                // todo possible race condition: we don't know if the board will stop itself due to the scorer, or if the multiplayer game will stop it
                if (time <= 0) {
                    stop();
                }
                time--;
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    @Override
    public void stop() {
        if (stage == Stage.QUALIFICATIONS && !finalStageBoards.isEmpty()) {
            ArrayList<Set<Player>> qualifyingPlayers = new ArrayList<>();

            // record top four players and put them in the finals
            for (int i = 0; i < Math.min(finalStageBoards.size(), rankings.size()); i++) {
                qualifyingPlayers.add(new HashSet<>(rankings.get(i).getPlayers()));
            }

            super.stop();

            stage = Stage.FINALS;
            for (PlayingField field : playingFields) {
                field.sendTitleToPlayers(ChatColor.AQUA + "Qualifications over!", "Next up: Finals", 0, 60, 20);
            }

            playingFields.clear();

            PlayingField example = finalStageBoards.getFirst();
            // New generator
            generator = new WallGenerator(example.getLength(), example.getHeight(),
                    5, 10, 200);
            generator.setRandomizeFurther(false);

            // remove
            for (Set<Player> set : qualifyingPlayers) {
                for (Player player : set) {
                    PlayingFieldManager.removeGame(player);
                }
            }

            for (int i = 0; i < Math.min(finalStageBoards.size(), qualifyingPlayers.size()); i++) {
                PlayingField field = finalStageBoards.get(i);
                for (Player player : qualifyingPlayers.get(i)) {
                    field.addPlayer(player);
                }
                field.setLocked(true);
                playingFields.add(field);
            }

            // todo maybe i'll just make a new pregame object???
            otherTasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    for (PlayingField field : playingFields) {
                        // Spawn location
                        Location spawn = field.getReferencePoint().subtract(0.5, 0.5, 0.5);
                        spawn.add(field.getFieldDirection()
                                .multiply(field.getLength() / 2.0));
                        spawn.add(field.getIncomingDirection().multiply(field.getStandingDistance() / 2.0));
                        spawn.setDirection(field.getIncomingDirection().multiply(-1));
                        for (Player player : field.getPlayers()) {
                            player.teleport(spawn);
                            player.sendTitle(ChatColor.YELLOW + "Welcome to the Finals!", "Bigger board, bigger competition!", 10, 60, 20);
                        }
                    }
                }
            }.runTaskLater(HoleInTheWall.getInstance(), 20 * 5));

            otherTasks.add(new BukkitRunnable() {

                @Override
                public void run() {
                    start();
                    // todo temporary
                    PlayingFieldManager.game = ScoreAttackGame.this;
                }
            }.runTaskLater(HoleInTheWall.getInstance(), 20 * 10));

            time = (int) gamemode.getAttribute(GamemodeAttribute.TIME_LIMIT);
        } else {
            super.stop();
        }

        if (sortTask != null) {
            sortTask.cancel();
        }
    }

    private BukkitTask sortLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                rankPlayingFields();
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 20);
    }

    @Override
    protected void rankPlayingFields() {
        rankings.clear();
        rankings.addAll(playingFields);
        rankings.sort((a, b) -> b.getScorer().getScore() - a.getScorer().getScore());
    }

    @Override
    protected void broadcastResults() {
        Bukkit.broadcastMessage(ChatColor.AQUA + "Hole In The Wall - " + stage.toString());
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
