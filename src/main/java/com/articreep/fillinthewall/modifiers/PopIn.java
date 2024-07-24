package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.Wall;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import org.javatuples.Pair;

import java.util.HashSet;
import java.util.Random;

public class PopIn extends ModifierEvent {
    private final int MAX_BLOCK_COUNT = 5;
    public PopIn(PlayingField field, int ticks) {
        super(field, ticks);
    }

    /*
    Right after the player submits a wall, up to five blocks will pop in on the playing field.
    Each has a 50% chance to fill in one of the holes in the incoming wall.
     */

    @Override
    public void activate() {
        super.activate();
        field.sendTitleToPlayers(ChatColor.RED + "Pop-in", "Random blocks may appear...", 0, 40, 10);
        playIntroSound(field);
    }

    @Override
    public void end() {
        super.end();
        field.sendTitleToPlayers("", ChatColor.RED + "Blocks no longer randomly pop in!", 0, 20, 10);
        playOutroSound(field);
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
                    block.setType(field.getPlayerMaterial());
                    if (field.getScorer().getSettings().getBooleanAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS)) {
                        field.refreshIncorrectBlockHighlights(incomingWall);
                    }
                }
            }
        }.runTaskLater(FillInTheWall.getInstance(), field.getClearDelay()+20);
    }

    public static void playIntroSound(PlayingField field) {
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

    public static void playOutroSound(PlayingField field) {
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

    public static void popInAnimation(Block block) {
        // todo implement - display entity that scales up to fill the block space in around 5 ticks
    }
}
