package com.articreep.holeinthewall;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
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
            inventory.setItemInMainHand(confirmItem());
            Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> {
                inventory.setItemInMainHand(mainHand);
            }, 10);
        }
    }

    @EventHandler
    public void onCrackedStoneClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getClickedBlock().getType() == Material.CRACKED_STONE_BRICKS) {
                PlayingField field = playingFields.get(event.getPlayer());
                if (field != null) {
                    event.getPlayer().getWorld().spawnParticle(Particle.BLOCK,
                            event.getClickedBlock().getLocation(),
                            10, 0.5, 0.5, 0.5, 0.1,
                            Material.CRACKED_STONE_BRICKS.createBlockData());
                    Bukkit.getScheduler().runTask(HoleInTheWall.getInstance(),
                            () -> event.getClickedBlock().breakNaturally(new ItemStack(Material.LEAD)));
                }
            }
        }
    }

    public static void newGame(Player player) {
        PlayingField field = playingFields.get(player);
        if (field != null) {
            removeGame(player);
            player.sendMessage("Stopped Hole in the Wall");
            return;
        }
        field = new PlayingField(player,
                new Location(player.getWorld(), -261, -58, -301), new Vector(-1, 0, 0),
                new Vector(0, 0, -1));
        // constructor automatically adds this queue to the playingfield object
        WallQueue queue = new WallQueue(field);
        Wall wall1 = new Wall();
        wall1.insertHoles(new Pair<>(3, 1), new Pair<>(4, 1));
        queue.addWall(wall1);

        playingFields.put(player, field);
    }

    public static void removeGame(Player player) {
        PlayingField field = playingFields.get(player);
        if (field != null) {
            field.stop();
            playingFields.remove(player);
        }
    }

    public static ItemStack confirmItem() {
        ItemStack item = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRM");
        item.setItemMeta(meta);
        return item;
    }
}
