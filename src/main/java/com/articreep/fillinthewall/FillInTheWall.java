package com.articreep.fillinthewall;

import com.articreep.fillinthewall.environments.Finals;
import com.articreep.fillinthewall.environments.TheVoid;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.modifiers.*;
import com.articreep.fillinthewall.multiplayer.Pregame;
import com.articreep.fillinthewall.multiplayer.SettingsMenu;
import com.articreep.fillinthewall.utils.Utils;
import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import net.md_5.bungee.api.ChatColor;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_21_R3.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public final class FillInTheWall extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static FillInTheWall instance = null;
    private FileConfiguration playingFieldConfig;
    private Set<Entity> displays = new HashSet<>();
    private NamespacedKey interactionKey = new NamespacedKey(this, "singleplayerPortal");
    private Display singleplayerDisplay = null;
    private Display multiplayerDisplay = null;
    private Location multiplayerSpawn = null;
    private Location spectatorFinalsSpawn = null;

    private BukkitTask leaderboardUpdateTask = null;

    private final static MysqlDataSource dataSource = new MysqlConnectionPoolDataSource();

    @Override
    public void onEnable() {
        instance = this;
        RegisterPlayingField registerPlayingField = new RegisterPlayingField();
        SettingsMenu settingsMenu = new SettingsMenu();
        getCommand("fillinthewall").setExecutor(this);
        getCommand("registerplayingfield").setExecutor(registerPlayingField);
        getCommand("settingsmenu").setExecutor(settingsMenu);
        getServer().getPluginManager().registerEvents(new PlayingFieldManager(), this);
        getServer().getPluginManager().registerEvents(new TheVoid(), this);
        getServer().getPluginManager().registerEvents(registerPlayingField, this);
        getServer().getPluginManager().registerEvents(new Finals(), this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(settingsMenu, this);

        loadPlayingFieldConfig();
        saveDefaultConfig();

        if (!loadSQL()) {
            Database.setOfflineMode(true);
        }

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            // todo temporary
            PlayingFieldManager.pregame = new Pregame(Bukkit.getWorld("multi"), Gamemode.MULTIPLAYER_SCORE_ATTACK,
                    2, 30);
            PlayingFieldManager.vsPregame = new Pregame(Bukkit.getWorld("versus"), Gamemode.VERSUS, 2, 15);
            PlayingFieldManager.parseConfig(getPlayingFieldConfig());
            spawnPortals();
            Leaderboards.spawnLeaderboards(getConfig());
            leaderboardUpdateTask = Bukkit.getScheduler().runTaskTimer(this, Leaderboards::updateLeaderboards, 0, 20 * 30);
            multiplayerSpawn = getConfig().getLocation("multiplayer-spawn");
            spectatorFinalsSpawn = getConfig().getLocation("spectator-finals-spawn");
        }, 1);

        Bukkit.getLogger().info(ChatColor.BLUE + "FillInTheWall has been enabled!");

    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        displays.forEach(Entity::remove);
        Leaderboards.removeLeaderboards();

        for (PlayingField field : PlayingFieldManager.playingFieldLocations.values()) {
            if (field.hasStarted()) field.stop(false, false);
            else {
                field.removeMenu();
                field.removeEndScreen();
            }
        }
    }

    private void spawnPortals() {
        Location singleplayerLocation = getConfig().getLocation("singleplayer-portal.location");
        Location multiplayerLocation = getConfig().getLocation("multiplayer-portal.location");
        String singleplayerText = getConfig().getString("singleplayer-portal.text");
        String multiplayerText = getConfig().getString("multiplayer-portal.text");

        if (singleplayerLocation != null) {
            ItemDisplay itemDisplay = (ItemDisplay) singleplayerLocation.getWorld().spawnEntity(
                    singleplayerLocation, EntityType.ITEM_DISPLAY);
            itemDisplay.setItemStack(new ItemStack(Material.GLOW_BERRIES));
            float size = 3.0f;
            itemDisplay.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1), new Vector3f(size, size, size),
                    new AxisAngle4f(0, 0, 0, 1)));
            itemDisplay.setGlowing(true);
            itemDisplay.setBillboard(Display.Billboard.VERTICAL);
            TextDisplay textDisplay = (TextDisplay) singleplayerLocation.getWorld().spawnEntity(
                    singleplayerLocation.clone().add(0, size/2, 0), EntityType.TEXT_DISPLAY);
            textDisplay.setText(singleplayerText);
            textDisplay.setBillboard(Display.Billboard.VERTICAL);
            Interaction interaction = (Interaction) singleplayerLocation.getWorld().spawnEntity(
                    singleplayerLocation.clone().add(0, -size/2, 0), EntityType.INTERACTION);
            interaction.setInteractionHeight(size);
            interaction.setInteractionWidth(size);
            interaction.getPersistentDataContainer().set(interactionKey, PersistentDataType.STRING, "SINGLEPLAYER");
            displays.add(itemDisplay);
            displays.add(textDisplay);
            displays.add(interaction);
            singleplayerDisplay = itemDisplay;
        }
        if (multiplayerLocation != null) {
            ItemDisplay itemDisplay = (ItemDisplay) multiplayerLocation.getWorld().spawnEntity(
                    multiplayerLocation, EntityType.ITEM_DISPLAY);
            itemDisplay.setItemStack(new ItemStack(Material.SOUL_CAMPFIRE));
            float size = 3.0f;
            itemDisplay.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1), new Vector3f(size, size, size),
                    new AxisAngle4f(0, 0, 0, 1)));
            itemDisplay.setGlowing(true);
            itemDisplay.setBillboard(Display.Billboard.VERTICAL);
            TextDisplay textDisplay = (TextDisplay) multiplayerLocation.getWorld().spawnEntity(
                    multiplayerLocation.clone().add(0, size/2, 0), EntityType.TEXT_DISPLAY);
            textDisplay.setText(multiplayerText);
            textDisplay.setBillboard(Display.Billboard.VERTICAL);
            Interaction interaction = (Interaction) multiplayerLocation.getWorld().spawnEntity(
                    multiplayerLocation.clone().add(0, -size/2, 0), EntityType.INTERACTION);
            interaction.setInteractionHeight(size);
            interaction.setInteractionWidth(size);
            interaction.getPersistentDataContainer().set(interactionKey, PersistentDataType.STRING, "MULTIPLAYER");
            displays.add(itemDisplay);
            displays.add(textDisplay);
            displays.add(interaction);
            multiplayerDisplay = textDisplay;
        }
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!entity.getPersistentDataContainer().has(interactionKey, PersistentDataType.STRING)) return;
        String string = entity.getPersistentDataContainer().getOrDefault(interactionKey, PersistentDataType.STRING, "");
        if (string.equals("SINGLEPLAYER")) {
            event.getPlayer().teleport(multiplayerDisplay.getLocation());
        } else if (string.equals("MULTIPLAYER")) {
            event.getPlayer().teleport(singleplayerDisplay.getLocation());
        }
    }

    public static FillInTheWall getInstance() {
        return instance;
    }

    public FileConfiguration getPlayingFieldConfig() {
        return playingFieldConfig;
    }

    public void savePlayingFieldConfig() {
        try {
            playingFieldConfig.save(new File(getDataFolder(), "playingfields.yml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("reload") && sender.isOp()) {
                reload();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
                return true;
            } else if (args[0].equalsIgnoreCase("abort") && sender.isOp()) {
                // todo everything from this line forth is temporary
                if (PlayingFieldManager.game != null) {
                    PlayingFieldManager.game.stop();
                    PlayingFieldManager.game = null;
                    sender.sendMessage("Score attack game aborted");
                } else {
                    sender.sendMessage("No score attack game to abort");
                }

                if (PlayingFieldManager.vsGame != null) {
                    PlayingFieldManager.vsGame.stop();
                    PlayingFieldManager.vsGame = null;
                    sender.sendMessage("Versus game aborted");
                } else {
                    sender.sendMessage("No versus game to abort");
                }
            } else if (args[0].equalsIgnoreCase("timer") && sender.isOp()) {
                if (PlayingFieldManager.pregame.isActive()) {
                    PlayingFieldManager.pregame.cancelCountdown();
                    sender.sendMessage("Score attack timer cancelled");
                } else {
                    PlayingFieldManager.pregame.startCountdown();
                    sender.sendMessage("Score attack timer started");
                }

                if (PlayingFieldManager.vsPregame.isActive()) {
                    PlayingFieldManager.vsPregame.cancelCountdown();
                    sender.sendMessage("Versus timer cancelled");
                } else {
                    PlayingFieldManager.vsPregame.startCountdown();
                    sender.sendMessage("Versus timer started");
                }
            } else if (args[0].equalsIgnoreCase("versus") && sender.isOp()) {
                if (PlayingFieldManager.vsPregame.isActive()) {
                    PlayingFieldManager.vsPregame.cancelCountdown();
                    sender.sendMessage("Timer cancelled");
                } else {
                    PlayingFieldManager.vsPregame.startCountdown();
                    sender.sendMessage("Timer started");
                }
            } else if (args[0].equalsIgnoreCase("start") && sender.isOp()) {
                if (args.length >= 2 && args[1].equalsIgnoreCase("versus")) {
                    if (PlayingFieldManager.vsPregame.isActive()) {
                        PlayingFieldManager.vsPregame.startGame();
                        sender.sendMessage("Starting versus game");
                    } else {
                        sender.sendMessage("Start a timer with /fillinthewall versus first");
                    }
                    return true;
                } else {
                    if (PlayingFieldManager.pregame.isActive()) {
                        PlayingFieldManager.pregame.startGame();
                        sender.sendMessage("Starting game");
                    } else {
                        sender.sendMessage("Start a timer with /fillinthewall timer first");
                    }
                }

            } else if (args[0].equalsIgnoreCase("custom")) {
                if (args.length == 2 && sender instanceof Player player && PlayingFieldManager.isInGame(player)) {
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field.getScorer().getGamemode() == Gamemode.CUSTOM) {
                        WallBundle bundle = WallBundle.getWallBundle(args[1]);
                        if (bundle.size() == 0) {
                            sender.sendMessage(ChatColor.RED + "Something went wrong loading custom walls!");
                        } else {
                            List<Wall> walls = bundle.getWalls();
                            field.getQueue().clearAllWalls();
                            walls.forEach(field.getQueue()::addWall);
                            sender.sendMessage(ChatColor.GREEN + "Imported " + walls.size() + " walls");
                            field.getScorer().setHasImportedCustomWalls(true);
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "You can only use this command in custom mode");
                    }
                } else {
                    sender.sendMessage("Wrong syntax... I won't tell you how though! >:)");
                }
            } else if (args[0].equalsIgnoreCase("modifier") && sender.isOp()) {
                if (args.length == 4) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw modifier <player> <mod> <ticks>");
                        return true;
                    }
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field == null) {
                        sender.sendMessage("This player isn't in a game!");
                        return true;
                    }
                    int ticks = Integer.parseInt(args[3]);

                    ModifierEvent event;
                    try {
                        event = ModifierEvent.Type.valueOf(args[2].toUpperCase()).createEvent(field);
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Unknown modifier");
                        return true;
                    }

                    event.setTicksRemaining(ticks);
                    event.activate();
                } else {
                    sender.sendMessage("/fitw modifier <player> <mod> <ticks>");
                }

            } else if (args[0].equalsIgnoreCase("spawn") && sender instanceof Player player) {
                player.teleport(FillInTheWall.getInstance().getMultiplayerSpawn());
                player.setGameMode(GameMode.ADVENTURE);
            } else if (args[0].equalsIgnoreCase("garbage") && sender.isOp()) {
                if (args.length == 3) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw garbage <player> <amount>");
                        return true;
                    }
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field == null) {
                        sender.sendMessage("This player isn't in a game!");
                        return true;
                    }

                    int amount = Integer.parseInt(args[2]);

                    for (int i = 0; i < amount; i++) {
                        field.getScorer().getGarbageQueue().add(new Wall(field.getLength(), field.getHeight()));
                    }

                    sender.sendMessage("Sent " + amount + " garbage walls to " + player.getName());
                } else {
                    sender.sendMessage("/fitw garbage <player> <amount>");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("bundle") && sender.isOp()) {
                if (args.length == 3) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw bundle <player> <bundlename>");
                        return true;
                    }
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field == null) {
                        sender.sendMessage("This player isn't in a game!");
                        return true;
                    }

                    WallBundle bundle = WallBundle.getWallBundle(args[2]);
                    if (bundle.size() == 0) {
                        sender.sendMessage(ChatColor.RED + "Something went wrong loading custom walls!");
                    } else {
                        List<Wall> walls = bundle.getWalls();
                        walls.forEach(field.getQueue()::addPriorityWall);
                        sender.sendMessage(ChatColor.GREEN + "Imported " + walls.size() + " walls");
                    }
                } else {
                    sender.sendMessage("/fitw bundle <player> <bundlename>");
                    return true;
                }
            } else if (args[0].equalsIgnoreCase("tip") && sender.isOp()) {
                if (args.length >= 3) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw tip <player> <string>");
                        return true;
                    }
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    if (field == null) {
                        sender.sendMessage("This player isn't in a game!");
                        return true;
                    }

                    StringBuilder tip = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        tip.append(args[i]);
                        tip.append(" ");
                    }

                    field.setTipDisplay(tip.toString());
                }
            } else if (args[0].equalsIgnoreCase("demomode") && sender.isOp()) {
                if (args.length >= 2) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw demomode <player>");
                        return true;
                    }

                    ClientboundGameEventPacket packet = new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, 0);
                    ((CraftPlayer) player).getHandle().connection.sendPacket(packet);
                }
            } else if (args[0].equalsIgnoreCase("endcredits") && sender.isOp()) {
                if (args.length >= 2) {
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage("/fitw endcredits <player>");
                        return true;
                    }

                    ClientboundGameEventPacket packet = new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1);
                    ((CraftPlayer) player).getHandle().connection.sendPacket(packet);
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public void reload() {
        super.reloadConfig();
        for (Entity display : displays) {
            display.remove();
        }
        displays.clear();
        if (!loadSQL()) return;
        spawnPortals();
        Leaderboards.spawnLeaderboards(getConfig());
        leaderboardUpdateTask = Bukkit.getScheduler().runTaskTimer(this, Leaderboards::updateLeaderboards, 0, 20 * 30);
        multiplayerSpawn = getConfig().getLocation("multiplayer-spawn");
        spectatorFinalsSpawn = getConfig().getLocation("spectator-finals-spawn");
        loadPlayingFieldConfig();
        PlayingFieldManager.removeAllGames();
        PlayingFieldManager.parseConfig(getPlayingFieldConfig());
    }

    private void loadPlayingFieldConfig() {
        File playingFieldFile = new File(getDataFolder(), "playingfields.yml");
        if (!playingFieldFile.exists()) {
            playingFieldFile.getParentFile().mkdirs();
            saveResource("playingfields.yml", false);
        }
        playingFieldConfig = YamlConfiguration.loadConfiguration(playingFieldFile);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        ArrayList<String> strings = new ArrayList<>();
        if (args.length == 1) {
            strings.add("spawn");
            strings.add("custom");

            if (sender.isOp()) {
                strings.add("reload");
                strings.add("abort");
                strings.add("timer");
                strings.add("start");
                strings.add("versus");
                strings.add("garbage");
                strings.add("bundle");
                strings.add("tip");
                strings.add("modifier");
                strings.add("demomode");
                strings.add("endcredits");
            }
            StringUtil.copyPartialMatches(args[0], strings, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("custom")) {
                StringUtil.copyPartialMatches(args[1], WallBundle.getAvailableWallBundles(), completions);
            } else if (args[0].equalsIgnoreCase("modifier")|| args[0].equalsIgnoreCase("garbage")
                || args[0].equalsIgnoreCase("bundle") || args[0].equalsIgnoreCase("tip")
                    || args[0].equalsIgnoreCase("endcredits") || args[0].equalsIgnoreCase("demomode")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    strings.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[1], strings, completions);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("modifier")) {
                for (ModifierEvent.Type type : ModifierEvent.Type.values()) {
                    strings.add(type.name());
                }
                StringUtil.copyPartialMatches(args[2], strings, completions);
            } else if (args[0].equalsIgnoreCase("bundle")) {
                StringUtil.copyPartialMatches(args[2], WallBundle.getAvailableWallBundles(), completions);
            }
        }
        return completions;
    }

    private boolean loadSQL() {
        FileConfiguration config = getConfig();
        dataSource.setServerName(config.getString("database.host"));
        dataSource.setPortNumber(config.getInt("database.port"));
        dataSource.setDatabaseName(config.getString("database.database"));
        dataSource.setUser(config.getString("database.user"));
        dataSource.setPassword(config.getString("database.password"));


        // Test the connection
        try {
            Connection conn = dataSource.getConnection();
            if (!conn.isValid(1)) {
                throw new SQLException("Could not establish database connection.");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("FillInTheWall: Could not establish database connection. " +
                    "Please make sure you are using a MySQL server and that the config.yml is set up correctly." +
                    "\nThe plugin will still work, but leaderboards will be disabled, scores will not submit, and player-saved " +
                    "hotbars will not load");
            return false;
        }

        String sql1 = "CREATE TABLE IF NOT EXISTS scores(" +
                "uuid CHAR(36) NOT NULL," +
                "SCORE_ATTACK INT DEFAULT 0 NOT NULL," +
                "RUSH_SCORE_ATTACK INT DEFAULT 0 NOT NULL," +
                "MARATHON INT DEFAULT 0 NOT NULL," +
                "SPRINT INT DEFAULT 12000 NOT NULL," +
                "MEGA INT DEFAULT 12000 NOT NULL," +
                "PRIMARY KEY (uuid));";
        String sql2 = "CREATE TABLE IF NOT EXISTS hotbars(" +
                "uuid CHAR(36) NOT NULL," +
                "hotbar CHAR(9) DEFAULT ? NOT NULL," +
                "PRIMARY KEY (uuid));";
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(sql1);
            stmt.executeUpdate();
            stmt = conn.prepareStatement(sql2);
            stmt.setString(1, PlayingField.DEFAULT_HOTBAR);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static Connection getSQLConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public Location getMultiplayerSpawn() {
        return multiplayerSpawn.clone();
    }

    public Location getSpectatorFinalsSpawn() {
        return spectatorFinalsSpawn.clone();
    }
}
