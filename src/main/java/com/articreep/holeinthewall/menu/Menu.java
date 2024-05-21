package com.articreep.holeinthewall.menu;

import com.articreep.holeinthewall.HoleInTheWall;
import com.articreep.holeinthewall.PlayingField;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class Menu implements Listener {
    private Location location;
    private TextDisplay select;
    private PlayingField field;

    public Menu(Player player, Location location, PlayingField field) {
        this.location = location;
        this.field = field;
    }

    public void display() {
        select = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        select.setText("Select a gamemode\n" +
                ChatColor.BOLD + "test\n" +
                "Left click to change gamemode\n" +
                "Right click to confirm");
        select.setBillboard(Display.Billboard.CENTER);
        HoleInTheWall.getInstance().getServer().getPluginManager().registerEvents(this, HoleInTheWall.getInstance());
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        if (!field.getPlayers().contains(event.getPlayer())) return;
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Change gamemode
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Confirm gamemode
            confirmAndDespawn();
        }
    }

    public void confirmAndDespawn() {
        despawn();
        field.start();
    }

    public void despawn() {
        HandlerList.unregisterAll(this);
        select.remove();
    }
}
