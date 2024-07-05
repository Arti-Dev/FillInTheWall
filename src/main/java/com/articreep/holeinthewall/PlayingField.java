package com.articreep.holeinthewall;

import com.articreep.holeinthewall.display.DisplayType;
import com.articreep.holeinthewall.environments.TheVoid;
import com.articreep.holeinthewall.menu.EndScreen;
import com.articreep.holeinthewall.menu.Menu;
import com.articreep.holeinthewall.modifiers.ModifierEvent;
import com.articreep.holeinthewall.modifiers.Rush;
import com.articreep.holeinthewall.multiplayer.WallGenerator;
import com.articreep.holeinthewall.utils.Utils;
import com.articreep.holeinthewall.utils.WorldBoundingBox;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class PlayingField implements Listener {
    private final Set<Player> players = new HashSet<>();
    private final HashMap<Player, GameMode> previousGamemodes = new HashMap<>();
    /**
     * Must be the bottom left corner of the playing field (NOT including the border blocks)
     * The location is situated in the CENTER of the target block when it is set by the constructor.
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
    private final Material playerMaterial;
    public static final NamespacedKey meterKey = new NamespacedKey(HoleInTheWall.getInstance(), "METER_ITEM");
    private final WorldBoundingBox boundingBox;
    private final WorldBoundingBox effectBox;
    private String environment;

    private final int displaySlotsLength = 6;
    private final DisplayType[] displaySlots = new DisplayType[displaySlotsLength];
    private final TextDisplay[] textDisplays = new TextDisplay[displaySlotsLength];
    private boolean scoreDisplayOverride = false;

    private PlayingFieldScorer scorer;
    private ModifierEvent event = null;

    private WallQueue queue;
    private Material wallMaterial;
    private boolean hideBottomBorder;

    private BukkitTask countdown = null;
    private BukkitTask task = null;
    private Menu menu = null;
    private EndScreen endScreen = null;
    private boolean confirmOnCooldown = false;

    // Multiplayer settings
    /** Whether to tick the scorer or let another class handle it (e.g. multiplayer) */
    private boolean tickScorer = true;
    /** Whether to prevent new players from joining and current players from leaving, AND prevent players from starting their own games
     * Used for multiplayer games */
    private boolean locked = false;

    public PlayingField(Location referencePoint, Vector direction, Vector incomingDirection, WorldBoundingBox boundingBox,
                        WorldBoundingBox effectBox, String environment, int length, int height,
                        Material wallMaterial, Material playerMaterial, boolean hideBottomBorder) {
        // define playing field in a very scuffed way
        this.fieldReferencePoint = Utils.centralizeLocation(referencePoint);
        this.fieldDirection = direction;
        this.incomingDirection = incomingDirection;
        this.scorer = new PlayingFieldScorer(this);
        this.boundingBox = boundingBox;
        this.effectBox = effectBox;
        this.playerMaterial = playerMaterial;
        this.height = height;
        this.length = length;
        this.environment = environment;
        if (this.environment == null) this.environment = "";
        this.hideBottomBorder = hideBottomBorder;
        this.wallMaterial = wallMaterial;
        setDefaultDisplaySlots();

        // Track border blocks
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
    }

    public void createMenu() {
        if (players.isEmpty()) return;
        if (locked) return;
        if (hasMenu()) removeMenu();
        if (hasEndScreen()) removeEndScreen();
        menu = new Menu(getCenter(), this);
        menu.display();
    }

    public void countdownStart(Gamemode mode) {
        countdown = new BukkitRunnable() {
            int i = 3;
            @Override
            public void run() {
                if (i == 3) {
                    sendTitleToPlayers(ChatColor.GREEN + "③", "", 0, 20, 0);
                    playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1);
                } else if (i == 2) {
                    sendTitleToPlayers(ChatColor.YELLOW + "②", "", 0, 20, 0);
                    playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1);
                } else if (i == 1) {
                    sendTitleToPlayers(ChatColor.RED + "①", "", 0, 20, 0);
                    playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_BELL, 1);
                } else if (i == 0) {
                    // It's important that the countdown reference is removed before we start the game
                    // so that hasStarted() returns false
                    countdown = null;
                    start(mode);
                    sendTitleToPlayers(ChatColor.GREEN + "GO!", "", 0, 5, 3);
                    playSoundToPlayers(Sound.BLOCK_BELL_USE, 0.5f);
                    cancel();
                }
                i--;
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 10);
    }

    // todo geneator argument is a hotfix
    public void start(Gamemode mode, WallGenerator generator) {
        // Log fail if this is already running
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
        HoleInTheWall.getInstance().getServer().getPluginManager().registerEvents(this, HoleInTheWall.getInstance());
        // Pass gamemode to a brand new scorer object
        scorer = new PlayingFieldScorer(this);
        queue = new WallQueue(this, wallMaterial, hideBottomBorder);
        if (generator != null) queue.setGenerator(generator);
        scorer.setGamemode(mode);
        setDisplaySlotsFromGamemode(mode);
        removeMenu();
        removeEndScreen();
        spawnTextDisplays();
        for (Player player : players) {
            formatInventory(player);
            setCreative(player);
        }
        task = tickLoop();
    }

    public void start(Gamemode mode) {
        start(mode, new WallGenerator(getLength(), getHeight(), 2, 4, 160));
    }

    public boolean addPlayer(Player player) {
        if (locked) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        players.add(player);
        if (!hasStarted() && !hasMenu()) {
            // Display a new menu
            createMenu();
        } else if (hasStarted()) {
            formatInventory(player);
            setCreative(player);
            if (scorer.getScoreboard() != null) player.setScoreboard(scorer.getScoreboard());
        }

        // Register with the playing field manager
        PlayingFieldManager.activePlayingFields.put(player, this);

        return true;
    }

    public int playerCount() {
        return players.size();
    }

    /** Returns true if the player was removed, false if unable to (locked to field) */
    public boolean removePlayer(Player player) {
        if (locked) return false;

        // If this will be our last player, shut the game down
        if (playerCount() == 1) {
            if (hasStarted()) {
                stop();
            }
            else removeMenu();
        }

        players.remove(player);
        // do not recover the player's gamemode if in spectator
        if (previousGamemodes.containsKey(player) && player.getGameMode() != GameMode.SPECTATOR) {
            GameMode previousGamemode = previousGamemodes.get(player);
            if (previousGamemode != null) player.setGameMode(previousGamemode);
        }
        previousGamemodes.remove(player);

        // Remove from playing field manager
        PlayingFieldManager.activePlayingFields.remove(player);

        return true;
    }

    public void setCreative(Player player) {
        previousGamemodes.put(player, player.getGameMode());
        player.setGameMode(GameMode.CREATIVE);
    }

    public void formatInventory(Player player) {
        // todo could save the player's inventory and restore after, but might not be necessary
        // since this is a minigame
        player.getInventory().clear();
        player.getInventory().setItem(0, buildingItem(playerMaterial));
        player.getInventory().setItem(1, supportItem());
        player.getInventory().setItem(2, meterItem());
        // todo temporary
        if (scorer.getGamemode().hasAttribute(GamemodeAttribute.DO_CLEARING_MODES)) {
            player.getInventory().setItem(3, new ItemStack(Material.FIREWORK_STAR));
        }

    }

    public void stop(boolean submitFinalWall) {
        if (!hasStarted()) {
            Bukkit.getLogger().severe("Tried to stop game that's already been stopped");
            return;
        }

        if (submitFinalWall) queue.instantSend();

        task.cancel();
        task = null;
        for (TextDisplay display : textDisplays) {
            display.remove();
        }
        locked = false;
        queue.clearAllWalls();
        queue.allowMultipleWalls(false);
        if (event != null) {
            event.end();
            event = null;
        }
        scorer.removeScoreboard();
        scorer.announceFinalScore();
        endScreen = scorer.createEndScreen();
        endScreen.display();

        HandlerList.unregisterAll(this);
    }

    public void stop() {
        stop(true);
    }

    // Listeners
    @EventHandler
    public void onLeverFlick(PlayerInteractEvent event) {
        if (!players.contains(event.getPlayer())) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                event.getClickedBlock().getType() == Material.LEVER) {
            queue.instantSend();
        }
    }

    @EventHandler
    public void onSwitchToOffhand(PlayerSwapHandItemsEvent event) {
        if (!players.contains(event.getPlayer())) return;
        event.setCancelled(true);

        if (confirmOnCooldown) return;
        queue.instantSend();
        PlayerInventory inventory = event.getPlayer().getInventory();
        ItemStack mainHandItem = inventory.getItemInMainHand();
        inventory.setItemInMainHand(confirmItem());
        // todo in theory a player could switch around the confirm item's location before the original item is given back to them.
        // should fix, but for now.. too bad!
        int heldSlot = inventory.getHeldItemSlot();
        confirmOnCooldown = true;
        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), () -> {
            inventory.setItem(heldSlot, mainHandItem);
            confirmOnCooldown = false;
        }, 10);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!players.contains(event.getPlayer())) return;

        Player player = event.getPlayer();
        if (!isInField(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Can't place blocks here!");
            return;
        }

        if (event.getBlockPlaced().getType() == Material.CRACKED_STONE_BRICKS) {
            Random random = new Random();
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.7f, random.nextFloat(0.5f, 2));
        }

        scorer.increaseBlocksPlaced();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!players.contains(event.getPlayer())) return;
        Player player = event.getPlayer();
        if (!isInField(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Can't break blocks here!");
        }
    }

    @EventHandler
    public void onCrackedStoneClick(PlayerInteractEvent event) {
        if (!players.contains(event.getPlayer())) return;
        Player player = event.getPlayer();
        // Player must click with an item in hand
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getClickedBlock().getType() == Material.CRACKED_STONE_BRICKS) {
                // Check to make sure the block placement wasn't an accident
                Location newBlock = event.getClickedBlock().getLocation().add(event.getBlockFace().getDirection());
                if (isInField(newBlock)) {
                    // Despawn the cracked stone bricks
                    player.getWorld().spawnParticle(Particle.BLOCK,
                            event.getClickedBlock().getLocation(),
                            10, 0.5, 0.5, 0.5, 0.1,
                            Material.CRACKED_STONE_BRICKS.createBlockData());
                    Bukkit.getScheduler().runTask(HoleInTheWall.getInstance(),
                            () -> event.getClickedBlock().breakNaturally(new ItemStack(Material.LEAD)));
                    player.playSound(player.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 0.7f, 1);
                }
            }
        }

        if (item.getType() == Material.FIREWORK_ROCKET) {
            // Action must be a click
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    || event.getAction() == Action.LEFT_CLICK_BLOCK
                    || event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_AIR) {
                event.setCancelled(true);
                scorer.onMeterActivate(player);
            }
        }

        if (item.getType() == Material.FIREWORK_STAR) {
            // Action must be a click
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    || event.getAction() == Action.LEFT_CLICK_BLOCK
                    || event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_AIR) {
                event.setCancelled(true);
                scorer.onClearingModeChange(player);
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
            Judgement judgement = scorer.scoreWall(wall, this);
            changeBorderBlocks(judgement.getBorder());
            if (environment.equalsIgnoreCase("VOID")) {
                TheVoid.judgementEffect(this, judgement);
            }
        } else {
            event.score(wall);
        }

        // An event may want to do something other than override scoring
        if (eventActive()) {
            event.onWallScore(wall);
        }

        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(this);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(this);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(this);

        // Visually display what blocks were correct and what were wrong
        int pauseTime = this.clearDelay;
        if (eventActive()) pauseTime = event.clearDelay;
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
                        coordinatesToBlock(hole).setType(playerMaterial);
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

    public boolean isInField(Location location) {
        // decentralize the reference point for this
        Location bottomLeft = getReferencePoint().subtract(0.5, 0.5, 0.5);
        Location topRight = bottomLeft.clone()
                .add(fieldDirection.clone().multiply(length-0.5))
                .add(new Vector(0, height-0.5, 0));
        // haha copilot go brr
        return (Utils.withinBounds(bottomLeft.getX(), topRight.getX(), location.getX()) &&
                Utils.withinBounds(bottomLeft.getY(), topRight.getY(), location.getY()) &&
                Utils.withinBounds(bottomLeft.getZ(), topRight.getZ(), location.getZ()));
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

    // Events, ticking, and effects

    public BukkitTask tickLoop() {
        return new BukkitRunnable() {
            int ticks = 0;
            int beats = 0;
            int beatLength = 64;
            int measureLength = 8;
            boolean speedingUp = true;
            @Override
            public void run() {
                updateTextDisplays();

                if (eventActive() && event.actionBarOverride() != null) {
                    sendActionBarToPlayers(new TextComponent(event.actionBarOverride()));
                } else {
                    sendActionBarToPlayers(new TextComponent(scorer.getFormattedMeter()));
                }
                queue.tick();
                if (eventActive()) {
                    event.tick();
                    if (event.getTicksRemaining() <= 0) {
                        endEvent();
                    }
                }
                if (tickScorer) scorer.tick();

                // Effects, start them at a slower pace and intensify them as the game goes on
                // Do not do effects if an event is active
                if (ticks % beatLength == 0 && !eventActive()) {
                    beats++;

                    // Passive effect particles for the void environment
                    /*

                     */

                    if (environment.equalsIgnoreCase("VOID")) {
                        if (beats < measureLength/2) {
                            TheVoid.randomShape(PlayingField.this);
                        } else if (beats <= measureLength) {
                            TheVoid.randomPetal(PlayingField.this);
                        }
                        if (beats >= measureLength) {
                            if (speedingUp) {
                                beatLength /= 2;
                                measureLength *= 2;
                            } else {
                                beatLength *= 2;
                                measureLength /= 2;
                            }
                            beats = 0;

                            if (beatLength <= 8) {
                                speedingUp = false;
                            } else if (beatLength >= 64) {
                                speedingUp = true;
                            }
                        }
//                        if (beats <= 16) {
//                            if (beats % 2 == 0) TheVoid.randomShape(PlayingField.this);
//                        } else if (beats <= 32) {
//                            if (beats % 2 == 0) TheVoid.randomPetal(PlayingField.this);
//                        } else {
//                            beats = 0;
//                        }
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

    public void setDisplaySlotsFromGamemode(Gamemode mode) {
        displaySlots[0] = (DisplayType) mode.getAttribute(GamemodeAttribute.DISPLAY_SLOT_0);
        displaySlots[1] = (DisplayType) mode.getAttribute(GamemodeAttribute.DISPLAY_SLOT_1);
        displaySlots[2] = (DisplayType) mode.getAttribute(GamemodeAttribute.DISPLAY_SLOT_2);
        displaySlots[3] = (DisplayType) mode.getAttribute(GamemodeAttribute.DISPLAY_SLOT_3);
    }

    public void spawnTextDisplays() {
        Location slot0 = getReferencePoint().subtract(fieldDirection.clone().multiply(1.5))
                .subtract(incomingDirection.clone().multiply(3))
                .add(new Vector(0, 1.5, 0));
        Location slot1 = slot0.clone().subtract(new Vector(0, 1, 0));
        Location slot2 = slot0.clone().add(fieldDirection.clone().multiply(length + 2));
        Location slot3 = slot2.clone().subtract(new Vector(0, 1, 0));
        Location slot4 = getCenter()
                .add(new Vector(0, 1, 0).multiply(height/2 + 3));
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
            // todo we definitely need to refactor this, especially the position display
            Object data;
            DisplayType type = displaySlots[i];
            if (type == DisplayType.SCORE && scoreDisplayOverride) continue;
            data = switch (type) {
                case NONE -> "";
                case SCORE -> scorer.getScore();
                case ACCURACY -> "null";
                case SPEED -> scorer.getFormattedBlocksPerSecond();
                case PERFECT_WALLS -> scorer.getPerfectWallsCleared();
                case TIME -> scorer.getFormattedTime();
                case LEVEL -> scorer.getLevel();
                // todo this is some ugly ternary crap, but it does kind of make sense
                case POSITION -> new Object[]{(scorer.getPosition() > 0 ? scorer.getPosition() : "None"),
                        (scorer.getPointsBehind() == -1 ? "Way to go!" : scorer.getPointsBehind() + " behind #" + (scorer.getPosition()-1))};
                case NAME -> players.iterator().next().getName();
                case GAMEMODE -> scorer.getGamemode().getTitle();
            };
            if (data instanceof Object[]) {
                textDisplays[i].setText(type.getFormattedText((Object[]) data));
            } else {
                textDisplays[i].setText(type.getFormattedText(data));
            }
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
        return task != null || countdown != null;
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

    public void removeMenu() {
        if (menu != null) this.menu.despawn();
        this.menu = null;
    }

    public boolean hasMenu() {
        return menu != null;
    }

    public void removeEndScreen() {
        if (endScreen != null) this.endScreen.despawn();
        this.endScreen = null;
    }

    public boolean hasEndScreen() {
        return endScreen != null;
    }

    /**
     * Gets the location in the center (height and length) of this playing field.
     * @return The center location
     */
    public Location getCenter() {
        return getReferencePoint()
                // There's a -1 on the length because the reference point is in the center of the target block.
                .add(fieldDirection.clone().multiply((double) (length - 1) / 2))
                .add(new Vector(0, 1, 0).multiply((double) (height - 1) / 2));
    }

    public void doTickScorer(boolean tickScorer) {
        this.tickScorer = tickScorer;
    }

    public static ItemStack confirmItem() {
        ItemStack item = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRM");
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack buildingItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Fill the holes in the incoming wall with this!"));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack supportItem() {
        ItemStack item = new ItemStack(Material.CRACKED_STONE_BRICKS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Support Block");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "- place this block on the field",
                ChatColor.GRAY + "- place another block against this block",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "- ???",
                ChatColor.AQUA + "- floating block"));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack meterItem() {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Special Ability");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "When your meter is full enough, hold this item",
                ChatColor.GRAY + "and click to activate a" + ChatColor.GOLD + " special ability" + ChatColor.GRAY + "!"));
        meta.getPersistentDataContainer().set(meterKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public void sendMessageToPlayers(String message) {
        for (Player player : players) {
            player.sendMessage(message);
        }
    }

    public void sendTitleToPlayers(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (Player player : players) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    public void sendActionBarToPlayers(TextComponent component) {
        for (Player player : players) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, component);
        }
    }

    public void playSoundToPlayers(Sound sound, float pitch) {
        for (Player player : players) {
            player.playSound(player.getLocation(), sound, 1, pitch);
        }
    }

    public void playSoundToPlayers(Sound sound, float volume, float pitch) {
        for (Player player : players) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isLocked() {
        return locked;
    }

    public World getWorld() {
        return getReferencePoint().getWorld();
    }

    public Material getPlayerMaterial() {
        return playerMaterial;
    }

    public void flashScore() {
        overrideScoreDisplay(40, "");
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= 8) cancel();
                String text = DisplayType.SCORE.getFormattedText(scorer.getScore());
                if (i % 2 == 0) text = ChatColor.WHITE + "" + ChatColor.BOLD + ChatColor.stripColor(text);

                for (int i = 0; i < displaySlotsLength; i++) {
                    if (displaySlots[i] == DisplayType.SCORE) {
                        textDisplays[i].setText(text);
                    }
                }
                i++;
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 5);

    }

    public Material getWallMaterial() {
        return wallMaterial;
    }
}
