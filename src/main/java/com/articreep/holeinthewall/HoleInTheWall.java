package com.articreep.holeinthewall;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class HoleInTheWall extends JavaPlugin implements CommandExecutor {
    private static HoleInTheWall instance = null;

    @Override
    public void onEnable() {
        instance = this;
        getCommand("holeinthewall").setExecutor(this);

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
            PlayingField field = new PlayingField(player,
                    new Location(player.getWorld(), -261, -58, -301), new Vector(-1, 0, 0),
                    new Vector(0, 0, -1));
            WallQueue queue = new WallQueue(field);
            queue.addWall(new Wall());
        }
        return true;
    }
}
