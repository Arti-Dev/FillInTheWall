package com.articreep.holeinthewall;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayingField implements Listener {
    Player player;
    /**
     * Must be the bottom left corner of the playing field (NOT including the border blocks)
     */
    private Location fieldReferencePoint;
    private Vector fieldDirection;
    private Vector incomingDirection;
    private int score = 0;
    private final int height = 4;
    private final int length = 7;
    private WallQueue queue = null;
    private double bonus = 0;
    private BukkitTask task = null;
    private List<Block> borderBlocks = new ArrayList<>();
    private final Material defaultBorderMaterial = Material.GRAY_CONCRETE;

    public PlayingField(Player player, Location referencePoint, Vector direction, Vector incomingDirection) {
        // define playing field in a very scuffed way
        this.fieldReferencePoint = referencePoint;
        this.fieldDirection = direction;
        this.incomingDirection = incomingDirection;
        this.player = player;
        task = tickLoop();

        for (int x = 0; x < length + 2; x++) {
            for (int y = 0; y < height + 1; y++) {
                Location loc = getFieldReferencePoint().clone()
                        // Move to the block right to the left of the reference block
                        .subtract(fieldDirection)
                        .add(fieldDirection.clone().multiply(x))
                        .add(0, y, 0);
                // exclude corners
                if ((x == 0 || x == length + 1 || y == height) &&
                        !((x == 0 || x == length + 1) && y == height)) {
                    borderBlocks.add(loc.getBlock());
                }
            }
        }
    }

    /**
     * Returns all blocks in the playing field excluding air blocks.
     * @return all blocks in coordinate to block map
     */
    public Map<Pair<Integer, Integer>, Block> getPlayingFieldBlocks() {
        HashMap<Pair<Integer, Integer>, Block> blocks = new HashMap<>();
        // y direction loop
        for (int y = 0; y < height; y++) {
            Location loc = getFieldReferencePoint().add(0, y, 0);
            for (int x = 0; x < length; x++) {
                if (!loc.getBlock().isEmpty()) {
                    blocks.put(Pair.with(x, y), loc.getBlock());
                }
                loc.add(fieldDirection);
            }
        }
        return blocks;
    }

    public Location getFieldReferencePoint() {
        return fieldReferencePoint.clone();
    }

    public void matchAndScore(Wall wall) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(this);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(this);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(this);
        // Visually display this information
        int pauseTime = 10;
        if (queue.isRushEnabled()) pauseTime = 5;
        fillField(wall.getMaterial());
        for (Block block : extraBlocks.values()) {
            block.setType(Material.RED_WOOL);
        }
        for (Block block : correctBlocks.values()) {
            block.setType(Material.GREEN_WOOL);
        }
        for (Block block : missingBlocks.values()) {
            block.setType(Material.AIR);
        }
        boolean rushEnabledBeforehand = getQueue().isRushEnabled();
        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> {
            clearField();
            resetBorder();
            if (rushEnabledBeforehand && queue.isRushEnabled()) {
                for (Pair<Integer, Integer> hole : wall.getHoles()) {
                    coordinatesToBlock(hole).setType(Material.TINTED_GLASS);
                }
            }
        }, pauseTime);

        int score = correctBlocks.size() - extraBlocks.size();
        if (!queue.isRushEnabled()) {
            addScore(score);

            double percent = (double) score / wall.getHoles().size();
            String title = "";
            ChatColor color = ChatColor.GREEN;
            Material border = Material.GRAY_CONCRETE;
            if (percent == 1) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                color = ChatColor.GOLD;
                title = ChatColor.BOLD + "PERFECT!";
                border = Material.GLOWSTONE;
            } else {
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
            }
            if (percent < 1 && percent > 0.5) {
                title = "Cool!";
                border = Material.LIME_CONCRETE;
            } else if (percent < 0.5) {
                title = "Meh..";
                color = ChatColor.RED;
                border = Material.REDSTONE_BLOCK;
            }

            player.sendTitle(color + title, color + "+" + score + " points", 0, 10, 5);
            changeBorderBlocks(border);

            // Add/subtract to bonus and maybe even trigger rush
            if (percent >= 0.5) {
                bonus += percent;
                if (bonus >= 10) {
                    bonus = 0;
                    activateRush();
                }
            } else {
                bonus -= 2;
                if (bonus < 0) bonus = 0;
            }
        } else {
            double percent = (double) score / wall.getHoles().size();
            if (percent == 1) {
                // todo make this higher pitched over time
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                queue.getRush().increaseBoardsCleared();
            } else {
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
            }
        }
    }

    private void fillField(Material material) {
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                Location loc = getFieldReferencePoint().clone().add(fieldDirection.clone().multiply(x)).add(0, y, 0);
                loc.getBlock().setType(material);
            }
        }
    }

    private void clearField() {
        fillField(Material.AIR);
    }

    private void addScore(int score) {
        this.score += score;
        player.sendMessage("Current score: " + this.score);
    }

    public Vector getIncomingDirection() {
        return incomingDirection.clone();
    }

    public Vector getFieldDirection() {
        return fieldDirection.clone();
    }

    public Player getPlayer() {
        return player;
    }

    public Location getReferencePoint() {
        return fieldReferencePoint;
    }

    public Block coordinatesToBlock(Pair<Integer, Integer> coordinates) {
        return fieldReferencePoint.clone().add(fieldDirection.clone()
                .multiply(coordinates.getValue0())).add(0, coordinates.getValue1(), 0)
                .getBlock();

    }

    public void setQueue(WallQueue queue) {
        this.queue = queue;
    }

    public WallQueue getQueue() {
        return queue;
    }

    public void activateRush() {
        player.sendTitle(ChatColor.RED + "RUSH!", ChatColor.RED + "Clear as many walls as you can!", 0, 40, 10);
        clearField();
        queue.activateRush();
    }

    public void endRush() {
        clearField();
        player.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, player.getLocation(), 1);
        addScore(queue.getRush().getBoardsCleared() * 4);
        player.sendTitle(ChatColor.GREEN + "RUSH OVER!", ChatColor.GREEN + "" +
                queue.getRush().getBoardsCleared() + " walls cleared", 0, 40, 10);
    }

    public BukkitTask tickLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                ChatColor color = ChatColor.GRAY;
                if (bonus > 3 && bonus < 7) {
                    color = ChatColor.YELLOW;
                } else {
                    color = ChatColor.GREEN;
                }
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color + "Rush Meter: " + String.format("%.2f", bonus) + "/10"));
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    public void changeBorderBlocks(Material material) {
        for (Block block : borderBlocks) {
            block.setType(material);
        }

    }

    public void resetBorder() {
        changeBorderBlocks(defaultBorderMaterial);
    }
}
