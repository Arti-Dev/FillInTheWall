package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.Wall;
import com.articreep.fillinthewall.WallBundle;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

// Just the O piece for now.
public class Multiplace extends ModifierEvent implements Listener {
    Map<Player, Set<BlockDisplay>> blockDisplays = new HashMap<>();
    WallBundle priorityWallBundle;

    public Multiplace() {
        super();
    }

    @Override
    public void activate() {
        super.activate();
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        field.sendTitleToPlayers(ChatColor.GOLD + "Multiplace!", "Your blocks are 2x2 now!", 0, 40, 10);
        if (priorityWallBundle == null) {
            priorityWallBundle = generatePriorityWallBundle(field.getLength(), field.getHeight());
        }
        priorityWallBundle.getWalls().forEach(field.getQueue()::addWall);
    }

    @EventHandler (priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        if (field.getPlayers().contains(event.getPlayer())) {
            Set<Pair<Integer, Integer>> blockPlacements = calculateBlockPlacements(event.getBlock());

            for (Pair<Integer, Integer> coords : blockPlacements) {
                field.coordinatesToBlock(coords).setType(event.getBlock().getType());
            }
        }
    }

    // todo this needs a rewrite and a lot of bugfixing
    //@EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Block block = event.getPlayer().getLastTwoTargetBlocks(null, 5).getFirst();
        Material material = player.getInventory().getItemInMainHand().getType();
        Set<Pair<Integer, Integer>> blockPlacements = calculateBlockPlacements(block);
        if (!blockDisplays.containsKey(player)) {
            blockDisplays.put(player, new HashSet<>());
        }
        Set<BlockDisplay> displays = blockDisplays.get(player);
        displays.forEach(BlockDisplay::remove);
        displays.clear();

        if (material.isBlock()) {
            for (Pair<Integer, Integer> coords : blockPlacements) {
                Location location = field.coordinatesToBlock(coords).getLocation().add(0.5, 0.5, 0.5);
                BlockDisplay display = (BlockDisplay) field.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
                display.setBlock(material.createBlockData());
                float size = 0.5f;
                display.setTransformation(new Transformation(
                        new Vector3f(-size/2, -size/2, -size/2),
                        new AxisAngle4f(0, 0, 0, 1), new Vector3f(size, size, size),
                        new AxisAngle4f(0, 0, 0, 1)));
                displays.add(display);
            }
        }

    }

    private Set<Pair<Integer, Integer>> calculateBlockPlacements(Block pivot) {
        Set<Pair<Integer, Integer>> blocksToPlace = new HashSet<>();
        if (!field.isInField(pivot.getLocation())) return blocksToPlace;

        Pair<Integer, Integer> pivotCoords = field.blockToCoordinates(pivot);
        blocksToPlace.add(pivotCoords);
        blocksToPlace.add(Pair.with(pivotCoords.getValue0() + 1, pivotCoords.getValue1()));
        blocksToPlace.add(Pair.with(pivotCoords.getValue0(), pivotCoords.getValue1() + 1));
        blocksToPlace.add(Pair.with(pivotCoords.getValue0() + 1, pivotCoords.getValue1() + 1));

        if (isNotOccupiedByOtherBlocks(blocksToPlace, pivot)) return blocksToPlace;

        int[] x = {-1, 0, -1};
        int[] y = {0, -1, -1};

        // Tetris Super Rotation System style
        for (int i = 0; i < 3; i++) {
            // Deep-copy set
            Set<Pair<Integer, Integer>> shiftedBlocksToPlace = new HashSet<>();
            for (Pair<Integer, Integer> coords : blocksToPlace) {
                shiftedBlocksToPlace.add(Pair.with(coords.getValue0() + x[i], coords.getValue1() + y[i]));
            }
            if (isNotOccupiedByOtherBlocks(shiftedBlocksToPlace, pivot)) return shiftedBlocksToPlace;
        }

        // If none work, simply return the pivot point
        blocksToPlace.clear();
        blocksToPlace.add(pivotCoords);
        return blocksToPlace;

    }

    private boolean isNotOccupiedByOtherBlocks(Set<Pair<Integer, Integer>> blockSet, Block pivot) {
        for (Pair<Integer, Integer> coords : blockSet) {
            Block block = field.coordinatesToBlock(coords);
            if (field.coordinatesToBlock(coords).getType() != Material.AIR && !block.equals(pivot)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void end() {
        super.end();
        for (Set<BlockDisplay> displays : blockDisplays.values()) {
            displays.forEach(BlockDisplay::remove);
        }
        blockDisplays.clear();

        HandlerList.unregisterAll(this);
        field.sendTitleToPlayers("", "Block placements are back to normal!", 0, 20, 10);
        field.getQueue().clearPriorityHiddenWalls();
    }

    public Multiplace copy() {
        Multiplace copy = new Multiplace();
        copy.priorityWallBundle = priorityWallBundle;
        return copy;
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ITEM_MACE_SMASH_GROUND, 1);
    }

    @Override
    public void playDeactivateSound() {
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= 4) {
                    cancel();
                    return;
                }
                field.playSoundToPlayers(Sound.BLOCK_HEAVY_CORE_PLACE, 1);
                i++;
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 2);
    }

    public WallBundle generatePriorityWallBundle(int length, int height) {
        WallBundle bundle = new WallBundle();
        Random random = new Random();
        // A simpler algorithm without wall kicking
        for (int i = 0; i < 3; i++) {
            // Choose a random hole
            // Expand it to a 2x2 hole
            // 50/50 chance to remove one of the holes at random
            // Insert into wall

            Wall wall = new Wall(field.getLength(), field.getHeight());
            for (int j = 0; j < 3; j++) {
                ArrayList<Pair<Integer, Integer>> holes = new ArrayList<>();
                Pair<Integer, Integer> randomCoords = Pair.with(random.nextInt(field.getLength() - 2), random.nextInt(field.getHeight() - 2));
                holes.add(randomCoords);
                holes.add(Pair.with(randomCoords.getValue0() + 1, randomCoords.getValue1()));
                holes.add(Pair.with(randomCoords.getValue0(), randomCoords.getValue1() + 1));
                holes.add(Pair.with(randomCoords.getValue0() + 1, randomCoords.getValue1() + 1));

                if (random.nextBoolean()) {
                    holes.remove((int) (Math.random() * holes.size()));
                }

                wall.insertHoles(holes);
            }
            bundle.addWall(wall);
        }
        return bundle;
    }

    @Override
    public void additionalInit(int length, int height) {
        priorityWallBundle = generatePriorityWallBundle(length, height);
    }
}
