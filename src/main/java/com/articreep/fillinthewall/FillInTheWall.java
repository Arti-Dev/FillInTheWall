package com.articreep.fillinthewall;

import com.articreep.fillinthewall.commands.FITWCommand;
import com.articreep.fillinthewall.commands.PairUp;
import com.articreep.fillinthewall.commands.RegisterPlayingField;
import com.articreep.fillinthewall.environments.Finals;
import com.articreep.fillinthewall.environments.TheVoid;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.game.PlayingFieldManager;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.infodisplay.Leaderboards;
import com.articreep.fillinthewall.lobby.ChatAnnouncements;
import com.articreep.fillinthewall.lobby.LobbyItems;
import com.articreep.fillinthewall.lobby.NBSMusic;
import com.articreep.fillinthewall.menu.SandboxMenu;
import com.articreep.fillinthewall.multiplayer.Pregame;
import com.articreep.fillinthewall.commands.PregameSettingsMenu;
import com.articreep.fillinthewall.playerinfo.InventoryMenus;
import net.kyori.adventure.text.Component;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.*;

public final class FillInTheWall extends JavaPlugin implements Listener {
    public static final String NO_COLLISION_TEAM_NAME = "fitw_no_collision";
    private static Scoreboard blankScoreboard;
    private static FillInTheWall instance = null;
    private FileConfiguration playingFieldConfig;
    private final Set<Entity> displays = new HashSet<>();
    private final NamespacedKey interactionKey = new NamespacedKey(this, "singleplayerPortal");
    private Display singleplayerDisplay = null;
    private Display multiplayerDisplay = null;
    private Location multiplayerSpawn = null;
    private Location spectatorFinalsSpawn = null;

    private BukkitTask leaderboardUpdateTask = null;

    @Override
    public void onEnable() {
        instance = this;
        RegisterPlayingField registerPlayingField = new RegisterPlayingField();
        PregameSettingsMenu settingsMenu = new PregameSettingsMenu();
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
        getServer().getPluginManager().registerEvents(new InventoryMenus(), this);
        getServer().getPluginManager().registerEvents(new LobbyItems(), this);
        getServer().getPluginManager().registerEvents(new SandboxMenu(), this);
        getServer().getPluginManager().registerEvents(new PairUp(), this);

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            loadPlayingFieldConfig();
            saveDefaultConfig();

            blankScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Team team = blankScoreboard.registerNewTeam(FillInTheWall.NO_COLLISION_TEAM_NAME);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

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

            Database.loadSQL();

            if (getServer().getPluginManager().getPlugin("NoteBlockAPI") != null)
                NBSMusic.loadConfig(getConfig());
            else {
                getSLF4JLogger().info("NoteBlockAPI not found - note block music disabled");
                NBSMusic.enabled = false;
            }

            createMultiPregame();
            createVersusPregame();
            if (PlayingFieldManager.pregame != null)
                PlayingFieldManager.pregame.startCountdown();
            PlayingFieldManager.parseConfig(getPlayingFieldConfig());
            spawnPortals();
            Leaderboards.spawnLeaderboards(getConfig());
            leaderboardUpdateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, Leaderboards::updateLeaderboards, 0, 20 * 30);
            multiplayerSpawn = getConfig().getLocation("multiplayer-spawn");
            spectatorFinalsSpawn = getConfig().getLocation("spectator-finals-spawn");
            ChatAnnouncements.startAnnouncements();
        }, 1);

        getSLF4JLogger().info("FillInTheWall has been enabled!");

    }

    public void createMultiPregame() {
        if (!getConfig().getBoolean("pregame.enabled")) {
            getSLF4JLogger().info("Multiplayer pregame is disabled in config.");
            return;
        }
        String worldname = getConfig().getString("pregame.world");
        if (worldname == null) {
            getSLF4JLogger().warn("No world specified for multiplayer game.");
            return;
        }
        World world = Bukkit.getWorld(worldname);
        if (world == null) {
            getSLF4JLogger().warn("Could not find world {} for multiplayer game.", worldname);
            return;
        }

        PlayingFieldManager.pregame = new Pregame(world, Gamemode.MULTIPLAYER_SCORE_ATTACK,
                getConfig().getInt("pregame.min-players"),
                getConfig().getInt("pregame.countdown"));
    }

    public void createVersusPregame() {
        if (!getConfig().getBoolean("versus-pregame.enabled")) {
            return;
        }

        String worldname = getConfig().getString("versus-pregame.world");
        if (worldname == null) {
            getSLF4JLogger().warn("No world specified for versus game.");
            return;
        }
        World world = Bukkit.getWorld(worldname);
        if (world == null) {
            getSLF4JLogger().warn("Could not find world {} for versus game.", worldname);
            return;
        }

        PlayingFieldManager.vsPregame = new Pregame(world, Gamemode.VERSUS,
                getConfig().getInt("versus-pregame.min-players"),
                getConfig().getInt("versus-pregame.countdown"));
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
                field.forceRemoveMenu();
                field.removeEndScreen();
            }
        }
    }

    private void spawnPortals() {
        Location singleplayerLocation;
        if (!getConfig().getBoolean("singleplayer-portal.enabled")) {
            singleplayerLocation = null;
        } else {
            singleplayerLocation = getConfig().getLocation("singleplayer-portal.location");
        }

        Location multiplayerLocation;
        if (!getConfig().getBoolean("multiplayer-portal.enabled")) {
            multiplayerLocation = null;
        } else {
            multiplayerLocation = getConfig().getLocation("multiplayer-portal.location");
        }

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
            textDisplay.text(Component.text(singleplayerText));
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
            textDisplay.text(Component.text(multiplayerText));
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

        if (PlayingFieldManager.vsPregame != null) {
            PlayingFieldManager.vsPregame.cancelCountdown();
            PlayingFieldManager.vsPregame = null;
        }
        if (PlayingFieldManager.pregame != null) {
            PlayingFieldManager.pregame.cancelCountdown();
            PlayingFieldManager.pregame = null;
        }
        createMultiPregame();
        createVersusPregame();
        if (PlayingFieldManager.pregame != null)
            PlayingFieldManager.pregame.startCountdown();


        NBSMusic.loadConfig(getConfig());
        spawnPortals();
        Leaderboards.spawnLeaderboards(getConfig());
        if (leaderboardUpdateTask != null) {
            leaderboardUpdateTask.cancel();
        }
        leaderboardUpdateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, Leaderboards::updateLeaderboards, 0, 20 * 30);
        multiplayerSpawn = getConfig().getLocation("multiplayer-spawn");
        spectatorFinalsSpawn = getConfig().getLocation("spectator-finals-spawn");

        reloadPlayingFields();
    }

    public void reloadPlayingFields() {
        for (PlayingField field : PlayingFieldManager.playingFieldLocations.values()) {
            if (field.hasStarted()) field.stop(false, false);
            else {
                field.forceRemoveMenu();
                field.removeEndScreen();
            }
        }
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

    public Location getMultiplayerSpawn() {
        return multiplayerSpawn.clone();
    }

    public Location getSpectatorFinalsSpawn() {
        return spectatorFinalsSpawn.clone();
    }

    public static Scoreboard getBlankScoreboard() {
        return blankScoreboard;
    }
}
