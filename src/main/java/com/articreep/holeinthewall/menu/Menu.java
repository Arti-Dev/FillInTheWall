package com.articreep.holeinthewall.menu;

import com.articreep.holeinthewall.*;
import com.articreep.holeinthewall.gamemode.Gamemode;
import com.articreep.holeinthewall.gamemode.GamemodeAttribute;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class Menu implements Listener {
    private final Location location;
    private TextDisplay select;
    private final PlayingField field;
    private int gamemodeIndex = 0;

    public Menu(Location location, PlayingField field) {
        this.location = location;
        this.field = field;
    }

    public void display() {
        select = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        setMenuGamemode(Gamemode.values()[gamemodeIndex]);
        select.setBillboard(Display.Billboard.CENTER);
        HoleInTheWall.getInstance().getServer().getPluginManager().registerEvents(this, HoleInTheWall.getInstance());
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        if (!field.getPlayers().contains(event.getPlayer())) return;
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            nextGamemode();
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                event.getClickedBlock().getType() == Material.LEVER) {
            confirmAndDespawn();
        }
    }

    @EventHandler
    public void onPlayerSwap(PlayerSwapHandItemsEvent event) {
        if (!field.getPlayers().contains(event.getPlayer())) return;
        event.setCancelled(true);
        // Confirm gamemode
        confirmAndDespawn();
    }

    private void nextGamemode() {
        gamemodeIndex++;
        if (gamemodeIndex >= Gamemode.values().length) {
            gamemodeIndex = 0;
        }
        setMenuGamemode(Gamemode.values()[gamemodeIndex]);
    }

    private void setMenuGamemode(Gamemode mode) {
        select.setText("Select a gamemode\n" +
                mode.getTitle() + "\n" +
                ChatColor.RESET + "Left click to change gamemode\n" +
                "Press [F] or your offhand key to confirm");
    }

    public void confirmAndDespawn() {
        if (field.isLocked()) {
            field.sendMessageToPlayers(ChatColor.RED + "Field is locked - cannot start game");
            despawn();
            return;
        }
        Gamemode mode = Gamemode.values()[gamemodeIndex];
        if (mode.hasAttribute(GamemodeAttribute.MULTIPLAYER)) {
            field.sendMessageToPlayers(ChatColor.RED + "You cannot start a multiplayer game through this menu!");
        } else {
            field.countdownStart(Gamemode.values()[gamemodeIndex]);
        }
        despawn();
    }

    public void despawn() {
        HandlerList.unregisterAll(this);
        select.remove();
    }
}
