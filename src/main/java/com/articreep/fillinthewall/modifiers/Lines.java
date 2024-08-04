package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Lines extends ModifierEvent implements Listener {

    public Lines(PlayingField field) {
        super(field);
    }

    @Override
    public void activate() {
        super.activate();
        FillInTheWall.getInstance().getServer().getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        field.sendTitleToPlayers(ChatColor.LIGHT_PURPLE + "Lines", "Placed blocks extend to the other side!", 0, 40, 10);
        field.playSoundToPlayers(Sound.ENTITY_SHEEP_AMBIENT, 1, 1);
    }

    @Override
    public void end() {
        super.end();
        field.playSoundToPlayers(Sound.ENTITY_SHEEP_SHEAR, 1, 1);
        field.sendTitleToPlayers("", "Placed blocks are back to normal!", 0, 20, 10);
        HandlerList.unregisterAll(this);
    }


    @EventHandler (priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (field.getPlayers().contains(event.getPlayer())) {
            Material material = event.getBlock().getType();
            Block blockAgainst = event.getBlockAgainst();
            Block blockPlaced = event.getBlock();
            Vector direction = blockPlaced.getLocation().toVector().subtract(blockAgainst.getLocation().toVector()).normalize();
            new BukkitRunnable() {
                final Location currentLocation = blockPlaced.getLocation().clone().add(direction);
                int i = 0;
                @Override
                public void run() {
                    Block currentBlock = currentLocation.getBlock();
                    if (!active || currentBlock.getType() != Material.AIR || !field.isInField(currentBlock.getLocation())) {
                        cancel();
                    } else {
                        currentBlock.setType(material);
                        field.getWorld().playSound(currentBlock.getLocation(), Sound.BLOCK_WOOL_PLACE,
                                1, (float) Math.pow(2, i/12.0));
                        i++;
                        currentLocation.add(direction);
                    }

                }
            }.runTaskTimer(FillInTheWall.getInstance(), 2, 2);
        }
    }

    public Lines copy(PlayingField newPlayingField) {
        Lines copy = new Lines(newPlayingField);
        return copy;
    }

}
