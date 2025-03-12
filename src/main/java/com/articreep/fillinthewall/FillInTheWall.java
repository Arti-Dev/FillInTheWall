package com.articreep.fillinthewall;

import com.articreep.fillinthewall.environments.Finals;
import com.articreep.fillinthewall.environments.TheVoid;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.multiplayer.Pregame;
import com.articreep.fillinthewall.multiplayer.SettingsMenu;
import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public final class FillInTheWall extends JavaPlugin implements Listener {
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
        getCommand("fillinthewall").setExecutor(new FITWCommand());
        getCommand("registerplayingfield").setExecutor(registerPlayingField);
        getCommand("settingsmenu").setExecutor(settingsMenu);
        getServer().getPluginManager().registerEvents(new PlayingFieldManager(), this);
        getServer().getPluginManager().registerEvents(new TheVoid(), this);
        getServer().getPluginManager().registerEvents(registerPlayingField, this);
        getServer().getPluginManager().registerEvents(new Finals(), this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(settingsMenu, this);
        getServer().getPluginManager().registerEvents(new GlobalListeners(), this);

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            loadPlayingFieldConfig();
            saveDefaultConfig();

            // Create directories
            File customWallFolder = new File(getDataFolder(), "custom");
            File musicFolder = new File(getDataFolder(), "music");
            customWallFolder.mkdirs();
            if (!musicFolder.exists()) {
                musicFolder.mkdirs();
                saveResource("fortress hill.nbs", false);
                // move the default music file to the music folder
                File defaultMusic = new File(getDataFolder(), "fortress hill.nbs");
                defaultMusic.renameTo(new File(musicFolder, "fortress hill.nbs"));
            }

            if (!loadSQL()) {
                Database.setOfflineMode(true);
            }

            if (getServer().getPluginManager().getPlugin("NoteBlockAPI") != null)
                NBSMusic.loadConfig(getConfig());
            else {
                Bukkit.getLogger().info("NoteBlockAPI not found - note block music disabled");
                NBSMusic.enabled = false;
            }

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

    public void reload() {
        super.reloadConfig();
        for (Entity display : displays) {
            display.remove();
        }
        displays.clear();
        NBSMusic.loadConfig(getConfig());
        Database.setOfflineMode(!loadSQL());
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
                "FOREIGN KEY (uuid) REFERENCES scores(uuid) ON DELETE CASCADE);";
        String sql3 = "CREATE TABLE IF NOT EXISTS playerInfo(" +
                "uuid CHAR(36) NOT NULL," +
                "newcomer BIT DEFAULT 1 NOT NULL," +
                "FOREIGN KEY (uuid) REFERENCES scores(uuid) ON DELETE CASCADE);";
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(sql1);
            stmt.executeUpdate();
            stmt = conn.prepareStatement(sql2);
            stmt.setString(1, PlayingField.DEFAULT_HOTBAR);
            stmt.executeUpdate();
            stmt = conn.prepareStatement(sql3);
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
