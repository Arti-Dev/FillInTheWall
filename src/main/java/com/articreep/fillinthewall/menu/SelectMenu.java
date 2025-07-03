package com.articreep.fillinthewall.menu;

import com.articreep.fillinthewall.*;
import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectMenu implements Listener {
    private final Location location;
    private TextDisplay title;
    private BlockDisplay block;
    private TextDisplay description;
    private TextDisplay controls;

    private final PlayingField field;
    private final Map<Gamemode, Integer> personalBests = new HashMap<>();
    private int gamemodeIndex = 0;
    private BukkitTask particleTask;
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();

    public SelectMenu(Location location, PlayingField field) {
        this.location = location;
        this.field = field;
        if (field.getPlayers().size() == 1 && !Database.isOfflineMode()) {
            for (Gamemode mode : Database.getSupportedGamemodes()) {
                try {
                    personalBests.put(mode, Database.getRecord(field.getPlayers().iterator().next().getUniqueId(), mode));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void display() {
        // todo fine-tune and maybe generalize it for small playing fields
        title = (TextDisplay) location.getWorld().spawnEntity(location.clone().add(0, 1, 0), EntityType.TEXT_DISPLAY);
        // todo center this hitbox so we can spin it
        block = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        block.setInterpolationDuration(1);
        float initialScale = 0.05f;
        Utils.scaleDisplay(block, initialScale);
        description = (TextDisplay) location.getWorld().spawnEntity(location.clone().subtract(0, 0.5, 0.5), EntityType.TEXT_DISPLAY);
        controls = (TextDisplay) location.getWorld().spawnEntity(location.clone().subtract(0, 1.5, 0), EntityType.TEXT_DISPLAY);
        Utils.scaleDisplay(controls, 0.5f);


        if (field.getHeight() * field.getLength() >= 400) {
            for (int i = 0; i < Gamemode.values().length; i++) {
                if (Gamemode.values()[i] == Gamemode.MEGA) {
                    gamemodeIndex = i;
                    break;
                }
            }
        }
        title.setBillboard(Display.Billboard.CENTER);
        updateMenu(Gamemode.values()[gamemodeIndex]);
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        particleTask = createParticleTask();
        new BukkitRunnable() {
            float scale = initialScale;
            @Override
            public void run() {
                scale += 0.05f;
                Utils.scaleDisplay(block, scale);
                if (scale >= 1) this.cancel();
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
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
        String string = "Select a gamemode\n" +
                miniMessage.serialize(mode.getTitle());
        String descriptionString = miniMessage.serialize(mode.getDescription());

        if (personalBests.containsKey(mode)) {
            if (mode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.SCORE_BY_TIME)) {
                descriptionString += "<aqua>Personal best: <bold>" + Utils.getPreciseFormattedTime(personalBests.get(mode)) + "</bold>\n";
            } else {
                descriptionString += "<gold>Personal best: <bold>" + personalBests.get(mode) + "</bold>\n";
            }
        }
        controls.text(miniMessage.deserialize("<white><key:key.mouse.left>/<key:key.mouse.right> to change gamemode\n" +
                "Press <key:key.swapOffhand> to start game"));
        title.text(miniMessage.deserialize(string));
        description.text(miniMessage.deserialize(descriptionString));
        block.setBlock(Material.WAXED_EXPOSED_CUT_COPPER.createBlockData());
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
        } else {
            field.countdownStart(Gamemode.values()[gamemodeIndex]);
        }
        despawn();
    }

    public void despawn() {
        HandlerList.unregisterAll(this);
        if (title != null) title.remove();
        if (block != null) block.remove();
        if (description != null) description.remove();
        if (controls != null) controls.remove();
        if (particleTask != null) {
            particleTask.cancel();
        }
    }
}
