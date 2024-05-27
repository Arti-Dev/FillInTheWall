package com.articreep.holeinthewall;

import com.articreep.holeinthewall.display.DisplayType;
import com.articreep.holeinthewall.environments.TheVoid;
import com.articreep.holeinthewall.menu.Gamemode;
import com.articreep.holeinthewall.menu.Menu;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class PlayingField implements Listener {
    Set<Player> players = new HashSet<>();
    /**
     * Must be the bottom left corner of the playing field (NOT including the border blocks)
     */
    private final Location fieldReferencePoint;
    private final Vector fieldDirection; // parallel to the field, positive x direction
    private final Vector incomingDirection; // normal to the field
    private final int height;
    private final int length;
    /**
     * Amount of ticks to show wall results after clearing a wall
     */
    private final int clearDelay = 10;

    private final List<Block> borderBlocks = new ArrayList<>();
    private final Material defaultBorderMaterial = Material.GRAY_CONCRETE;
    private final WorldBoundingBox boundingBox;
    private final WorldBoundingBox effectBox;
    private String environment;

    private final int displaySlotsLength = 6;
    private final DisplayType[] displaySlots = new DisplayType[displaySlotsLength];
    private final TextDisplay[] textDisplays = new TextDisplay[displaySlotsLength];
    private boolean scoreDisplayOverride = false;

    private final PlayingFieldScorer scorer;
    private ModifierEvent event = null;
    private WallQueue queue = null;
    private BukkitTask task = null;
    private Menu menu = null;

    // Whether to tick the scorer or let another class handle it (e.g. multiplayer)
    private boolean tickScorer = true;

    public PlayingField(Location referencePoint, Vector direction, Vector incomingDirection, WorldBoundingBox boundingBox,
                        WorldBoundingBox effectBox, String environment, int length, int height, boolean hideBottomBorder) {
        // define playing field in a very scuffed way
        this.fieldReferencePoint = referencePoint;
        this.fieldDirection = direction;
        this.incomingDirection = incomingDirection;
        this.scorer = new PlayingFieldScorer(this);
        this.boundingBox = boundingBox;
        this.effectBox = effectBox;
        this.height = height;
        this.length = length;
        this.environment = environment;
        if (this.environment == null) this.environment = "";
        this.queue = new WallQueue(this);
        queue.setHideBottomBorder(hideBottomBorder);
        setDefaultDisplaySlots();

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
//        // starter wall
//        Wall wall1 = new Wall(length, height);
//        wall1.insertHoles(new Pair<>(3, 1), new Pair<>(4, 1));
//        queue.addWall(wall1);
    }

    public void start(Gamemode mode) {
        // Silently fail if this is already running
        if (hasStarted()) {
            Bukkit.getLogger().severe("Tried to start game that's already been started");
            return;
        }
        if (players.isEmpty()) {
            throw new IllegalStateException("There are no players!");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Gamemode cannot be null");
        }
        // Pass gamemode to scorer
        scorer.setGamemode(mode);
        removeMenu();
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
        if (event != null) {
            event.end();
            event = null;
        }
        // todo this does not work if player walks off
        scorer.announceFinalScore();
        scorer.reset();

        clearField();
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

    public Set<Player> getPlayers() {
        return players;
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
                for (Player player : players) player.sendTitle(
                        state.titleColor + state.title, state.titleColor + "+" + state.score + " points", 0, 10, 5);
            }
            changeBorderBlocks(state.borderMaterial);
            if (environment.equalsIgnoreCase("VOID")) {
                TheVoid.judgementEffect(this, state.judgement);
            }
        } else {
            event.score(wall);
        }

        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(this);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(this);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(this);

        // Visually display what blocks were correct and what were wrong
        int pauseTime = this.clearDelay;
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
            int ticks = 0;
            // todo this is exclusively for the void environment, but I haven't decided on a good way on doing this
            int beats = 0;
            @Override
            public void run() {
                updateTextDisplays();

                if (!eventActive() || event.actionBarOverride() == null) {
                    double bonus = scorer.getMeter();
                    int bonusMax = scorer.getMeterMax();
                    // todo change this so it can work with leveling instead of just rush
                    ChatColor color;
                    if (bonus <= 3) {
                        color = ChatColor.GRAY;
                    } else if (bonus <= 7) {
                        color = ChatColor.YELLOW;
                    } else {
                        color = ChatColor.GREEN;
                    }
                    for (Player player : players) player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(color + "Meter: " + String.format("%.2f", bonus) + "/" + bonusMax));
                }
                queue.tick();
                if (eventActive()) {
                    event.tick();
                    if (event.getTicksRemaining() <= 0) {
                        endEvent();
                    }
                }
                if (tickScorer) scorer.tick();

                // todo Effects, start them at a slower pace and intensify them as the game goes on
                // Do not do effects if an event is active
                if (environment.equalsIgnoreCase("VOID") && !eventActive()) {
                    if (ticks % 10 == 0) {
                        beats++;
                        if (beats <= 16) {
                            if (beats % 2 == 0) TheVoid.randomShape(PlayingField.this);
                        } else if (beats <= 32) {
                            if (beats % 2 == 0) TheVoid.randomPetal(PlayingField.this);
//                        } else if (beats <= 48) {
//                            TheVoid.randomFallingBlockDisplay(PlayingField.this);
                        } else {
                            beats = 0;
                        }
                    }
                }
                ticks++;

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

    public void setDefaultDisplaySlots() {
        displaySlots[0] = DisplayType.TIME;
        displaySlots[1] = DisplayType.PERFECT_WALLS;
        displaySlots[2] = DisplayType.LEVEL;
        displaySlots[3] = DisplayType.SCORE;
        displaySlots[4] = DisplayType.NAME;
        displaySlots[5] = DisplayType.GAMEMODE;
    }

    public void spawnTextDisplays() {
        Location slot0 = getReferencePoint().subtract(fieldDirection.clone().multiply(1.5))
                .subtract(incomingDirection.clone().multiply(3))
                .add(new Vector(0, 1.5, 0));
        Location slot1 = slot0.clone().subtract(new Vector(0, 1, 0));
        Location slot2 = slot0.clone().add(fieldDirection.clone().multiply(length + 1.5*2));
        Location slot3 = slot2.clone().subtract(new Vector(0, 1, 0));
        Location slot4 = getReferencePoint().add(getFieldDirection().multiply((double) length / 2))
                .add(new Vector(0, 1, 0).multiply(height + 3));
        Location slot5 = slot4.clone().subtract(new Vector(0, 1, 0).multiply(0.5));

        for (int i = 0; i < displaySlotsLength; i++) {
            float size = switch (i) {
                case 4 -> 3f;
                case 5 -> 1f;
                default -> 1.5f;
            };

            Location loc = switch (i) {
                case 0 -> slot0;
                case 1 -> slot1;
                case 2 -> slot2;
                case 3 -> slot3;
                case 4 -> slot4;
                case 5 -> slot5;
                default -> throw new IllegalStateException("Unexpected value: " + i);
            };

            textDisplays[i] = (TextDisplay) slot1.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
            textDisplays[i].setBillboard(Display.Billboard.CENTER);
            textDisplays[i].setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(size, size, size),
                    new AxisAngle4f(0, 0, 0, 1)));
            textDisplays[i].setText(ChatColor.DARK_GRAY + "Loading...");
        }
    }

    public void updateTextDisplays() {
        // todo in theory we don't need to tick the gamemode/name displays, but we can for now
        for (int i = 0; i < displaySlotsLength; i++) {
            Object data;
            DisplayType type = displaySlots[i];
            if (type == DisplayType.SCORE && scoreDisplayOverride) continue;
            data = switch (type) {
                case SCORE -> scorer.getScore();
                case ACCURACY -> "null";
                case SPEED -> "null";
                case PERFECT_WALLS -> scorer.getWallsCleared();
                case TIME -> scorer.getFormattedTime();
                case LEVEL -> scorer.getLevel();
                case NAME -> players.iterator().next().getName();
                case GAMEMODE -> scorer.getGamemode().getTitle();
            };
            textDisplays[i].setText(type.getFormattedText(data));
        }
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
        for (int i = 0; i < displaySlotsLength; i++) {
            if (displaySlots[i] == DisplayType.SCORE) {
                textDisplays[i].setText(message);
            }
        }
        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> scoreDisplayOverride = false, ticks);
    }

    public WorldBoundingBox getBoundingBox() {
        return boundingBox;
    }

    public boolean hasStarted() {
        return task != null;
    }

    public int getLength() {
        return length;
    }

    public int getHeight() {
        return height;
    }

    public WorldBoundingBox getEffectBox() {
        return effectBox;
    }

    public int getClearDelay() {
        return clearDelay;
    }

    public String getEnvironment() {
        return environment;
    }

    public void createMenu() {
        if (players.isEmpty()) return;
        Player player = players.iterator().next();
        if (hasMenu()) removeMenu();
        menu = new Menu(player, getCenter(), this);
        menu.display();
    }

    public void removeMenu() {
        if (menu != null) this.menu.despawn();
        this.menu = null;
    }

    public boolean hasMenu() {
        return menu != null;
    }

    public Location getCenter() {
        Location loc = getReferencePoint().add(fieldDirection.clone().multiply((double) length / 2))
                .add(new Vector(0, 1, 0).multiply((double) height / 2));
        return loc;
    }

    public void doTickScorer(boolean tickScorer) {
        this.tickScorer = tickScorer;
    }
}
