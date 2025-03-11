package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.Wall;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class PopIn extends ModifierEvent {
    private final int MAX_BLOCK_COUNT = 5;
    private final Set<BlockDisplay> blockDisplays = new HashSet<>();
    public PopIn(PlayingField field) {
        super(field);
    }

    /*
    Right after the player submits a wall, up to five blocks will pop in on the playing field.
    Each has a 50% chance to fill in one of the holes in the incoming wall.
     */

    @Override
    public void activate() {
        super.activate();
        field.sendTitleToPlayers(ChatColor.RED + "Pop-in", "Random blocks may appear...", 0, 40, 10);
    }

    @Override
    public void end() {
        super.end();
        for (BlockDisplay display : blockDisplays) {
            display.remove();
        }
        blockDisplays.clear();
        field.sendTitleToPlayers("", ChatColor.RED + "Blocks no longer randomly pop in!", 0, 20, 10);
    }

    @Override
    public void onWallScore(Wall wall) {
        new BukkitRunnable() {

            @Override
            public void run() {
                if (!active) return;
                Random random = new Random();
                HashSet<Pair<Integer, Integer>> coordinates = new HashSet<>();
                Wall incomingWall = field.getQueue().getFrontmostWall();
                if (incomingWall == null) return;
                for (int i = 0; i < random.nextInt(1, MAX_BLOCK_COUNT); i++) {
                    if (Math.random() < 0.5 && !coordinates.containsAll(incomingWall.getHoles())) {
                        for (Pair<Integer, Integer> hole : incomingWall.getHoles()) {
                            if (!coordinates.contains(hole)) {
                                coordinates.add(hole);
                                break;
                            }
                        }
                    } else {
                        coordinates.add(incomingWall.randomCoordinates());
                    }
                }

                for (Pair<Integer, Integer> coordinate : coordinates) {
                    Block block = field.coordinatesToBlock(coordinate);
                    field.getWorld().playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1, 1);
                    popInAnimation(block);
                }
            }
        }.runTaskLater(FillInTheWall.getInstance(), field.getClearDelay()+20);
    }

    @Override
    public void playActivateSound() {
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                field.playSoundToPlayers(Sound.ENTITY_ITEM_PICKUP, 1, (float) Math.pow(2, i/12.0));
                i++;
                if (i >= 12) cancel();
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    public void playDeactivateSound() {
        new BukkitRunnable() {
            int i = 12;
            @Override
            public void run() {
                field.playSoundToPlayers(Sound.ENTITY_ITEM_PICKUP, 1, (float) Math.pow(2, i/12.0));
                i--;
                if (i <= 0) cancel();
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    public void popInAnimation(Block block) {
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        BlockDisplay display = (BlockDisplay) field.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        display.setBlock(field.getPlayerMaterial().createBlockData());
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1), new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1)));
        display.setInterpolationDuration(1);
        blockDisplays.add(display);

        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= 3) {
                    block.setType(field.getPlayerMaterial());
                    display.remove();
                    blockDisplays.remove(display);
                    if (field.getScorer().getSettings().getBooleanAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS)) {
                        field.refreshIncorrectBlockHighlights(field.getQueue().getFrontmostWall());
                    }
                    cancel();
                    return;
                }
                float size = (float) -Math.pow((i-3)/3f, 2.0) + 1;
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(
                        new Vector3f(-size/2, -size/2, -size/2),
                        new AxisAngle4f(0, 0, 0, 1), new Vector3f(size, size, size),
                        new AxisAngle4f(0, 0, 0, 1)));
                i++;
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    public PopIn copy(PlayingField newPlayingField) {
        return new PopIn(newPlayingField);
    }
}
