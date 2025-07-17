package com.articreep.fillinthewall.menu;

import com.articreep.fillinthewall.*;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.playerinfo.PlayerLevels;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectMenu implements Listener {
    private final Location location;
    private TextDisplay title;
    private BlockDisplay block;
    private final float blockScale = 0.5f;
    private TextDisplay description;
    private TextDisplay controls;

    private final PlayingField field;
    private final Map<Gamemode, Integer> personalBests = new HashMap<>();
    private int playerLevel;
    private int gamemodeIndex = 0;
    private BukkitTask particleTask;
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();
    private BukkitTask spinTask = null;

    public SelectMenu(Location location, PlayingField field) {
        this.location = location;
        this.field = field;
        if (field.getPlayers().size() == 1 && !Database.isOfflineMode()) {
            UUID uuid = field.getPlayers().iterator().next().getUniqueId();
            Bukkit.getScheduler().runTaskAsynchronously(FillInTheWall.getInstance(), () -> {
                for (Gamemode mode : Database.getSupportedGamemodes()) {
                    try {
                        personalBests.put(mode, Database.getRecord(uuid, mode));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        if (!Database.isOfflineMode()) {
            for (Player player : field.getPlayers()) {
                try {
                    int level = PlayerLevels.getLevel(player.getUniqueId()).getValue0();
                    if (level > playerLevel) {
                        playerLevel = level;
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } else {
            playerLevel = -1; // Allow access to everything
        }
    }

    public void display() {
        // todo fine-tune and maybe generalize it for small playing fields
        title = (TextDisplay) location.getWorld().spawnEntity(location.clone().add(0, 1.5, 0), EntityType.TEXT_DISPLAY);
        block = (BlockDisplay) location.getWorld().spawnEntity(location.clone().add(0, 2.5, 0), EntityType.BLOCK_DISPLAY);
        block.setTransformation(new Transformation(
                new Vector3f(-blockScale/2f, -blockScale/2f, -blockScale/2f),
                new AxisAngle4f(0, 0, 0, 1), new Vector3f(blockScale, blockScale, blockScale),
                new AxisAngle4f(0, 0, 0, 1)));
        block.setInterpolationDuration(1);
        description = (TextDisplay) location.getWorld().spawnEntity(location.clone().add(0, 0.5, 0), EntityType.TEXT_DISPLAY);
        controls = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        controls.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1), new Vector3f(0.5f, 0.5f, 0.5f),
                new AxisAngle4f(0, 0, 0, 1)));


        if (field.getHeight() * field.getLength() >= 400) {
            for (int i = 0; i < Gamemode.values().length; i++) {
                if (Gamemode.values()[i] == Gamemode.MEGA) {
                    gamemodeIndex = i;
                    break;
                }
            }
        }
        title.setBillboard(Display.Billboard.CENTER);
        description.setBillboard(Display.Billboard.CENTER);
        controls.setBillboard(Display.Billboard.CENTER);
        updateMenu(Gamemode.values()[gamemodeIndex]);
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        particleTask = createParticleTask();
        spinTask = createSpinTask();
    }

    private BukkitTask createParticleTask() {
        return Bukkit.getScheduler().runTaskTimer(FillInTheWall.getInstance(), () -> {
            Player player = Bukkit.getPlayer(field.getEarliestPlayerUUID());
            if (player != null) {
                World world = player.getWorld();
                Color color = Color.fromRGB((int) (Math.random() * 255), (int) (Math.random() * 255), (int) (Math.random() * 255));
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 2,
                        0.5, 1, 0.5, 0.1, new Particle.DustOptions(color, 1F));
            }
        }, 0, 5);
    }

    private BukkitTask createSpinTask() {
        return new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = block.getLocation();
                loc.setYaw(loc.getYaw() + 10);
                block.teleport(loc);
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        UUID controller = field.getEarliestPlayerUUID();
        if (controller == null || !controller.equals(event.getPlayer().getUniqueId())) return;
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            nextGamemode();
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            previousGamemode();
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                event.getClickedBlock().getType() == Material.LEVER) {
            confirmAndDespawn();
        }
    }

    @EventHandler
    public void onPlayerSwap(PlayerSwapHandItemsEvent event) {
        if (!field.getPlayers().contains(event.getPlayer())) return;
        event.setCancelled(true);
        // Confirm gamemode
        confirmAndDespawn();
    }

    private void nextGamemode() {
        gamemodeIndex++;
        if (gamemodeIndex >= Gamemode.values().length) {
            gamemodeIndex = 0;
        }
        if (Gamemode.values()[gamemodeIndex].getDefaultSettings().getBooleanAttribute(GamemodeAttribute.MULTIPLAYER)) {
            nextGamemode();
            return;
        }
        updateMenu(Gamemode.values()[gamemodeIndex]);
    }

    private void previousGamemode() {
        gamemodeIndex--;
        if (gamemodeIndex < 0) gamemodeIndex = Gamemode.values().length - 1;
        if (Gamemode.values()[gamemodeIndex].getDefaultSettings().getBooleanAttribute(GamemodeAttribute.MULTIPLAYER)) {
            previousGamemode();
            return;
        }
        updateMenu(Gamemode.values()[gamemodeIndex]);
    }

    private void updateMenu(Gamemode mode) {
        String string = "<white><shadow:dark_gray:1>Select a gamemode</shadow>\n" +
                miniMessage.serialize(mode.getTitle());
        String descriptionString = miniMessage.serialize(mode.getDescription());

        if (personalBests.containsKey(mode)) {
            if (mode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.SCORE_BY_TIME)) {
                descriptionString += "\n<aqua>Personal best: <bold>" + Utils.getPreciseFormattedTime(personalBests.get(mode)) + "</bold>";
            } else {
                descriptionString += "\n<gold>Personal best: <bold>" + personalBests.get(mode) + "</bold>";
            }
        }

        if (mode.getLevelReq() > playerLevel && !Database.isOfflineMode()) {
            descriptionString += "\n<red>Requires player level " + mode.getLevelReq();
        }

        controls.text(miniMessage.deserialize("<gray><key:key.mouse.left>/<key:key.mouse.right> to change gamemode\n" +
                "Press <key:key.swapOffhand> to start game"));
        title.text(miniMessage.deserialize(string));
        description.text(miniMessage.deserialize(descriptionString));
        Material blockMaterial = mode.getBlock();
        if (!blockMaterial.isBlock()) blockMaterial = Material.STONE_BUTTON;
        block.setBlock(blockMaterial.createBlockData());
    }

    public void confirmAndDespawn() {
        if (field.isLocked()) {
            field.sendMessageToPlayers(miniMessage.deserialize("<red>Field is locked - cannot start game"));
            despawn();
            return;
        }
        Gamemode mode = Gamemode.values()[gamemodeIndex];
        if (mode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.MULTIPLAYER)) {
            field.sendMessageToPlayers(miniMessage.deserialize("<red>You cannot start a multiplayer game through this menu!"));
        } else if (mode == Gamemode.MEGA && field.getLength() * field.getHeight() < 400) {
            field.sendMessageToPlayers(miniMessage.deserialize("<red>Your board must be at least 400 blocks in total area to play this!"));
        } else if (mode.getLevelReq() > playerLevel && !Database.isOfflineMode()) {
            field.sendMessageToPlayers(miniMessage.deserialize("<red>Your player level is too low to play this gamemode!"));
        } else {
            field.countdownStart(Gamemode.values()[gamemodeIndex]);
        }
        despawn();
    }

    public void despawn(boolean force) {
        HandlerList.unregisterAll(this);
        if (title != null) title.remove();
        if (block != null && !force) {
            new BukkitRunnable() {
                float scale = blockScale;
                @Override
                public void run() {
                    scale -= 0.05f;
                    if (scale <= 0) {
                        block.remove();
                        this.cancel();
                        return;
                    }
                    block.setTransformation(new Transformation(
                            new Vector3f(-scale/2f, -scale/2f, -scale/2f),
                            new AxisAngle4f(0, 0, 0, 1), new Vector3f(scale, scale, scale),
                            new AxisAngle4f(0, 0, 0, 1)));
                }
            }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
        } else if (block != null) {
            block.remove();
        }
        if (description != null) description.remove();
        if (controls != null) controls.remove();
        if (particleTask != null) {
            particleTask.cancel();
        }
        if (spinTask != null) {
            spinTask.cancel();
        }
    }

    public void despawn() {
        despawn(false);
    }
}
