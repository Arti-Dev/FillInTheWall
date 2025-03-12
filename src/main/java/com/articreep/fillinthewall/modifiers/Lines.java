package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.Wall;
import com.articreep.fillinthewall.WallBundle;
import org.bukkit.*;
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

    public Lines() {
        super();
    }

    @Override
    public void activate() {
        super.activate();
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        field.sendTitleToPlayers(ChatColor.LIGHT_PURPLE + "Lines", "Placed blocks extend to the other side!", 0, 40, 10);
        priorityWallBundle.getWalls().forEach(field.getQueue()::addPriorityWall);
    }

    @Override
    public void end() {
        super.end();
        field.sendTitleToPlayers("", "Placed blocks are back to normal!", 0, 20, 10);
        field.getQueue().clearPriorityHiddenWalls();
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

    public Lines copy() {
        Lines copy = new Lines();
        copy.priorityWallBundle = priorityWallBundle;
        return copy;
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_SHEEP_AMBIENT, 1, 1);
    }

    @Override
    public void playDeactivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_SHEEP_SHEAR, 1, 1);
    }

    public WallBundle generatePriorityWallBundle(int length, int height) {
        Random random = new Random();
        WallBundle bundle = new WallBundle();
        for (int i = 0; i < 3; i++) {
            // Choose a x and y coordinate
            // Generate holes along these lines
            // Remove up to 2 holes

            Wall wall = new Wall(length, height);
            ArrayList<Pair<Integer, Integer>> holes = new ArrayList<>();
            int x = random.nextInt(0, length);
            int y = random.nextInt(0, height);

            for (int k = 0; k < length; k++) {
                holes.add(Pair.with(k, y));
            }

            for (int j = 0; j < height; j++) {
                holes.add(Pair.with(x, j));
            }

            for (int l = 0; l < Math.random() * 2; l++) {
                holes.remove((int) (Math.random() * holes.size()));
            }

            wall.insertHoles(holes);
            bundle.addWall(wall);
        }

        return bundle;
    }

    @Override
    public void additionalInit(int length, int height) {
        priorityWallBundle = generatePriorityWallBundle(length, height);
    }
}
