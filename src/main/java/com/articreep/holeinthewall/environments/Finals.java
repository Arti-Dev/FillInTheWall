package com.articreep.holeinthewall.environments;

import com.articreep.holeinthewall.HoleInTheWall;
import com.articreep.holeinthewall.PlayingField;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class Finals implements Listener {
    private static final Set<Block> noFlowBlocks = new HashSet<>();

    @EventHandler
    public void onFlow(BlockFromToEvent event) {
        if (noFlowBlocks.contains(event.getBlock())) event.setCancelled(true);
    }

    public static void torchGeyser(PlayingField field) {
        // hardcoded values lol
        Location left = field.getReferencePoint()
                .add(0.5, 0.5, 0.5)
                // todo not sure why i have to shift over by 8 compared to 6???
                .add(field.getFieldDirection().multiply(-8))
                .add(field.getIncomingDirection().multiply(-14))
                .add(0, -5, 0);
        Location right = field.getReferencePoint()
                .add(0.5, 0.5, 0.5)
                .add(field.getFieldDirection().multiply(field.getLength() + 6))
                .add(field.getIncomingDirection().multiply(-14))
                .add(0, -5, 0);
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                Finals.singleTorchGeyser(left);
                Finals.singleTorchGeyser(right);
                left.add(field.getFieldDirection().multiply(-1)).add(field.getIncomingDirection().multiply(1));
                right.add(field.getFieldDirection().multiply(1)).add(field.getIncomingDirection().multiply(1));
                i++;
                if (i >= Math.min(10, field.getScorer().getPerfectWallChain())) cancel();
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 3);
    }

    // FINALS_TORCH_GEYSER
    public static void singleTorchGeyser(Location referenceLocation) {
        referenceLocation = referenceLocation.clone();
        World world = referenceLocation.getWorld();

        List<BlockDisplay> blockDisplays = new ArrayList<>();
        BlockDisplay fence = (BlockDisplay) world.spawnEntity(referenceLocation.clone().add(0, 1, 0), EntityType.BLOCK_DISPLAY);
        fence.setBlock(Material.CRIMSON_FENCE.createBlockData());
        BlockDisplay hopper = (BlockDisplay) world.spawnEntity(referenceLocation.clone().add(0, 2, 0), EntityType.BLOCK_DISPLAY);
        hopper.setBlock(Material.HOPPER.createBlockData());
        BlockDisplay fire = (BlockDisplay) world.spawnEntity(referenceLocation.clone().add(0, 3, 0), EntityType.BLOCK_DISPLAY);
        fire.setBlock(Material.AIR.createBlockData());

        blockDisplays.add(fence);
        blockDisplays.add(hopper);
        blockDisplays.add(fire);

        for (BlockDisplay blockDisplay : blockDisplays) {
            blockDisplay.setTeleportDuration(1);
            blockDisplay.setInterpolationDuration(1);
        }

        Location finalReferenceLocation = referenceLocation;
        new BukkitRunnable() {
            int ticks = 0;
            final double acceleration = 7.0;
            final double gravity = -15.0;
            final Deque<Block> waterStack = new ArrayDeque<>();
            double torchVelocity = 10;
            double torchOffset = -3;

            boolean playedWaterSound = false;

            final int waterDespawnPeriod = 5;

            int phase = 0;
            @Override
            public void run() {
                /*
                A few phases:
                - Water is actively propelling the fire torch up at some acceleration (10 ticks)
                - Water and torch continue to rise but have stopped accelerating upwards
                and is now affected by gravity (5 ticks)
                - Water stops rising and starts to go back down at some linear speed
                - Torch continues to rise and is magically lit at the peak
                - Torch despawns when it falls below its initial spawn location
                 */
                if (phase == 0) {
                    torchVelocity += acceleration / 20;
                } else {
                    if (torchVelocity > 0 && torchVelocity < Math.abs(gravity / 20)) {
                        lightTorch(fire, torchOffset);
                    }
                    torchVelocity += gravity / 20;
                }

                if (phase == 0 && ticks > 10) {
                    phase = 1;
                } else if (phase == 1 && ticks > 15) {
                    phase = 2;
                }

                torchOffset += torchVelocity / 20;

                for (BlockDisplay blockDisplay : blockDisplays) {
                    blockDisplay.setInterpolationDelay(0);
                    blockDisplay.setTransformation(new Transformation(
                            new Vector3f(0, (float) torchOffset, 0),
                            new AxisAngle4f(0, 0, 0, 1), new Vector3f(1, 1, 1),
                            new AxisAngle4f(0, 0, 0, 1)));
                }

                if (phase <= 1) {
                    // Set water blocks until deque size is equal to how much the torch is offset
                    // todo currently not storing the original blocks.. might want to in the future
                    while (waterStack.size() < torchOffset) {
                        Location location = finalReferenceLocation.clone().add(0, waterStack.size() + 1, 0);
                        Block block = location.getBlock();
                        if (block.getType() == Material.AIR || block.getType() == Material.WATER || block.getType() == Material.BUBBLE_COLUMN) {
                            block.setType(Material.WATER);
                            noFlowBlocks.add(block);
                            waterStack.push(block);
                            if (!playedWaterSound) {
                                playedWaterSound = true;
                                world.playSound(location, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 5, 0.5f);
                            }
                        } else {
                            phase = 2;
                            break;
                        }
                    }
                } else if (phase == 2) {
                    // Remove water blocks until deque is empty
                    if (ticks % waterDespawnPeriod == 0 && !waterStack.isEmpty()) {
                        Block block = waterStack.poll();
                        noFlowBlocks.remove(block);
                        block.setType(Material.AIR);
                    }
                }

                if (torchOffset < -5) {
                    for (BlockDisplay blockDisplay : blockDisplays) {
                        blockDisplay.remove();
                    }
                    for (Block block : waterStack) {
                        block.setType(Material.AIR);
                        noFlowBlocks.remove(block);
                    }
                    cancel();
                }

                ticks++;

            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    private static void lightTorch(BlockDisplay display, double offset) {
        Location location = display.getLocation().add(0, offset, 0);
        display.setBlock(Material.SOUL_FIRE.createBlockData());
        display.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, display.getLocation().add(0.5, offset + 0.5, 0.5), 20, 0, 0, 0, 0.3);
        display.getWorld().playSound(display.getLocation().add(0, offset, 0), Sound.ENTITY_BLAZE_SHOOT, 5, 1);

        // todo questionable but it should be okay for now
        location.getBlock().setType(Material.LIGHT);
        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () ->
                location.getBlock().setType(Material.AIR), 20 * 3);

    }
}
