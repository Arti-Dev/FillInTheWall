package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.Wall;
import com.articreep.fillinthewall.WallBundle;
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
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.Random;

public class Lines extends ModifierEvent implements Listener {
    WallBundle priorityWallBundle;

    public Lines(PlayingField field) {
        super(field);
        priorityWallBundle = generatePriorityWallBundle();
    }

    @Override
    public void activate() {
        super.activate();
        FillInTheWall.getInstance().getServer().getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        field.sendTitleToPlayers(ChatColor.LIGHT_PURPLE + "Lines", "Placed blocks extend to the other side!", 0, 40, 10);
        field.playSoundToPlayers(Sound.ENTITY_SHEEP_AMBIENT, 1, 1);
        priorityWallBundle.getWalls().forEach(field.getQueue()::addWall);
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
        copy.priorityWallBundle = priorityWallBundle;
        return copy;
    }

    public WallBundle generatePriorityWallBundle() {
        Random random = new Random();
        WallBundle bundle = new WallBundle();
        for (int i = 0; i < 3; i++) {
            // Choose a x and y coordinate
            // Generate holes along these lines
            // Remove up to 2 holes

            Wall wall = new Wall(field.getLength(), field.getHeight());
            ArrayList<Pair<Integer, Integer>> holes = new ArrayList<>();
            int x = random.nextInt(0, field.getLength());
            int y = random.nextInt(0, field.getHeight());

            for (int j = 0; j < field.getHeight(); j++) {
                holes.add(Pair.with(x, j));
            }

            for (int k = 0; k < field.getLength(); k++) {
                holes.add(Pair.with(k, y));
            }

            for (int l = 0; l < Math.random() * 2; l++) {
                holes.remove((int) (Math.random() * holes.size()));
            }

            wall.insertHoles(holes);
            bundle.addWall(wall);
        }

        return bundle;
    }

}
