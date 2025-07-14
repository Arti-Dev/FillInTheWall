package com.articreep.fillinthewall.commands;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.game.*;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.modifiers.ModifierEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FITWCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("reload") && sender.isOp()) {
                FillInTheWall.getInstance().reload();
                sender.sendMessage(miniMessage.deserialize("<green>Config reloaded!"));
                return true;
            } else if (args[0].equalsIgnoreCase("abort") && sender.isOp()) {
                if (PlayingFieldManager.game != null) {
                    PlayingFieldManager.game.setIncompleteGame(true);
                    PlayingFieldManager.game.stop();
                    PlayingFieldManager.game = null;
                    sender.sendMessage("Score attack game aborted");
                } else {
                    sender.sendMessage("No score attack game to abort");
                }

                if (PlayingFieldManager.vsGame != null) {
                    PlayingFieldManager.vsGame.setIncompleteGame(true);
                    PlayingFieldManager.vsGame.stop();
                    PlayingFieldManager.vsGame = null;
                    sender.sendMessage("Versus game aborted");
                } else {
                    sender.sendMessage("No versus game to abort");
                }
            } else if (args[0].equalsIgnoreCase("timer") && sender.isOp()) {
                if (PlayingFieldManager.pregame.isActive()) {
                    PlayingFieldManager.pregame.cancelCountdown();
                    sender.sendMessage("Score attack timer cancelled");
                } else {
                    PlayingFieldManager.pregame.startCountdown();
                    sender.sendMessage("Score attack timer started");
                }

                if (PlayingFieldManager.vsPregame.isActive()) {
                    PlayingFieldManager.vsPregame.cancelCountdown();
                    sender.sendMessage("Versus timer cancelled");
                } else {
                    PlayingFieldManager.vsPregame.startCountdown();
                    sender.sendMessage("Versus timer started");
                }
            } else if (args[0].equalsIgnoreCase("versus") && sender.isOp()) {
                if (PlayingFieldManager.vsPregame.isActive()) {
                    PlayingFieldManager.vsPregame.cancelCountdown();
                    sender.sendMessage("Timer cancelled");
                } else {
                    PlayingFieldManager.vsPregame.startCountdown();
                    sender.sendMessage("Timer started");
                }
            } else if (args[0].equalsIgnoreCase("start") && sender.isOp()) {
                if (args.length >= 2 && args[1].equalsIgnoreCase("versus")) {
                    if (PlayingFieldManager.vsPregame.isActive()) {
                        PlayingFieldManager.vsPregame.startGame();
                        sender.sendMessage("Starting versus game");
                    } else {
                        sender.sendMessage("Start a timer with /fillinthewall versus first");
                    }
                    return true;
                } else {
                    if (PlayingFieldManager.pregame.isActive()) {
                        PlayingFieldManager.pregame.startGame();
                        sender.sendMessage("Starting game");
                    } else {
                        sender.sendMessage("Start a timer with /fillinthewall timer first");
                    }
                }

            } else if (args[0].equalsIgnoreCase("custom")) {
                if (args.length == 2 && sender instanceof Player player && PlayingFieldManager.isInGame(player)) {
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field.getScorer().getGamemode() == Gamemode.SANDBOX) {
                        WallBundle bundle = WallBundle.getWallBundle(args[1]);
                        if (bundle.size() == 0) {
                            sender.sendMessage(miniMessage.deserialize("<red>Something went wrong loading custom walls!"));
                        } else {
                            List<Wall> walls = bundle.getWalls();
                            field.getQueue().clearAllWalls();
                            walls.forEach(field.getQueue()::addWall);
                            sender.sendMessage(miniMessage.deserialize("<green>Imported " + walls.size() + " walls"));
                            field.getScorer().setHasImportedCustomWalls(true);
                        }
                    } else {
                        sender.sendMessage(miniMessage.deserialize("<red>You can only use this command in custom mode."));
                    }
                } else {
                    sender.sendMessage("Wrong syntax... I won't tell you how though! >:)");
                }
            } else if (args[0].equalsIgnoreCase("modifier") && sender.isOp()) {
                if (args.length == 4) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw modifier <player> <mod> <ticks>");
                        return true;
                    }
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field == null) {
                        sender.sendMessage("This player isn't in a game!");
                        return true;
                    }
                    int ticks = Integer.parseInt(args[3]);

                    ModifierEvent event;
                    try {
                        event = ModifierEvent.Type.valueOf(args[2].toUpperCase()).createEvent();
                        if (event == null) return true;
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(miniMessage.deserialize("<red>Unknown modifier"));
                        return true;
                    }

                    event.setTicksRemaining(ticks);
                    event.setPlayingField(field);
                    event.additionalInit(field.getLength(), field.getHeight());
                    event.activate();
                } else {
                    sender.sendMessage("/fitw modifier <player> <mod> <ticks>");
                }

            } else if (args[0].equalsIgnoreCase("spawn") && sender instanceof Player player) {
                player.teleport(FillInTheWall.getInstance().getMultiplayerSpawn());
                player.setGameMode(GameMode.ADVENTURE);
            } else if (args[0].equalsIgnoreCase("garbage") && sender.isOp()) {
                if (args.length == 3) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw garbage <player> <amount>");
                        return true;
                    }
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field == null) {
                        sender.sendMessage("This player isn't in a game!");
                        return true;
                    }

                    int amount = Integer.parseInt(args[2]);

                    for (int i = 0; i < amount; i++) {
                        field.getScorer().getGarbageQueue().add(new Wall(field.getLength(), field.getHeight()));
                    }

                    sender.sendMessage("Sent " + amount + " garbage walls to " + player.getName());
                } else {
                    sender.sendMessage("/fitw garbage <player> <amount>");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("bundle") && sender.isOp()) {
                if (args.length == 3) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw bundle <player> <bundlename>");
                        return true;
                    }
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field == null) {
                        sender.sendMessage("This player isn't in a game!");
                        return true;
                    }

                    WallBundle bundle = WallBundle.getWallBundle(args[2]);
                    if (bundle.size() == 0) {
                        sender.sendMessage(miniMessage.deserialize("<red>Something went wrong loading custom walls!"));
                    } else {
                        List<Wall> walls = bundle.getWalls();
                        walls.forEach(field.getQueue()::addPriorityWall);
                        sender.sendMessage(miniMessage.deserialize("<green>Imported " + walls.size() + " walls"));
                    }
                } else {
                    sender.sendMessage("/fitw bundle <player> <bundlename>");
                    return true;
                }
            } else if (args[0].equalsIgnoreCase("tip") && sender.isOp()) {
                if (args.length >= 3) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw tip <player> <string>");
                        return true;
                    }
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field == null) {
                        sender.sendMessage("This player isn't in a game!");
                        return true;
                    }

                    StringBuilder tip = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        tip.append(args[i]);
                        tip.append(" ");
                    }

                    field.setTipDisplay(MiniMessage.miniMessage().deserialize(tip.toString()), true);
                }
            } else if (args[0].equalsIgnoreCase("demomode") && sender.isOp()) {
                if (args.length >= 2) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw demomode <player>");
                        return true;
                    }

                    ClientboundGameEventPacket packet = new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, 0);
                    ((CraftPlayer) player).getHandle().connection.send(packet);
                }
            } else if (args[0].equalsIgnoreCase("endcredits") && sender.isOp()) {
                if (args.length >= 2) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw endcredits <player>");
                        return true;
                    }

                    ClientboundGameEventPacket packet = new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1);
                    ((CraftPlayer) player).getHandle().connection.send(packet);
                }
            } else if (args[0].equalsIgnoreCase("swap") && sender.isOp()) {
                if (args.length >= 2) {
                    String name = args[1];
                    PlayingField field = PlayingFieldManager.activePlayingFields.get((Player) sender);
                    if (field == null) {
                        sender.sendMessage("This player isn't in a game!");
                        return true;
                    }
                    BuildSwapper.swapBuild(field, name);
                    sender.sendMessage("Attempted a swap!");
                } else {
                    sender.sendMessage("/fitw swap <buildname>");
                }
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        ArrayList<String> strings = new ArrayList<>();
        if (args.length == 1) {
            strings.add("spawn");
            strings.add("custom");

            if (sender.isOp()) {
                strings.add("reload");
                strings.add("abort");
                strings.add("timer");
                strings.add("start");
                strings.add("versus");
                strings.add("garbage");
                strings.add("bundle");
                strings.add("tip");
                strings.add("modifier");
                strings.add("demomode");
                strings.add("endcredits");
                strings.add("swap");
            }
            StringUtil.copyPartialMatches(args[0], strings, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("custom")) {
                StringUtil.copyPartialMatches(args[1], WallBundle.getAvailableWallBundles(), completions);
            } else if (args[0].equalsIgnoreCase("modifier")|| args[0].equalsIgnoreCase("garbage")
                    || args[0].equalsIgnoreCase("bundle") || args[0].equalsIgnoreCase("tip")
                    || args[0].equalsIgnoreCase("endcredits") || args[0].equalsIgnoreCase("demomode")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    strings.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[1], strings, completions);
            } else if (args[0].equalsIgnoreCase("swap")) {
                StringUtil.copyPartialMatches(args[1], BuildSwapper.getAvailableSchematics(), completions);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("modifier")) {
                for (ModifierEvent.Type type : ModifierEvent.Type.values()) {
                    strings.add(type.name());
                }
                StringUtil.copyPartialMatches(args[2], strings, completions);
            } else if (args[0].equalsIgnoreCase("bundle")) {
                StringUtil.copyPartialMatches(args[2], WallBundle.getAvailableWallBundles(), completions);
            }
        }
        return completions;
    }
}
