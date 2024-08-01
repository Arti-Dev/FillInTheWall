package com.articreep.fillinthewall;

import com.articreep.fillinthewall.environments.Finals;
import com.articreep.fillinthewall.environments.TheVoid;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.modifiers.*;
import com.articreep.fillinthewall.multiplayer.Pregame;
import com.articreep.fillinthewall.multiplayer.SettingsMenu;
import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    private Map<TextDisplay, Gamemode> leaderboards = new HashMap<>();
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

        if (!loadSQL()) return;

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            // todo temporary
            PlayingFieldManager.pregame = new Pregame(Bukkit.getWorld("multi"), Gamemode.MULTIPLAYER_SCORE_ATTACK,
                    2, 30);
            PlayingFieldManager.vsPregame = new Pregame(Bukkit.getWorld("versus"), Gamemode.VERSUS, 2, 15);
            PlayingFieldManager.parseConfig(getPlayingFieldConfig());
            spawnPortals();
            spawnLeaderboards();
            multiplayerSpawn = getConfig().getLocation("multiplayer-spawn");
        }, 1);

        Bukkit.getLogger().info(ChatColor.BLUE + "FillInTheWall has been enabled!");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        displays.forEach(Entity::remove);
        removeLeaderboards();
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
            if (args[0].equalsIgnoreCase("reload")) {
                reload();
                sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
                return true;
            } else if (args[0].equalsIgnoreCase("abort")) {
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
            } else if (args[0].equalsIgnoreCase("timer")) {
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
            } else if (args[0].equalsIgnoreCase("versus")) {
                if (PlayingFieldManager.vsPregame.isActive()) {
                    PlayingFieldManager.vsPregame.cancelCountdown();
                    sender.sendMessage("Timer cancelled");
                } else {
                    PlayingFieldManager.vsPregame.startCountdown();
                    sender.sendMessage("Timer started");
                }
            } else if (args[0].equalsIgnoreCase("start")) {
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
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "You can only use this command in custom mode");
                    }
                } else {
                    sender.sendMessage("Wrong syntax... I won't tell you how though! >:)");
                }
            } else if (args[0].equalsIgnoreCase("modifier")) {
                if (args.length == 3 && sender instanceof Player player && PlayingFieldManager.isInGame(player)) {
                    PlayingField field = PlayingFieldManager.activePlayingFields.get(player);
                    int ticks = Integer.parseInt(args[2]);

                    ModifierEvent event;
                    if (args[1].equalsIgnoreCase("popin")) event = new PopIn(field);
                    else if (args[1].equalsIgnoreCase("freeze")) event = new Freeze(field);
                    else if (args[1].equalsIgnoreCase("rush")) event = new Rush(field);
                    else if (args[1].equalsIgnoreCase("scale")) event = new Scale(field);
                    else if (args[1].equalsIgnoreCase("line")) event = new Lines(field);
                    else if (args[1].equalsIgnoreCase("inverted")) event = new Inverted(field);
                    else if (args[1].equalsIgnoreCase("fireinthehole")) event = new FireInTheHole(field);
                    else if (args[1].equalsIgnoreCase("stripes")) event = new Stripes(field);
                    else if (args[1].equalsIgnoreCase("gravity")) event = new Gravity(field);
                    else if (args[1].equalsIgnoreCase("playerinthewall")) event = new PlayerInTheWall(field);
                    else if (args[1].equalsIgnoreCase("multiplace")) event = new Multiplace(field);
                    else if (args[1].equalsIgnoreCase("flip")) event = new Flip(field);
                    else {
                        sender.sendMessage(ChatColor.RED + "Unknown modifier");
                        return true;
                    }

                    event.setTicksRemaining(ticks);
                    event.activate();
                } else {
                    sender.sendMessage("/hitw modifier <mod> <ticks>");
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
        spawnLeaderboards();
        multiplayerSpawn = getConfig().getLocation("multiplayer-spawn");
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
            strings.add("modifier");
            StringUtil.copyPartialMatches(args[0], strings, completions);
        } else if (args.length == 2) {
            strings.add("popin");
            strings.add("freeze");
            strings.add("rush");
            strings.add("scale");
            strings.add("line");
            strings.add("inverted");
            strings.add("fireinthehole");
            strings.add("stripes");
            strings.add("gravity");
            strings.add("playerinthewall");
            strings.add("multiplace");
            strings.add("flip");
            StringUtil.copyPartialMatches(args[1], strings, completions);
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


        // Test the connection, if it fails do not load the plugin
        try {
            Connection conn = dataSource.getConnection();
            if (!conn.isValid(1)) {
                throw new SQLException("Could not establish database connection.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        String sql = "CREATE TABLE IF NOT EXISTS scores(" +
                "uuid CHAR(36) NOT NULL," +
                "SCORE_ATTACK INT DEFAULT 0 NOT NULL," +
                "RUSH_SCORE_ATTACK INT DEFAULT 0 NOT NULL," +
                "MARATHON INT DEFAULT 0 NOT NULL," +
                "PRIMARY KEY (uuid));";
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static Connection getSQLConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void spawnLeaderboards() {
        removeLeaderboards();
        FileConfiguration config = getConfig();

        Location scoreAttackLocation = config.getLocation("leaderboards.score-attack");
        Location rushScoreAttackLocation = config.getLocation("leaderboards.rush-score-attack");
        Location marathonLocation = config.getLocation("leaderboards.marathon");

        if (scoreAttackLocation != null) {
            TextDisplay scoreAttackDisplay = (TextDisplay) scoreAttackLocation.getWorld().spawnEntity(
                    scoreAttackLocation, EntityType.TEXT_DISPLAY);
            scoreAttackDisplay.setText("Score Attack Leaderboard");
            scoreAttackDisplay.setBillboard(Display.Billboard.VERTICAL);
            leaderboards.put(scoreAttackDisplay, Gamemode.SCORE_ATTACK);
        }
        if (rushScoreAttackLocation != null) {
            TextDisplay rushScoreAttackDisplay = (TextDisplay) rushScoreAttackLocation.getWorld().spawnEntity(
                    rushScoreAttackLocation, EntityType.TEXT_DISPLAY);
            rushScoreAttackDisplay.setText("Rush Score Attack Leaderboard");
            rushScoreAttackDisplay.setBillboard(Display.Billboard.VERTICAL);
            leaderboards.put(rushScoreAttackDisplay, Gamemode.RUSH_SCORE_ATTACK);
        }
        if (marathonLocation != null) {
            TextDisplay marathonDisplay = (TextDisplay) marathonLocation.getWorld().spawnEntity(
                    marathonLocation, EntityType.TEXT_DISPLAY);
            marathonDisplay.setText("Marathon Leaderboard");
            marathonDisplay.setBillboard(Display.Billboard.VERTICAL);
            leaderboards.put(marathonDisplay, Gamemode.MARATHON);
        }

        updateLeaderboards();
    }

    private void removeLeaderboards() {
        for (TextDisplay display : leaderboards.keySet()) {
            display.remove();
        }
        leaderboards.clear();
    }

    private void updateLeaderboards() {
        if (leaderboardUpdateTask != null) {
            leaderboardUpdateTask.cancel();
        }
        leaderboardUpdateTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Map.Entry<TextDisplay, Gamemode> entry : leaderboards.entrySet()) {
                TextDisplay display = entry.getKey();
                Gamemode gamemode = entry.getValue();
                StringBuilder stringBuilder = new StringBuilder(gamemode.getTitle());
                stringBuilder.append("\n").append(ChatColor.GRAY).append("Top Scores\n");
                try {
                    LinkedHashMap<UUID, Integer> topScores = ScoreDatabase.getTopScores(gamemode);
                    int i = 1;
                    for (Map.Entry<UUID, Integer> score : topScores.entrySet()) {
                        stringBuilder.append("\n")
                                .append(ChatColor.YELLOW)
                                .append("#").append(i).append(" ")
                                .append(Bukkit.getOfflinePlayer(score.getKey()).getName())
                                .append(": ").append(score.getValue());
                        i++;
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    stringBuilder.append("\n").append(ChatColor.RED).append("Error loading scores");
                } finally {
                    stringBuilder.append("\n\n").append(ChatColor.GRAY).append("Updates every 30 seconds");
                    display.setText(stringBuilder.toString());
                }
            }
        }, 0, 20 * 30);
    }

    public Location getMultiplayerSpawn() {
        return multiplayerSpawn.clone();
    }
}
