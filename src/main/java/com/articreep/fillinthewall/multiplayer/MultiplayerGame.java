package com.articreep.fillinthewall.multiplayer;

import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.PlayingFieldManager;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.modifiers.ModifierEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public abstract class MultiplayerGame implements Listener {
    protected final Set<PlayingField> playingFields = new HashSet<>();
    protected final List<PlayingField> rankings = new ArrayList<>();
    protected WallGenerator generator;
    protected int time;
    protected BukkitTask mainTask = null;
    protected Set<BukkitTask> otherTasks = new HashSet<>();
    protected GamemodeSettings settings;
    protected Set<Player> spectators = new HashSet<>();
    protected int ticksBetweenSignals = 20;
    protected int signalCount = 3;

    public MultiplayerGame(List<PlayingField> fields, GamemodeSettings settings) {
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
        if (settings.getBooleanAttribute(GamemodeAttribute.COOP)) {
            generator.setCoop(true);
            generator.setWallHolesMax(16);
        }
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

        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());

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

        if (settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT) != ModifierEvent.Type.NONE) {
            deployEvent(settings.getModifierEventTypeAttribute(GamemodeAttribute.SINGULAR_EVENT), true);
        }
        generator.addNewWallToQueues();
        otherTasks.add(spectatorTask());
        otherTasks.add(disconnectTask());
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

        Set<Player> playersToTeleport = new HashSet<>(spectators);
        for (PlayingField field : playingFields) {
            field.getQueue().resetGenerator();
            field.setMultiplayerMode(false);
            field.getScorer().setMultiplayerGame(null);
            playersToTeleport.addAll(field.getPlayers());
        }

        if (markAsEnded) {
            HandlerList.unregisterAll(this);
            PlayingFieldManager.game = null;
            Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
                for (Player player : playersToTeleport) {
                    player.teleport(FillInTheWall.getInstance().getMultiplayerSpawn());
                    if (spectators.contains(player)) {
                        // todo may want to instead store the previous gamemode instead..?
                        player.setGameMode(GameMode.ADVENTURE);
                    }
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
            field.getScorer().setMultiplayerGame(null);
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

    protected BukkitTask disconnectTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<PlayingField> it = playingFields.iterator();
                while (it.hasNext()) {
                    PlayingField field = it.next();
                    if (field.playerCount() == 0) {
                        it.remove();
                        field.setMultiplayerMode(false);
                        field.getQueue().resetGenerator();
                        field.getScorer().setMultiplayerGame(null);
                        if (playingFields.isEmpty()) {
                            stop();
                        }
                    }
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    protected void deployEvent(ModifierEvent.Type type, boolean infinite) {
        PlayingField sampleField = playingFields.iterator().next();
        ModifierEvent event = type.createEvent();
        event.additionalInit(sampleField.getLength(), sampleField.getHeight());
        for (PlayingField field : playingFields) {
            ModifierEvent copy = field.getScorer().activateEvent(event.copy());
            if (infinite) copy.setInfinite(true);
        }
    }

    protected void deployEvent(ModifierEvent.Type type) {
        deployEvent(type, false);
    }

    protected void deployEventWithSignals(ModifierEvent.Type type) {
        PlayingField sampleField = playingFields.iterator().next();
        ModifierEvent event = type.createEvent();
        event.additionalInit(sampleField.getLength(), sampleField.getHeight());

        // Hold an event for each playing field until they're ready to be activated
        HashSet<ModifierEvent> events = new HashSet<>();
        for (PlayingField field : playingFields) {
            ModifierEvent copy = event.copy();
            copy.setPlayingField(field);
            events.add(copy);
        }

        new BukkitRunnable() {
            int signals = 0;
            @Override
            public void run() {
                if (signals < signalCount) {
                    ChatColor color = ChatColor.YELLOW;
                    if (signals == signalCount - 1) {
                        color = ChatColor.RED;
                    }

                    for (ModifierEvent event : events) {
                        if (signals == 0) event.playActivateSound();
                        else event.getPlayingField().playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_HAT, 1);
                        event.getPlayingField().sendTitleToPlayers(color + "⚠", "", 0, 5, 10);
                    }
                    signals++;
                } else {
                    for (ModifierEvent event : events) {
                        event.activate();
                    }
                    cancel();
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, ticksBetweenSignals);
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (spectators.contains(player)) {
            player.teleport(FillInTheWall.getInstance().getMultiplayerSpawn());
            player.setGameMode(GameMode.ADVENTURE);
            spectators.remove(player);
        }
    }

}
