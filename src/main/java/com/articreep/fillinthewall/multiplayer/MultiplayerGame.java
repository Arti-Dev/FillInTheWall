package com.articreep.fillinthewall.multiplayer;

import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.PlayingFieldManager;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public abstract class MultiplayerGame {
    protected final Set<PlayingField> playingFields = new HashSet<>();
    protected final List<PlayingField> rankings = new ArrayList<>();
    protected WallGenerator generator;
    protected int time;
    protected BukkitTask mainTask = null;
    protected Set<BukkitTask> otherTasks = new HashSet<>();
    protected GamemodeSettings settings;
    protected Set<Player> spectators;

    public MultiplayerGame(List<PlayingField> fields, GamemodeSettings settings) {
        // todo add some kind of way to input setting changes
        if (fields.isEmpty()) {
            Bukkit.getLogger().severe("Tried to create multiplayer game with no playing fields");
        }
        this.settings = settings;
        playingFields.addAll(fields);
        // Use the first field to make the generator
        PlayingField field = fields.getFirst();
        generator = new WallGenerator(field.getLength(), field.getHeight(), settings.getIntAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT),
                settings.getIntAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT),
                settings.getIntAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME));
        generator.setRandomizeFurther(false);
        generator.setWallHolesMax(8);
        generator.setWallTimeDecrease(settings.getIntAttribute(GamemodeAttribute.WALL_TIME_DECREASE_AMOUNT));
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

        // Init playing fields
        for (PlayingField field : playingFields) {

            field.getQueue().setGenerator(generator);
            generator.addQueue(field.getQueue());
            field.getScorer().setMultiplayerGame(this);
        }

        otherTasks.add(new BukkitRunnable() {
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
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 20));
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
        if (mainTask != null) {
            Bukkit.getLogger().severe("Tried to start multiplayer game that's already been started");
            return;
        }
        for (PlayingField field : playingFields) {
            try {
                field.start(getGamemode(), settings);
            } catch (IllegalStateException e) {
                e.printStackTrace();
                removePlayingfield(field);
            }
        }
        generator.addNewWallToQueues();
        otherTasks.add(spectatorTask());
        mainTask = tickLoop();
    }

    public void stop(boolean markAsEnded) {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }

        for (BukkitTask task : otherTasks) {
            task.cancel();
        }
        otherTasks.clear();

        for (PlayingField field : playingFields) {
            field.stop();
        }

        rankPlayingFields();
        broadcastResults();

        Set<Player> playersToTeleport = new HashSet<>();
        playersToTeleport.addAll(spectators);
        for (PlayingField field : playingFields) {
            field.getQueue().resetGenerator();
            field.setMultiplayerMode(false);
            field.getScorer().setMultiplayerGame(null);
            playersToTeleport.addAll(field.getPlayers());
        }

        if (markAsEnded) {
            PlayingFieldManager.game = null;
            Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
                for (Player player : playersToTeleport) {
                    player.teleport(FillInTheWall.getInstance().getMultiplayerSpawn());
                    player.setGameMode(GameMode.ADVENTURE);
                }
            }, 20*10);
        }
    }

    public void stop() {
        stop(true);
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
            field.setMultiplayerMode(false);
            field.getQueue().resetGenerator();
        }
    }

    public int getPlayerCount() {
        return playingFields.size();
    }

    public int getRank(PlayingField field) {
        if (field == null || !rankings.contains(field)) return -1;
        int position = rankings.indexOf(field);
        return position + 1;
    }

    public int getPointsBehindNextRank(PlayingField field) {
        int position = getRank(field);
        if (position <= 0) return -1;
        if (position == 1) {
            return -1;
        } else {
            int pointsOfNextRank = rankings.get(position-2).getScorer().getScore();
            return pointsOfNextRank - field.getScorer().getScore();
        }
    }

    protected BukkitTask spectatorTask() {
        return new BukkitRunnable() {

            @Override
            public void run() {
                Iterator<Player> it = spectators.iterator();
                while (it.hasNext()) {
                    Player player = it.next();
                    if (player.getGameMode() != GameMode.SPECTATOR) it.remove();
                    else player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(ChatColor.YELLOW + "To stop spectating, run /fitw spawn"));
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 20);
    }

}
