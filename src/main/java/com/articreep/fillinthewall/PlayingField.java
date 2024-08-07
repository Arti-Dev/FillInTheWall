package com.articreep.fillinthewall;

import com.articreep.fillinthewall.display.DisplayType;
import com.articreep.fillinthewall.environments.Finals;
import com.articreep.fillinthewall.environments.TheVoid;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.gamemode.GamemodeSettings;
import com.articreep.fillinthewall.menu.EndScreen;
import com.articreep.fillinthewall.menu.Menu;
import com.articreep.fillinthewall.modifiers.ModifierEvent;
import com.articreep.fillinthewall.modifiers.PlayerInTheWall;
import com.articreep.fillinthewall.modifiers.Rush;
import com.articreep.fillinthewall.multiplayer.WallGenerator;
import com.articreep.fillinthewall.utils.Utils;
import com.articreep.fillinthewall.utils.WorldBoundingBox;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.javatuples.Pair;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.sql.SQLException;
import java.util.*;

public class PlayingField implements Listener {
    private final Set<Player> players = new HashSet<>();
    private final HashMap<Player, GameMode> previousGamemodes = new HashMap<>();
    private final HashMap<Player, Double> previousBlockReach = new HashMap<>();
    /**
     * Must be the bottom left corner of the playing field (NOT including the border blocks)
     * The location is situated in the CENTER of the target block when it is set by the constructor.
     */
    private final Location fieldReferencePoint;
    private final Vector fieldDirection; // parallel to the field, positive x direction
    private final Vector incomingDirection; // normal to the field
    private final int height;
    private final int length;
    private final int standingDistance;
    /**
     * Amount of ticks to show wall results after clearing a wall
     */
    private final int clearDelay = 10;
    private boolean clearDelayActive = false;

    private final List<Block> borderBlocks = new ArrayList<>();
    private final Material defaultBorderMaterial = Material.GRAY_CONCRETE;
    private final Material playerMaterial;
    public static final NamespacedKey meterKey = new NamespacedKey(FillInTheWall.getInstance(), "METER_ITEM");
    public static final NamespacedKey variableKey = new NamespacedKey(FillInTheWall.getInstance(), "VARIABLE_ITEM");
    private final WorldBoundingBox boundingBox;
    private final WorldBoundingBox effectBox;
    private String environment;

    private final int displaySlotsLength = 6;
    private final DisplayType[] displaySlots = new DisplayType[displaySlotsLength];
    private final TextDisplay[] textDisplays = new TextDisplay[displaySlotsLength];
    private boolean scoreDisplayOverride = false;

    // Tip statistics
    private int ticksSinceFlying = 0;
    private boolean hasFlownBefore = false;
    private int ticksSinceOffhandSubmit = 0;
    private boolean hasSubmittedUsingOffhand = false;
    private Set<TextDisplay> tipDisplays = new HashSet<>();

    private PlayingFieldScorer scorer;
    private ModifierEvent event = null;

    private WallQueue queue;
    private Material wallMaterial;
    private boolean hideBottomBorder;

    /** The scorer and queue should be reset before each game starts. */
    private boolean resetRecently = false;

    private BukkitTask countdown = null;
    private BukkitTask task = null;
    private Menu menu = null;
    private EndScreen endScreen = null;
    private boolean confirmOnCooldown = false;

    private final HashMap<Block, BlockDisplay> incorrectBlockHighlights = new HashMap<>();

    private Sound currentlyPlayingTrack = null;

    public static final String DEFAULT_HOTBAR = "PVCSM____";

    // Multiplayer settings
    /** Whether to prevent new players from joining and current players from leaving, AND prevent players from starting their own games
     * Used for multiplayer games */
    private boolean multiplayerMode = false;

