package com.articreep.holeinthewall;

import com.articreep.holeinthewall.modifiers.ModifierEvent;
import com.articreep.holeinthewall.modifiers.Rush;
import com.articreep.holeinthewall.utils.WorldBoundingBox;
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
import org.bukkit.util.BoundingBox;
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
    private final Vector fieldDirection; // parallel to the field, positive x direction
    private final Vector incomingDirection; // normal to the field
    private final int height = 4;
    private final int length = 7;

    private final List<Block> borderBlocks = new ArrayList<>();
    private final Material defaultBorderMaterial = Material.GRAY_CONCRETE;
    private final WorldBoundingBox boundingBox;

    private final List<TextDisplay> textDisplays = new ArrayList<>();
    private TextDisplay scoreDisplay = null;
    private TextDisplay accuracyDisplay = null;
    private TextDisplay speedDisplay = null;
    private TextDisplay wallDisplay = null;
    private boolean scoreDisplayOverride = false;

    private final PlayingFieldScorer scorer;
    private ModifierEvent event = null;
    private WallQueue queue = null;
    private BukkitTask task = null;

    public PlayingField(Player player, Location referencePoint, Vector direction, Vector incomingDirection, WorldBoundingBox boundingBox) {
        // define playing field in a very scuffed way
        this.fieldReferencePoint = referencePoint;
        this.fieldDirection = direction;
        this.incomingDirection = incomingDirection;
        this.player = player;
        this.scorer = new PlayingFieldScorer(this);
        this.queue = new WallQueue(this);
        this.boundingBox = boundingBox;

        for (int x = 0; x < length + 2; x++) {
            for (int y = 0; y < height + 1; y++) {
                Location loc = getReferencePoint().clone()
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
        // starter wall
        Wall wall1 = new Wall();
        wall1.insertHoles(new Pair<>(3, 1), new Pair<>(4, 1));
        queue.addWall(wall1);
    }

    public void start() {
        // Silently fail if this is already running
        if (hasStarted()) {
            Bukkit.getLogger().severe("Tried to start game that's already been started");
            return;
        }
        if (player == null) {
            throw new IllegalStateException("Player is null");
        }
        spawnTextDisplays();
        task = tickLoop();
    }

    public void stop() {
        if (!hasStarted()) {
            Bukkit.getLogger().severe("Tried to stop game that's already been stopped");
            return;
        }
        task.cancel();
        task = null;
        for (TextDisplay display : textDisplays) {
            display.remove();
        }
        queue.clearAllWalls();
        event = null;
        scorer.reset();
    }

    /**
     * Returns all blocks in the playing field excluding air blocks.
     * @return all blocks in coordinate to block map
     */
    public Map<Pair<Integer, Integer>, Block> getPlayingFieldBlocks() {
        HashMap<Pair<Integer, Integer>, Block> blocks = new HashMap<>();
        // y direction loop
        for (int y = 0; y < height; y++) {
            Location loc = getReferencePoint().add(0, y, 0);
            for (int x = 0; x < length; x++) {
                if (!loc.getBlock().isEmpty()) {
                    blocks.put(Pair.with(x, y), loc.getBlock());
                }
                loc.add(fieldDirection);
            }
        }
        return blocks;
    }

    public Location getReferencePoint() {
        return fieldReferencePoint.clone();
    }

    public Vector getIncomingDirection() {
        return incomingDirection.clone();
    }

    public Vector getFieldDirection() {
        return fieldDirection.clone();
    }

    public Block coordinatesToBlock(Pair<Integer, Integer> coordinates) {
        return fieldReferencePoint.clone().add(fieldDirection.clone()
                        .multiply(coordinates.getValue0())).add(0, coordinates.getValue1(), 0)
                .getBlock();

    }

    public Player getPlayer() {
        return player;
    }

    public void setQueue(WallQueue queue) {
        // todo this method might be used for swapping two players' queues in the future
        this.queue = queue;
    }

    public WallQueue getQueue() {
        return queue;
    }

    /**
     * Matches the blocks in the playing field with the blocks in the wall and scores the player
     * @param wall Wall to check against
     */
    public void matchAndScore(Wall wall) {
        // Check score
        // Events can override scoring
        if (!eventActive() || !event.overrideScoring) {
            PlayingFieldState state = scorer.scoreWall(wall, this);
            if (state.title != null) {
                player.sendTitle(state.titleColor + state.title, state.titleColor + "+" + state.score + " points", 0, 10, 5);
            }
            changeBorderBlocks(state.borderMaterial);
        } else {
            event.score(wall);
        }

        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(this);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(this);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(this);

        // Visually display what blocks were correct and what were wrong
        int pauseTime = 10;
        if (eventActive()) pauseTime = event.pauseTime;
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

        // Clear the field after the pauseTime
        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> {
            clearField();
            resetBorder();

            // Rush jank
            // todo might move to rush class
            if (eventActive() && event instanceof Rush rush) {
                if (rush.hasFirstWallCleared()) {
                    for (Pair<Integer, Integer> hole : wall.getHoles()) {
                        coordinatesToBlock(hole).setType(Material.TINTED_GLASS);
                    }
                } else {
                    rush.setFirstWallCleared(true);
                }
            }
        }, pauseTime);
    }

    // Block-related methods

    /**
     * Fills the playing field with the given material
     * @param material the material to fill the field with
     */
    private void fillField(Material material) {
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                Location loc = getReferencePoint().clone().add(fieldDirection.clone().multiply(x)).add(0, y, 0);
                loc.getBlock().setType(material);
            }
        }
    }

    public void clearField() {
        fillField(Material.AIR);
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

    // Events and ticking

    public BukkitTask tickLoop() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                wallDisplay.setText(ChatColor.GOLD + "Perfect Walls: " + scorer.getWallsCleared());
                if (!scoreDisplayOverride) {
                    scoreDisplay.setText(ChatColor.GREEN + "Score: " + scorer.getScore());
                }


                if (!eventActive() || event.actionBarOverride() == null) {
                    double bonus = scorer.getBonus();
                    ChatColor color;
                    if (bonus <= 3) {
                        color = ChatColor.GRAY;
                    } else if (bonus <= 7) {
                        color = ChatColor.YELLOW;
                    } else {
                        color = ChatColor.GREEN;
                    }
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(color + "Rush Meter: " + String.format("%.2f", bonus) + "/10"));
                }
                queue.tick();
                if (eventActive()) {
                    event.tick();
                    if (event.getTicksRemaining() <= 0) {
                        endEvent();
                    }
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);
    }

    public void activateEvent(ModifierEvent event) {
        this.event = event;
        this.event.activate();
    }

    public void endEvent() {
        event.end();
        event = null;
    }

    public void spawnTextDisplays() {
        Location loc = getReferencePoint().add(fieldDirection.clone().multiply(-1.5)
                .add(incomingDirection.clone().multiply(-3)));
        wallDisplay = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        wallDisplay.setText(ChatColor.GOLD + "Perfect Walls: " + scorer.getWallsCleared());
        wallDisplay.setBillboard(Display.Billboard.CENTER);
        wallDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(1.5f, 1.5f, 1.5f),
                new AxisAngle4f(0, 0, 0, 1)));

        loc = getReferencePoint().add(fieldDirection.clone().multiply(7.5)
                .add(incomingDirection.clone().multiply(-3)));
        scoreDisplay = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        scoreDisplay.setText(ChatColor.GREEN + "Score: " + scorer.getScore());
        scoreDisplay.setBillboard(Display.Billboard.CENTER);
        scoreDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(1.5f, 1.5f, 1.5f),
                new AxisAngle4f(0, 0, 0, 1)));

        textDisplays.add(wallDisplay);
        textDisplays.add(scoreDisplay);
    }

    public Material getDefaultBorderMaterial() {
        return defaultBorderMaterial;
    }

    public PlayingFieldScorer getScorer() {
        return scorer;
    }

    public ModifierEvent getEvent() {
        return event;
    }

    public boolean eventActive() {
        return event != null;
    }

    public void overrideScoreDisplay(int ticks, String message) {
        scoreDisplayOverride = true;
        scoreDisplay.setText(message);
        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> scoreDisplayOverride = false, ticks);
    }

    public WorldBoundingBox getBoundingBox() {
        return boundingBox;
    }

    public boolean hasStarted() {
        return task != null;
    }
}
