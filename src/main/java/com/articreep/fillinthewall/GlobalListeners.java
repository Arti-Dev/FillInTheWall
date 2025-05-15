package com.articreep.fillinthewall;

import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.playerinfo.PlayerLevels;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;

public class GlobalListeners implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        if (!event.getPlayer().isOp()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        if (!event.getPlayer().isOp()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDamageEntityEvent(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!player.isOp()) return;
        if (event.getEntity() instanceof ItemFrame) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerSpawnEgg(PlayerInteractEvent event) {
        if (event.getPlayer().isOp()) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        if (item.getType().toString().contains("SPAWN_EGG")) {
            event.setCancelled(true);
        }
        if (item.getType().toString().contains("BUCKET")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onExplosion(EntityExplodeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked().isOp()) return;
        ItemStack cursorItem = event.getCursor();

        checkAndDeleteItem(event, cursorItem);
    }

    private void checkAndDeleteItem(InventoryClickEvent event, ItemStack clickedItem) {
        if (clickedItem != null && clickedItem.getType() != Material.AIR) {
            if (!clickedItem.getItemMeta().getPersistentDataContainer().has(PlayingField.gameKey, PersistentDataType.BOOLEAN)) {
                event.setCancelled(true);
                clickedItem.setType(Material.AIR);
                clickedItem.setAmount(0);
                event.getWhoClicked().sendMessage(ChatColor.DARK_GRAY + "Can't use this item!");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerSwapItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null) return;
        if (item.getType() == Material.AIR) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(PlayingField.gameKey, PersistentDataType.BOOLEAN)) {
            player.getInventory().setItem(event.getNewSlot(), new ItemStack(Material.AIR));
            player.sendMessage(ChatColor.DARK_GRAY + "Can't use this item!");
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer().isOp()) return;
        event.getPlayer().teleport(FillInTheWall.getInstance().getMultiplayerSpawn());
        // load level in cache
        Bukkit.getScheduler().runTaskAsynchronously(FillInTheWall.getInstance(), () -> {
            try {
                PlayerLevels.getRawXP(event.getPlayer().getUniqueId());
            } catch (SQLException e) {
                FillInTheWall.getInstance().getSLF4JLogger().error("Failed to cache player level for " + event.getPlayer().getName());
                e.printStackTrace();
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        Component prefix = null;
        try {
            prefix = PlayerLevels.getPrefix(player.getUniqueId());
        } catch (SQLException e) {
            event.setCancelled(false);
            return;
        }
        Bukkit.broadcast(Component.text("<")
                .append(prefix)
                .append(Component.text(" "))
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text("> ", NamedTextColor.WHITE))
                .append(event.message()).color(NamedTextColor.WHITE));
    }
}
