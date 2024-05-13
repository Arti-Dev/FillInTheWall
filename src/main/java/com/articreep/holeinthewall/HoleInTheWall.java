package com.articreep.holeinthewall;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HoleInTheWall extends JavaPlugin implements CommandExecutor {
    private static HoleInTheWall instance = null;

    @Override
    public void onEnable() {
        instance = this;
        getCommand("holeinthewall").setExecutor(this);
        getServer().getPluginManager().registerEvents(new PlayingFieldListeners(), this);
        Bukkit.getLogger().info(ChatColor.BLUE + "HoleInTheWall has been enabled!");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static HoleInTheWall getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            PlayingFieldListeners.newGame(player);
        }
        return true;
    }
}
