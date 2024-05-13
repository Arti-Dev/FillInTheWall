package com.articreep.holeinthewall;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class HoleInTheWall extends JavaPlugin {
    private static HoleInTheWall instance = null;

    @Override
    public void onEnable() {
        instance = this;
        getCommand("holeinthewall").setExecutor(this);
        getServer().getPluginManager().registerEvents(new PlayingFieldManager(), this);
        Bukkit.getLogger().info(ChatColor.BLUE + "HoleInTheWall has been enabled!");
        System.out.println("lmao");

        saveDefaultConfig();

        PlayingFieldManager.parseConfig(getConfig());

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static HoleInTheWall getInstance() {
        return instance;
    }
}
