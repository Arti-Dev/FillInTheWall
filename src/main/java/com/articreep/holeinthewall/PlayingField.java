package com.articreep.holeinthewall;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayingField implements Listener {
    Player player;
    /**
     * Must be the bottom left corner of the playing field (NOT including the border blocks)
     */
    private final Location fieldReferencePoint;
    private final Vector fieldDirection; // parallel to the field
    private final Vector incomingDirection; // normal to the field
    private int score = 0;
    private final int height = 4;
    private final int length = 7;
    private WallQueue queue = null;
    private double bonus = 0;
    private BukkitTask task = null;
    private final List<Block> borderBlocks = new ArrayList<>();
    private Material defaultBorderMaterial = Material.GRAY_CONCRETE;
    private List<TextDisplay> textDisplays = new ArrayList<>();
    private TextDisplay scoreDisplay = null;
    private TextDisplay accuracyDisplay = null;
    private TextDisplay speedDisplay = null;
    private TextDisplay wallDisplay = null;
    private int wallsCleared = 0;
    private int rushResults = 0;
    private int rushResultsDisplayTime = 0;

    public PlayingField(Player player, Location referencePoint, Vector direction, Vector incomingDirection) {
        // define playing field in a very scuffed way
        this.fieldReferencePoint = referencePoint;
        this.fieldDirection = direction;
        this.incomingDirection = incomingDirection;
        this.player = player;
        spawnTextDisplays();

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

        task = tickLoop();
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

    /**
     * Matches the blocks in the playing field with the blocks in the wall and scores the player
     * @param wall Wall to check against
     */
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

        // Check score
        int score = correctBlocks.size() - extraBlocks.size();
        // todo split this into a separate rush method
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
                wallsCleared++;
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

            // todo sometimes the big title doesn't show for some reason
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
                double pitch = Math.pow(2, (double) (queue.getRush().getBoardsCleared() - 6) / 12);
                if (pitch > 2) pitch = 2;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, (float) pitch);
                queue.getRush().increaseBoardsCleared();
                critParticles();
            } else {
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 1, 1);
            }
        }
    }

    private void addScore(int score) {
        this.score += score;
        player.sendMessage("Current score: " + this.score);
    }

    /**
     * Fills the playing field with the given material
     * @param material the material to fill the field with
     */
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
        defaultBorderMaterial = Material.MAGMA_BLOCK;
        clearField();
        player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5F, 1);
        queue.activateRush();
    }

    public void endRush() {
        rushResultsDisplayTime = 80;
        defaultBorderMaterial = Material.GRAY_CONCRETE;
        clearField();
        player.playSound(player, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
        player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, player.getLocation(), 1);
        rushResults = queue.getRush().getBoardsCleared() * 4;
        addScore(rushResults);
        player.sendTitle(ChatColor.GREEN + "RUSH OVER!", ChatColor.GREEN + "" +
                queue.getRush().getBoardsCleared() + " walls cleared", 0, 40, 10);
    }

    public BukkitTask tickLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                wallDisplay.setText(ChatColor.GOLD + "Perfect Walls: " + wallsCleared);
                if (rushResultsDisplayTime > 0) {
                    rushResultsDisplayTime--;
                    scoreDisplay.setText(ChatColor.RED + "+" + ChatColor.BOLD + rushResults + " points from Rush!!!");
                } else {
                    scoreDisplay.setText(ChatColor.GREEN + "Score: " + score);
                }

                if (!queue.isRushEnabled()) {
                    ChatColor color = ChatColor.GRAY;
                    if (bonus > 3 && bonus < 7) {
                        color = ChatColor.YELLOW;
                    } else if (bonus <= 10) {
                        color = ChatColor.GREEN;
                    }
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(color + "Rush Meter: " + String.format("%.2f", bonus) + "/10"));
                } else {
                    ChatColor color = ChatColor.GRAY;
                    int cleared = queue.getRush().getBoardsCleared();
                    if (cleared > 3 && cleared < 7) {
                        color = ChatColor.YELLOW;
                    } else if (cleared <= 10) {
                        color = ChatColor.GREEN;
                    }
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(color + "" + ChatColor.BOLD + "Walls Cleared: " + cleared));
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    public void changeBorderBlocks(Material material) {
        for (Block block : borderBlocks) {
            block.setType(material);
        }
    }

    public void critParticles() {
        for (Block block : borderBlocks) {
           block.getWorld().spawnParticle(Particle.CRIT, block.getLocation(), 7, 0.5, 0.5, 0.5, 0.1);
        }
    }

    public void resetBorder() {
        changeBorderBlocks(defaultBorderMaterial);
    }

    public void stop() {
        task.cancel();
        for (TextDisplay display : textDisplays) {
            display.remove();
        }
        queue.stop();
    }

    public void spawnTextDisplays() {
        Location loc = getFieldReferencePoint().add(fieldDirection.clone().multiply(-1.5)
                .add(incomingDirection.clone().multiply(-3)));
        wallDisplay = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        wallDisplay.setText(ChatColor.GOLD + "Perfect Walls: " + wallsCleared);
        wallDisplay.setBillboard(Display.Billboard.CENTER);
        wallDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(1, 1, 1),
                new AxisAngle4f(0, 0, 0, 1)));

        loc = getFieldReferencePoint().add(fieldDirection.clone().multiply(7.5)
                .add(incomingDirection.clone().multiply(-3)));
        scoreDisplay = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        scoreDisplay.setText(ChatColor.GREEN + "Score: " + score);
        scoreDisplay.setBillboard(Display.Billboard.CENTER);

        textDisplays.add(wallDisplay);
        textDisplays.add(scoreDisplay);
    }
}