    public PlayingField(Location referencePoint, Vector direction, Vector incomingDirection, int standingDistance,
                        WorldBoundingBox boundingBox, WorldBoundingBox effectBox, String environment, int length, int height,
                        Material wallMaterial, Material playerMaterial, boolean hideBottomBorder) {
        // define playing field in a very scuffed way
        this.fieldReferencePoint = Utils.centralizeLocation(referencePoint);
        this.fieldDirection = direction;
        this.incomingDirection = incomingDirection;
        if (!fieldDirection.isZero()) fieldDirection.normalize();
        if (!incomingDirection.isZero()) incomingDirection.normalize();
        this.scorer = new PlayingFieldScorer(this);
        this.queue = new WallQueue(this, wallMaterial, WallGenerator.defaultGenerator(length, height), hideBottomBorder);
        this.boundingBox = boundingBox;
        this.effectBox = effectBox;
        this.playerMaterial = playerMaterial;
        this.height = height;
        this.length = length;
        this.environment = environment;
        if (this.environment == null) this.environment = "";
        this.hideBottomBorder = hideBottomBorder;
        this.wallMaterial = wallMaterial;
        this.standingDistance = standingDistance;
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
        if (hasMenu()) removeMenu();
        if (hasEndScreen()) removeEndScreen();
        menu = new Menu(getCenter(true, false).add(0, 1, 0), this);
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
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 10);
    }

    public void reset() {
        scorer = new PlayingFieldScorer(this);
        queue = new WallQueue(this, wallMaterial, WallGenerator.defaultGenerator(length, height), hideBottomBorder);
        ticksSinceOffhandSubmit = 0;
        ticksSinceFlying = 0;
        hasFlownBefore = false;
        hasSubmittedUsingOffhand = false;
        resetRecently = true;
    }

    // Running this method will create new scorer and queue objects
    public void start(Gamemode mode, GamemodeSettings settings) {
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
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        if (!resetRecently) reset();
        scorer.setGamemode(mode, settings);
        setDisplaySlots(settings);
        removeMenu();
        removeEndScreen();
        spawnTextDisplays();
        for (Player player : players) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, -1, 0, false, false));
            formatInventory(player);
            setCreative(player);
            setInfiniteReach(player);
        }
        task = tickLoop();
    }

    public void start(Gamemode gamemode) {
        start(gamemode, gamemode.getDefaultSettings());
    }

    /**
     * Adds a player to the game. If locked is true, locks this playing field after the player is added.
     * @param player Player to add
     * @return
     */
    public boolean addPlayer(Player player, AddReason reason) {
        if (multiplayerMode && reason != AddReason.MULTIPLAYER) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        players.add(player);
        player.setAllowFlight(true);
        if (!hasStarted() && !hasMenu() && !multiplayerMode) {
            // Display a new menu
            createMenu();
        } else if (hasStarted()) {
            formatInventory(player);
            setCreative(player);
            setInfiniteReach(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, -1, 0, false, false));
            if (scorer.getScoreboard() != null) player.setScoreboard(scorer.getScoreboard());
            if (currentlyPlayingTrack != null)  player.playSound(player, currentlyPlayingTrack, 0.5f, 1);
        }

        // Register with the playing field manager
        PlayingFieldManager.activePlayingFields.put(player, this);

        return true;
    }

    public int playerCount() {
        return players.size();
    }

    /** Returns true if the player was removed, false if unable to (locked to field) */
    public boolean removePlayer(Player player, boolean force) {
        if (multiplayerMode && !force) return false;
        player.setAllowFlight(false);
        saveHotbar(player);

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
        resetReach(player);
        if (currentlyPlayingTrack != null) player.stopSound(currentlyPlayingTrack);

        // Remove from playing field manager
        PlayingFieldManager.activePlayingFields.remove(player);

        return true;
    }

    public boolean removePlayer(Player player) {
        return removePlayer(player, false);
    }

    public void setCreative(Player player) {
        previousGamemodes.put(player, player.getGameMode());
        player.setGameMode(GameMode.CREATIVE);
    }

    public void formatInventory(Player player) {
        player.getInventory().clear();
        String hotbar;
        try {
            hotbar = Database.getHotbar(player.getUniqueId());
        } catch (SQLException e) {
            e.printStackTrace();
            hotbar = DEFAULT_HOTBAR;

        }
        for (int i = 0; i < hotbar.length(); i++) {
            char c = hotbar.charAt(i);
            if (c == 'P') {
                player.getInventory().setItem(i, buildingItem(playerMaterial));
            } else if (c == 'V') {
                player.getInventory().setItem(i, variableItem());
            } else if (c == 'C') {
                player.getInventory().setItem(i, copperSupportItem());
            } else if (c == 'S') {
                player.getInventory().setItem(i, stoneSupportItem());
            } else if (c == 'M') {
                player.getInventory().setItem(i, meterItem());
            } else {
                player.getInventory().setItem(i, new ItemStack(Material.AIR));
            }
        }

        // Add any missing items
        if (!hotbar.contains("P")) {
            player.getInventory().addItem(buildingItem(playerMaterial));
        } if (!hotbar.contains("V")) {
            player.getInventory().addItem(variableItem());
        } if (!hotbar.contains("C")) {
            player.getInventory().addItem(copperSupportItem());
        } if (!hotbar.contains("S")) {
            player.getInventory().addItem(stoneSupportItem());
        } if (!hotbar.contains("M")) {
            player.getInventory().addItem(meterItem());
        }
        // todo temporary
        if (scorer.getSettings().getBooleanAttribute(GamemodeAttribute.DO_CLEARING_MODES)) {
            player.getInventory().addItem(new ItemStack(Material.FIREWORK_STAR));
        }

    }

    public void setInfiniteReach(Player player) {
        if (!scorer.getSettings().getBooleanAttribute(GamemodeAttribute.INFINITE_BLOCK_REACH)) return;
        AttributeInstance attribute = player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE);
        previousBlockReach.put(player, attribute.getBaseValue());
        attribute.setBaseValue(64);
    }

    public void resetReach(Player player) {
        if (previousBlockReach.containsKey(player)) {
            player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE).setBaseValue(previousBlockReach.get(player));
            previousBlockReach.remove(player);
        }
    }

    public void stop(boolean submitFinalWall, boolean showEndScreen) {
        if (!hasStarted()) {
            Bukkit.getLogger().severe("Tried to stop game that's already been stopped");
            return;
        }

        if (submitFinalWall) queue.instantSend(true);
        else clearField();

        task.cancel();
        task = null;
        for (TextDisplay display : textDisplays) {
            display.remove();
        }
        if (!tipDisplays.isEmpty()) clearTipDisplays();
        multiplayerMode = false;
        stopMusic();
        queue.clearAllWalls();
        queue.allowMultipleWalls(false);
        for (Player player : getPlayers()) {
            resetReach(player);
        }
        if (event != null) {
            event.end();
            event = null;
        }
        scorer.removeScoreboard();
        scorer.announceFinalScore();
        if (showEndScreen) {
            endScreen = scorer.createEndScreen();
            endScreen.display();
        }

        resetRecently = false;

        HandlerList.unregisterAll(this);
    }

    public void stop() {
        stop(true, true);
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
        hasSubmittedUsingOffhand = true;
        confirmOnCooldown = true;
        int pauseTime = this.clearDelay;
        if (eventActive()) pauseTime = this.event.clearDelay;
        Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
            confirmOnCooldown = false;
        }, pauseTime);
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

        if (scorer.getSettings().getBooleanAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS)) {
            Block block = event.getBlockPlaced();
            if (block.getType() != copperSupportItem().getType()) {
                Pair<Integer, Integer> coordinates = blockToCoordinates(block);
                if (queue.getFrontmostWall() != null && !queue.getFrontmostWall().getHoles().contains(coordinates)) {
                    addIncorrectBlockHighlight(block);
                }
            }
        }

        scorer.increaseBlocksPlaced();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!players.contains(event.getPlayer())) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!isInField(block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Can't break blocks here!");
        }

        if (incorrectBlockHighlights.containsKey(block)) {
            incorrectBlockHighlights.get(block).remove();
            incorrectBlockHighlights.remove(block);
        }
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (!players.contains(event.getPlayer())) return;
        Player player = event.getPlayer();
        // Player must click with an item in hand
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getClickedBlock().getType() == Material.CRACKED_STONE_BRICKS) {
                Block clickedBlock = event.getClickedBlock();
                // Check to make sure the block placement wasn't an accident
                Location newBlock = clickedBlock.getLocation().add(event.getBlockFace().getDirection());
                if (isInField(newBlock)) {
                    // Despawn the cracked stone bricks
                    getWorld().spawnParticle(Particle.BLOCK,
                            clickedBlock.getLocation().add(0.5, 0.5, 0.5),
                            100, 0.5, 0.5, 0.5, 0.1,
                            Material.CRACKED_STONE_BRICKS.createBlockData());
                    Bukkit.getScheduler().runTask(FillInTheWall.getInstance(),
                            () -> clickedBlock.breakNaturally(new ItemStack(Material.LEAD)));
                    if (incorrectBlockHighlights.containsKey(clickedBlock)) {
                        incorrectBlockHighlights.get(clickedBlock).remove();
                        incorrectBlockHighlights.remove(clickedBlock);
                    }
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

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (!players.contains(event.getPlayer())) return;
        Player player = event.getPlayer();
        event.setCancelled(true);
        scorer.onMeterActivate(player);
    }

    public void refreshIncorrectBlockHighlights(Wall wall) {
        for (BlockDisplay display : incorrectBlockHighlights.values()) {
            display.remove();
        }
        incorrectBlockHighlights.clear();
        if (!scorer.getSettings().getBooleanAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS)) return;
        if (clearDelayActive) return;


        for (Pair<Integer, Integer> coordinates : getPlayingFieldBlocks(false).keySet()) {
            if (!wall.getHoles().contains(coordinates) && coordinatesToBlock(coordinates).getType() != copperSupportItem().getType()) {
                addIncorrectBlockHighlight(coordinatesToBlock(coordinates));
            }
        }
    }

    /**
     * Returns all blocks in the playing field excluding air blocks and copper support blocks (which don't count)
     * @return all blocks in coordinate to block map
     */
    public Map<Pair<Integer, Integer>, Block> getPlayingFieldBlocks(boolean includeCopperSupports) {
        HashMap<Pair<Integer, Integer>, Block> blocks = new HashMap<>();
        // y direction loop
        for (int y = 0; y < height; y++) {
            Location loc = getReferencePoint().add(0, y, 0);
            for (int x = 0; x < length; x++) {
                if (!loc.getBlock().isEmpty()) {
                    if (includeCopperSupports || loc.getBlock().getType() != copperSupportItem().getType()) {
                        blocks.put(Pair.with(x, y), loc.getBlock());
                    }
                }
                loc.add(fieldDirection);
            }
        }
        return blocks;
    }

    // todo add the option to get this reference point in the center of the block or in the natural corner
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

    // Generated with GitHub Copilot, then adjusted
    public Pair<Integer, Integer> blockToCoordinates(Block block) {
        Vector relative = block.getLocation().subtract(getReferencePoint().subtract(0.5, 0.5, 0.5)).toVector();
        int x = (int) relative.dot(fieldDirection);
        int y = (int) relative.getY();
        return Pair.with(x, y);
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
        // An event may want to do something other than override scoring
        if (eventActive()) {
            event.onWallScore(wall);
        }

        // Events can override scoring
        if (!eventActive() || !event.overrideCompleteScoring) {
            Judgement judgement = scorer.scoreWall(wall, this);
            changeBorderBlocks(judgement.getBorder());
            if (environment.equalsIgnoreCase("VOID")) {
                TheVoid.judgementEffect(this, judgement);
            } else if (environment.equalsIgnoreCase("FINALS") && judgement == Judgement.PERFECT) {
                Finals.torchGeyser(this);
            }
        } else {
            event.score(wall);
        }

        // Spawn copper break particles - we're not actually breaking them, they're just getting replaced
        for (Block block : getPlayingFieldBlocks(true).values()) {
            if (block.getType() == copperSupportItem().getType()) {
                getWorld().spawnParticle(Particle.BLOCK,
                        block.getLocation().add(0.5, 0.5, 0.5),
                        100, 0.5, 0.5, 0.5, 0.1,
                        copperSupportItem().getType().createBlockData());
            }
        }

        int pauseTime = this.clearDelay;
        if (eventActive()) pauseTime = event.clearDelay;

        clearDelayActive = true;
        if (eventActive() && event.overrideCorrectBlocksVisual) {
            event.correctBlocksVisual(wall);
        } else {
            correctBlocksVisual(wall);
        }

        // Clear the field after the pauseTime
        Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
            if (eventActive() && event.fillFieldAfterSubmission) fillField(playerMaterial);
            else clearField();

            resetBorder();
            clearDelayActive = false;

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

        if (scorer.getSettings().getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP) > 0) {
            if (scorer.getPerfectWallsCleared() >= scorer.getSettings().getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP)) {
                stop(false, true);
            }
        }
    }

    // Block-related methods

    /**
     * Fills the playing field with the given material
     * @param material the material to fill the field with
     */
    public void fillField(Material material) {
        for (int x = 0; x < length; x++) {
            for (int y = 0; y < height; y++) {
                Location loc = getReferencePoint().clone().add(fieldDirection.clone().multiply(x)).add(0, y, 0);
                loc.getBlock().setType(material);
            }
        }
    }

    public void correctBlocksVisual(Wall wall) {
        Map<Pair<Integer, Integer>, Block> extraBlocks = wall.getExtraBlocks(this);
        Map<Pair<Integer, Integer>, Block> correctBlocks = wall.getCorrectBlocks(this);
        Map<Pair<Integer, Integer>, Block> missingBlocks = wall.getMissingBlocks(this);

        // Visually display what blocks were correct and what were wrong
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
        for (BlockDisplay display : incorrectBlockHighlights.values()) {
            display.remove();
        }
        incorrectBlockHighlights.clear();
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

                // Tips
                if (!arePlayersFlying()) ticksSinceFlying++;
                else {
                    ticksSinceFlying = 0;
                    hasFlownBefore = true;
                }

                ticksSinceOffhandSubmit++;

                if (ticksSinceFlying % (20*20) == 0 && !hasFlownBefore) {
                    // one exception
                    if (!eventActive() || !(event instanceof PlayerInTheWall)) {
                        setTipDisplay(ChatColor.GRAY + "Tip: " + ChatColor.YELLOW + "You can fly!");
                    }
                } else if (ticksSinceOffhandSubmit % (30*20) == 0 && !hasSubmittedUsingOffhand) {
                    setTipDisplay(ChatColor.GRAY + "Tip: " + ChatColor.YELLOW + "Submit walls by pressing offhand (usually [F])!");
                }

                if (eventActive() && event.actionBarOverride() != null) {
                    sendActionBarToPlayers(new TextComponent(event.actionBarOverride()));
                } else {
                    sendActionBarToPlayers(scorer.getFormattedMeter());
                }
                queue.tick();
                if (eventActive()) {
                    event.tick();
                    if (event.getTicksRemaining() <= 0) {
                        endEvent();
                    }
                }
                if (!multiplayerMode) scorer.tick();

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
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    public boolean arePlayersFlying() {
        for (Player player : players) {
            if (player.isFlying()) return true;
        }
        return false;
    }

    public void setEvent(ModifierEvent newEvent) {
        if (event != null && newEvent != null) {
            if (newEvent.shelveEvent) {
                Bukkit.getLogger().info("Shelving event " + event);
                newEvent.setShelvedEvent(event);
                event.end();
            } else {
                endEvent();
            }
        }
        this.event = newEvent;
    }

    public void endEvent() {
        event.end();
        if (event.shelveEvent) {
            // todo this line prevents infinite recursion - kind of scary
            event = event.getShelvedEvent();
            if (event != null) {
                event.activate();
            }
        } else {
            event = null;
        }
        if (scorer.getSettings().getIntAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP) > 0) {
            if (scorer.getEventCount() >= (int) scorer.getSettings().getAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP)) {
                stop();
            }
        }
    }

    public void setDefaultDisplaySlots() {
        displaySlots[0] = DisplayType.TIME;
        displaySlots[1] = DisplayType.PERFECT_WALLS;
        displaySlots[2] = DisplayType.LEVEL;
        displaySlots[3] = DisplayType.SCORE;
        displaySlots[4] = DisplayType.NAME;
        displaySlots[5] = DisplayType.GAMEMODE;
    }

    public void setDisplaySlots(GamemodeSettings settings) {
        displaySlots[0] = settings.getDisplayTypeAttribute(GamemodeAttribute.DISPLAY_SLOT_0);
        displaySlots[1] = settings.getDisplayTypeAttribute(GamemodeAttribute.DISPLAY_SLOT_1);
        displaySlots[2] = settings.getDisplayTypeAttribute(GamemodeAttribute.DISPLAY_SLOT_2);
        displaySlots[3] = settings.getDisplayTypeAttribute(GamemodeAttribute.DISPLAY_SLOT_3);
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
            // todo we definitely need to refactor this
            Object data = null;
            DisplayType type = displaySlots[i];
            if (type == DisplayType.SCORE && scoreDisplayOverride) continue;
            switch (type) {
                case NONE -> data = "";
                case SCORE -> data = scorer.getScore();
                case ACCURACY -> data = "null";
                case SPEED -> data = scorer.getFormattedBlocksPerSecond();
                case PERFECT_WALLS -> {
                    ArrayList<Object> array = new ArrayList<>();
                    array.add(scorer.getPerfectWallsCleared());
                    if (scorer.getSettings().getIntAttribute(GamemodeAttribute.PERFECT_WALL_CAP) > 0) {
                        array.add("/" + scorer.getSettings().getAttribute(GamemodeAttribute.PERFECT_WALL_CAP));
                    } else {
                        array.add("");
                    }
                    data = array;
                }
                case TIME -> data = scorer.getFormattedTime();
                case LEVEL -> data = scorer.getLevel();
                case POSITION -> {
                    ArrayList<Object> array = new ArrayList<>();
                    if (scorer.getPointsBehind() == -1) {
                        array.add("Way to go!");
                        array.add("");
                    } else {
                        array.add(scorer.getPointsBehind());
                        array.add("#" + (scorer.getPosition() - 1));
                    }
                    data = array;
                }
                case NAME -> data = Utils.playersToString(players);
                case GAMEMODE -> data = scorer.getGamemode().getTitle();
                case EVENTS -> {
                    ArrayList<Object> array = new ArrayList<>();
                    array.add(scorer.getEventCount());
                    if (scorer.getSettings().getIntAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP) > 0) {
                        array.add("/" + scorer.getSettings().getAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP));
                    } else {
                        array.add("");
                    }
                    data = array;
                }
            }
            if (data instanceof ArrayList<?> list) {
                textDisplays[i].setText(type.getFormattedText(list));
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
        if (event == null) return false;
        return event.isActive();
    }

    public void overrideScoreDisplay(int ticks, String message) {
        scoreDisplayOverride = true;
        for (int i = 0; i < displaySlotsLength; i++) {
            if (displaySlots[i] == DisplayType.SCORE) {
                textDisplays[i].setText(message);
            }
        }
        Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> scoreDisplayOverride = false, ticks);
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
     * Returns a location that is the center of the playing field, either on the horizontal axis (on the bottom), vertical axis (on the left side), or both.
     * @return The center location
     */
    public Location getCenter(boolean alongLength, boolean alongHeight) {
        Location location = getReferencePoint();
        if (alongLength) {
            // There's a -1 on the length because the reference point is in the center of the target block.
            location.add(fieldDirection.clone().multiply((double) (length - 1) / 2));
        }
        if (alongHeight) {
            location.add(new Vector(0, 1, 0).multiply((double) (height - 1) / 2));
        }
        return location;
    }

    public Location getCenter() {
        return getCenter(true, true);
    }

    public static ItemStack buildingItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Fill the holes in the incoming wall with this!"));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack stoneSupportItem() {
        ItemStack item = new ItemStack(Material.CRACKED_STONE_BRICKS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Stone Support Block");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "- place this block on the field",
                ChatColor.GRAY + "- place another block against this block",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "- ???",
                ChatColor.AQUA + "- floating block"));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack copperSupportItem() {
        ItemStack item = new ItemStack(Material.WAXED_COPPER_GRATE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.of(new java.awt.Color(154, 95, 74)) + "Copper Support Block");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "- place this block on the field",
                ChatColor.GRAY + "- block breaks right before wall is submitted",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "- ???",
                ChatColor.AQUA + "- no left clicks required"));
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

    public static ItemStack variableItem() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Variable Item");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "This is replaced with special items during specific events!",
                ChatColor.DARK_GRAY + "Feel free to move this anywhere in your inventory!"));
        meta.getPersistentDataContainer().set(variableKey, PersistentDataType.BOOLEAN, true);
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

    public void sendActionBarToPlayers(BaseComponent component) {
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

    public void setMultiplayerMode(boolean bool) {
        this.multiplayerMode = bool;
    }

    public boolean isLocked() {
        return multiplayerMode;
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
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 5);
    }

    public Material getWallMaterial() {
        return wallMaterial;
    }

    public int getStandingDistance() {
        return standingDistance;
    }

    public enum AddReason {
        MULTIPLAYER, IN_RANGE
    }

    public Location getSpawnLocation() {
        Location spawn = getReferencePoint().subtract(0.5, 0.5, 0.5);
        spawn.add(getFieldDirection()
                .multiply(getLength() / 2.0));
        spawn.add(getIncomingDirection().multiply(getStandingDistance() / 2.0));
        spawn.setDirection(getIncomingDirection().multiply(-1));
        return spawn;
    }

    // todo maybe the display could slide across the floor?
    public void setTipDisplay(String tip) {
        if (!tipDisplays.isEmpty()) clearTipDisplays();
        Location bottomLocation = getCenter(true, false)
                .subtract(0, 0.45, 0)
                .add(getIncomingDirection().multiply(0.5));
        bottomLocation.setDirection(getIncomingDirection());
        bottomLocation.setPitch(-90);

        Location topLocation = bottomLocation.clone()
                .subtract(getIncomingDirection().multiply(1))
                .add(0, height-0.1, 0);
        topLocation.setPitch(90);

        TextDisplay bottomDisplay = (TextDisplay) getWorld().spawnEntity(bottomLocation, EntityType.TEXT_DISPLAY);
        bottomDisplay.setText(tip);
        bottomDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(1.3f, 1.3f, 1.3f),
                new AxisAngle4f(0, 0, 0, 1)));

        TextDisplay topDisplay = (TextDisplay) getWorld().spawnEntity(topLocation, EntityType.TEXT_DISPLAY);
        topDisplay.setText(tip);
        topDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(1.3f, 1.3f, 1.3f),
                new AxisAngle4f(0, 0, 0, 1)));

        tipDisplays.add(bottomDisplay);
        tipDisplays.add(topDisplay);

        Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), this::clearTipDisplays, 20*10);
    }

    public void clearTipDisplays() {
        for (TextDisplay display : tipDisplays) {
            display.remove();
        }
        tipDisplays.clear();
    }

    public void addIncorrectBlockHighlight(Block block) {
        BlockDisplay display = (BlockDisplay) getWorld().spawnEntity(block.getLocation(), EntityType.BLOCK_DISPLAY);
        display.setBlock(playerMaterial.createBlockData());
        display.setGlowing(true);
        display.setGlowColorOverride(Color.RED);
        incorrectBlockHighlights.put(block, display);

        // remove after 10 seconds
        Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), () -> {
            display.remove();
            incorrectBlockHighlights.remove(block);
        }, 20*10);
    }

    public boolean isClearDelayActive() {
        return clearDelayActive;
    }

    public void playMusic(Sound sound) {
        stopMusic();
        currentlyPlayingTrack = sound;
        for (Player player : players) {
            player.playSound(player, currentlyPlayingTrack, 0.5f, 1);
        }
    }

    public void stopMusic() {
        if (currentlyPlayingTrack == null) return;
        for (Player player : players) {
            player.stopSound(currentlyPlayingTrack);
        }
        currentlyPlayingTrack = null;
    }

    public void fireworks() {
        new BukkitRunnable() {
            int i = 0;
            // Players might leave the field immediately so copy them to a new set
            final Set<Player> playersToCelebrate = new HashSet<>(players);
            @Override
            public void run() {
                for (Player player : playersToCelebrate) {
                    Firework firework = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);
                    firework.setMaxLife(20);
                    FireworkMeta meta = firework.getFireworkMeta();
                    FireworkEffect effect = FireworkEffect.builder()
                            .withColor(wallMaterial.createBlockData().getMapColor())
                            .withFade(playerMaterial.createBlockData().getMapColor()).build();
                    meta.addEffect(effect);
                    firework.setFireworkMeta(meta);
                }
                i++;
                if (i >= 4) cancel();
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 20);
    }

    public void saveHotbar(Player player) {
        StringBuilder hotbar = new StringBuilder();
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                hotbar.append("_");
                continue;
            }
            PersistentDataContainer container = null;
            if (item.hasItemMeta()) {
                container = item.getItemMeta().getPersistentDataContainer();
            }

            if (item.getType() == playerMaterial) {
                hotbar.append("P");
            } else if (item.getType() == copperSupportItem().getType()) {
                hotbar.append("C");
            } else if (item.getType() == stoneSupportItem().getType()) {
                hotbar.append("S");
            } else if (container != null && container.has(meterKey, PersistentDataType.BOOLEAN)) {
                hotbar.append("M");
            } else if (container != null && container.has(variableKey, PersistentDataType.BOOLEAN)) {
                hotbar.append("V");
            } else {
                hotbar.append("_");
            }
        }

        Database.updateHotbar(player.getUniqueId(), hotbar.toString());
    }
}
