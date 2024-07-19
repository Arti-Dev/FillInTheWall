package com.articreep.fillinthewall;

import com.articreep.fillinthewall.environments.Finals;
import com.articreep.fillinthewall.environments.TheVoid;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.multiplayer.Pregame;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FillInTheWall extends JavaPlugin implements CommandExecutor {
    private static FillInTheWall instance = null;

    @Override
    public void onEnable() {
        instance = this;
        RegisterPlayingField registerPlayingField = new RegisterPlayingField();
        getCommand("fillinthewall").setExecutor(this);
        getCommand("registerplayingfield").setExecutor(registerPlayingField);
        getServer().getPluginManager().registerEvents(new PlayingFieldManager(), this);
        getServer().getPluginManager().registerEvents(new TheVoid(), this);
        getServer().getPluginManager().registerEvents(registerPlayingField, this);
        getServer().getPluginManager().registerEvents(new Finals(), this);
        Bukkit.getLogger().info(ChatColor.BLUE + "FillInTheWall has been enabled!");

        saveDefaultConfig();

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            // todo temporary
            PlayingFieldManager.pregame = new Pregame(Bukkit.getWorld("multi"), Gamemode.MULTIPLAYER_SCORE_ATTACK,
                    2, 30);
            PlayingFieldManager.vsPregame = new Pregame(Bukkit.getWorld("versus"), Gamemode.VERSUS, 2, 15);
            PlayingFieldManager.parseConfig(getConfig());
        }, 1);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static FillInTheWall getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
                return true;
            } else if (args[0].equalsIgnoreCase("abort")) {
                // todo everything from this line forth is temporary
                if (PlayingFieldManager.game != null) {
                    PlayingFieldManager.game.stop();
                    PlayingFieldManager.game = null;
                    sender.sendMessage("Score attack game aborted");
                } else {
                    sender.sendMessage("No score attack game to abort");
                }

                if (PlayingFieldManager.vsGame != null) {
                    PlayingFieldManager.vsGame.stop();
                    PlayingFieldManager.vsGame = null;
                    sender.sendMessage("Versus game aborted");
                } else {
                    sender.sendMessage("No versus game to abort");
                }
            } else if (args[0].equalsIgnoreCase("timer")) {
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
            } else if (args[0].equalsIgnoreCase("versus")) {
                if (PlayingFieldManager.vsPregame.isActive()) {
                    PlayingFieldManager.vsPregame.cancelCountdown();
                    sender.sendMessage("Timer cancelled");
                } else {
                    PlayingFieldManager.vsPregame.startCountdown();
                    sender.sendMessage("Timer started");
                }
            } else if (args[0].equalsIgnoreCase("start")) {
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
                if (args.length >= 2 && sender instanceof Player player && PlayingFieldManager.isInGame(player)) {
                    PlayingFieldManager.activePlayingFields.get(player).getQueue().addWall(Wall.parseWall(args[1]));
                } else {
                    sender.sendMessage("Wrong syntax... I won't tell you how though! >:)");
                }

            } else {
                return false;
            }
        }
        return true;
    }

    public void reloadConfig() {
        super.reloadConfig();
        PlayingFieldManager.removeAllGames();
        PlayingFieldManager.parseConfig(getConfig());
    }
}
