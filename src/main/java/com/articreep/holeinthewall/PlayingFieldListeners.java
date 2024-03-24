package com.articreep.holeinthewall;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.Lever;
import org.bukkit.util.Vector;
import org.javatuples.Pair;

import java.util.HashMap;
import java.util.Map;

public class PlayingFieldListeners implements Listener {
    public static Map<Player, PlayingField> playingFields = new HashMap<>();
    @EventHandler
    public void onLeverFlick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                event.getClickedBlock().getType() == Material.LEVER) {
            PlayingField field = playingFields.get(event.getPlayer());
            if (field != null) {
                field.getQueue().instantSend();
            }
        }
    }

    @EventHandler
    public void onSwitchToOffhand(PlayerSwapHandItemsEvent event) {
        PlayingField field = playingFields.get(event.getPlayer());
        if (field != null) {
            event.setCancelled(true);
            field.getQueue().instantSend();
            PlayerInventory inventory = event.getPlayer().getInventory();
            ItemStack mainHand = inventory.getItemInMainHand();
            // todo make this a custom item with custom name
            inventory.setItemInMainHand(new ItemStack(Material.GREEN_CONCRETE));
            Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> {
                inventory.setItemInMainHand(mainHand);
            }, 10);
        }
    }

    public static void newGame(Player player) {
        PlayingField field = playingFields.get(player);
        if (field != null) {
            player.sendMessage("Restarted Hole in the Wall");
            field.getQueue().stop();
        }
        field = new PlayingField(player,
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

        playingFields.put(player, field);
    }
}
