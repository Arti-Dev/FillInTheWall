package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Just the O piece for now.
public class Multiplace extends ModifierEvent implements Listener {
    Map<Player, Set<BlockDisplay>> blockDisplays = new HashMap<>();

    public Multiplace(PlayingField field, int ticks) {
        super(field, ticks);
    }

    @Override
    public void activate() {
        super.activate();
        FillInTheWall.getInstance().getServer().getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        field.sendTitleToPlayers("Multiplace!", "Your blocks are 2x2 now!", 0, 40, 10);
    }

    @EventHandler (priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        if (field.getPlayers().contains(event.getPlayer())) {
            Set<Pair<Integer, Integer>> blockPlacements = calculateBlockPlacements(event.getBlock());

            // In the case that the box ends up not being part of the original placement, remove it (rare)
            // for some reason setCancelled will break everything, so we have to do this instead
            // todo this just breaks the entire thing and no blocks place
            if (!blockPlacements.contains(field.blockToCoordinates(event.getBlock()))) {
                event.getBlock().setType(Material.AIR);
            }

            for (Pair<Integer, Integer> coords : blockPlacements) {
                field.coordinatesToBlock(coords).setType(event.getBlock().getType());
            }
        }
    }

    // todo this needs a rewrite and a lot of bugfixing
    @EventHandler
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

        // Tetris Super Rotation System style
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                // Deep-copy set
                Set<Pair<Integer, Integer>> shiftedBlocksToPlace = new HashSet<>();
                for (Pair<Integer, Integer> coords : blocksToPlace) {
                    shiftedBlocksToPlace.add(Pair.with(coords.getValue0() + x, coords.getValue1() + y));
                }
                if (isNotOccupiedByOtherBlocks(shiftedBlocksToPlace, pivot)) return shiftedBlocksToPlace;
            }
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
    }
}
