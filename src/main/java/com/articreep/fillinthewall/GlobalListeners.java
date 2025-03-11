package com.articreep.fillinthewall;

import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

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
}
