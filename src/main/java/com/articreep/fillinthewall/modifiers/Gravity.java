package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.Wall;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class Gravity extends ModifierEvent {
    Vector direction;
    final double acceleration = 9.8;
    Set<BlockDisplay> blockDisplays = new HashSet<>();

    public Gravity(PlayingField field, int ticks) {
        super(field, ticks);
        List<Vector> possibleDirections = new ArrayList<>();
        possibleDirections.add(new Vector(0, -1, 0));
        possibleDirections.add(new Vector(0, 1, 0));
        possibleDirections.add(field.getFieldDirection());
        possibleDirections.add(field.getFieldDirection().multiply(-1));
        direction = possibleDirections.get((int) (Math.random() * possibleDirections.size()));
    }

    @Override
    public void activate() {
        super.activate();
        field.sendTitleToPlayers("Gravity!", "Blocks will fall in some direction!", 0, 40, 10);
    }

    @Override
    public void tick() {
        super.tick();
        if (field.isClearDelayActive()) return;
        Map<Pair<Integer, Integer>, Block> blocks = field.getPlayingFieldBlocks(true);
        for (Pair<Integer, Integer> coords : blocks.keySet()) {
            Block block = blocks.get(coords);
            if (block.getLocation().add(direction).getBlock().getType() == Material.AIR) {
                Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> convertToFallingBlock(block));
            }
        }
    }

    @Override
    public void onWallScore(Wall wall) {
        for (BlockDisplay display : blockDisplays) {
            Location location = display.getLocation().getBlock().getLocation();
            if (field.isInField(location)) {
                location.getBlock().setType(display.getBlock().getMaterial());
            }
            display.remove();
        }
        blockDisplays.clear();
    }

    public void convertToFallingBlock(Block block) {
        Material material = block.getType();
        block.setType(Material.AIR);
        BlockDisplay display = (BlockDisplay) block.getWorld().spawnEntity(block.getLocation().add(0.5, 0.5, 0.5), EntityType.BLOCK_DISPLAY);
        display.setTransformation(new Transformation(
                new Vector3f(-0.5f, -0.5f, -0.5f),
                new AxisAngle4f(0, 0, 0, 1), new Vector3f(1, 1, 1),
                new AxisAngle4f(0, 0, 0, 1)));
        display.setBlock(material.createBlockData());
        display.setTeleportDuration(1);
        blockDisplays.add(display);

        new BukkitRunnable() {
            double velocity = 0;
            @Override
            public void run() {
                if (display.isDead()) {
                    cancel();
                    return;
                }

                velocity += acceleration / 20;
                Location target = display.getLocation().add(direction.clone().multiply(velocity / 20));
                // Check half a block from this target to see if it'll end up inside a block
                Block block = target.clone().add(direction.clone().multiply(0.5)).getBlock();

                if (block.getType() != Material.AIR) {
                    // Attempt to settle down
                    // If another block's somehow already settled down...?
                    if (target.getBlock().getType() != Material.AIR) {
                        // Kick this block backwards by a bit
                        target = target.add(direction.clone().multiply(-0.5));
                        velocity = 0;
                        display.teleport(target);
                    } else {
                        // todo i should really stop having to "snap" locations to their block counterparts
                        if (field.isInField(target.getBlock().getLocation())) {
                            target.getBlock().setType(material);
                        } else {
                            Bukkit.getLogger().warning("Gravity block fell out of bounds!");
                        }
                        display.remove();
                        blockDisplays.remove(display);
                        cancel();
                    }
                } else {
                    display.teleport(target);
                }
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);

    }

    @Override
    public void end() {
        super.end();
        field.sendTitleToPlayers("", "Placed blocks are back to normal!", 0, 20, 10);
    }
}
