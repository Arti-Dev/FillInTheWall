package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.javatuples.Pair;

import java.util.HashSet;
import java.util.Set;

// Just the O piece for now.
public class Multiplace extends ModifierEvent implements Listener {

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
            if (!blockPlacements.contains(field.blockToCoordinates(event.getBlock()))) {
                event.getBlock().setType(Material.AIR);
            }

            for (Pair<Integer, Integer> coords : blockPlacements) {
                field.coordinatesToBlock(coords).setType(event.getBlock().getType());
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
        Bukkit.getLogger().info("Trying to place blocks in different positions");
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
                Bukkit.getLogger().info("Block at " + coords + " is occupied");
                return false;
            }
        }
        return true;
    }

    @Override
    public void end() {
        super.end();
        HandlerList.unregisterAll(this);
        field.sendTitleToPlayers("", "Block placements are back to normal!", 0, 20, 10);
    }
}
