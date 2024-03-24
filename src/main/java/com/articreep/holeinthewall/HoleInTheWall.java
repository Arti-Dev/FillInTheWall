package com.articreep.holeinthewall;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.javatuples.Pair;

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
            Wall wall1 = new Wall();
            wall1.insertHoles(new Pair<>(0, 0));
            queue.addWall(wall1);
            Wall wall2 = new Wall();
            wall2.insertHoles(new Pair<>(0, 0), new Pair<>(0, 1), new Pair<>(1, 1));
            Wall wall3 = new Wall();
            wall3.insertHoles(new Pair<>(0, 0), new Pair<>(0, 1), new Pair<>(1, 1), new Pair<>(1, 0));
            queue.addWall(wall2);
            queue.addWall(wall3);
            Wall wall4 = new Wall();
            wall4.insertHoles(new Pair<>(0, 0), new Pair<>(0, 1), new Pair<>(1, 1), new Pair<>(1, 0), new Pair<>(2, 0));
            queue.addWall(wall4);

        }
        return true;
    }
}
