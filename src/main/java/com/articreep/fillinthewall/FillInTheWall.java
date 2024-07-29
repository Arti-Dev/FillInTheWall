package com.articreep.fillinthewall;

import com.articreep.fillinthewall.environments.Finals;
import com.articreep.fillinthewall.environments.TheVoid;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.modifiers.*;
import com.articreep.fillinthewall.multiplayer.Pregame;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class FillInTheWall extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static FillInTheWall instance = null;
    private FileConfiguration playingFieldConfig;

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

        loadPlayingFieldConfig();
        saveDefaultConfig();

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            // todo temporary
            PlayingFieldManager.pregame = new Pregame(Bukkit.getWorld("multi"), Gamemode.MULTIPLAYER_SCORE_ATTACK,
                    2, 30);
            PlayingFieldManager.vsPregame = new Pregame(Bukkit.getWorld("versus"), Gamemode.VERSUS, 2, 15);
            PlayingFieldManager.parseConfig(getPlayingFieldConfig());
        }, 1);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static FillInTheWall getInstance() {
        return instance;
    }

    public FileConfiguration getPlayingFieldConfig() {
        return playingFieldConfig;
    }

    public void savePlayingFieldConfig() {
        try {
            playingFieldConfig.save(new File(getDataFolder(), "playingfields.yml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                if (args.length == 2 && sender instanceof Player player && PlayingFieldManager.isInGame(player)) {
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field.getScorer().getGamemode() == Gamemode.CUSTOM) {
                        WallBundle bundle = WallBundle.getWallBundle(args[1]);
                        if (bundle.size() == 0) {
                            sender.sendMessage(ChatColor.RED + "Something went wrong loading custom walls!");
                        } else {
                            List<Wall> walls = bundle.getWalls();
                            field.getQueue().clearAllWalls();
                            walls.forEach(field.getQueue()::addWall);
                            sender.sendMessage(ChatColor.GREEN + "Imported " + walls.size() + " walls");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "You can only use this command in custom mode");
                    }
                } else {
                    sender.sendMessage("Wrong syntax... I won't tell you how though! >:)");
                }
            } else if (args[0].equalsIgnoreCase("modifier")) {
                if (args.length == 3 && sender instanceof Player player && PlayingFieldManager.isInGame(player)) {
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    int ticks = Integer.parseInt(args[2]);

                    ModifierEvent event;
                    if (args[1].equalsIgnoreCase("popin")) event = new PopIn(field);
                    else if (args[1].equalsIgnoreCase("freeze")) event = new Freeze(field);
                    else if (args[1].equalsIgnoreCase("rush")) event = new Rush(field);
                    else if (args[1].equalsIgnoreCase("scale")) event = new Scale(field);
                    else if (args[1].equalsIgnoreCase("line")) event = new Lines(field);
                    else if (args[1].equalsIgnoreCase("inverted")) event = new Inverted(field);
                    else if (args[1].equalsIgnoreCase("fireinthehole")) event = new FireInTheHole(field);
                    else if (args[1].equalsIgnoreCase("stripes")) event = new Stripes(field);
                    else if (args[1].equalsIgnoreCase("gravity")) event = new Gravity(field);
                    else if (args[1].equalsIgnoreCase("playerinthewall")) event = new PlayerInTheWall(field);
                    else if (args[1].equalsIgnoreCase("multiplace")) event = new Multiplace(field);
                    else if (args[1].equalsIgnoreCase("flip")) event = new Flip(field);
                    else {
                        sender.sendMessage(ChatColor.RED + "Unknown modifier");
                        return true;
                    }

                    event.setTicksRemaining(ticks);
                    event.activate();
                } else {
                    sender.sendMessage("/hitw modifier <mod> <ticks>");
                }

            } else {
                return false;
            }
        }
        return true;
    }

    public void reloadConfig() {
        super.reloadConfig();
        loadPlayingFieldConfig();
        PlayingFieldManager.removeAllGames();
        PlayingFieldManager.parseConfig(getPlayingFieldConfig());
    }

    private void loadPlayingFieldConfig() {
        File playingFieldFile = new File(getDataFolder(), "playingfields.yml");
        if (!playingFieldFile.exists()) {
            playingFieldFile.getParentFile().mkdirs();
            saveResource("playingfields.yml", false);
        }
        playingFieldConfig = YamlConfiguration.loadConfiguration(playingFieldFile);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        ArrayList<String> strings = new ArrayList<>();
        if (args.length == 1) {
            strings.add("modifier");
            StringUtil.copyPartialMatches(args[0], strings, completions);
        } else if (args.length == 2) {
            strings.add("popin");
            strings.add("freeze");
            strings.add("rush");
            strings.add("scale");
            strings.add("line");
            strings.add("inverted");
            strings.add("fireinthehole");
            strings.add("stripes");
            strings.add("gravity");
            strings.add("playerinthewall");
            strings.add("multiplace");
            strings.add("flip");
            StringUtil.copyPartialMatches(args[1], strings, completions);
        }
        return completions;
    }
}
