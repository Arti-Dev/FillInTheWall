package com.articreep.fillinthewall.menu;

import com.articreep.fillinthewall.*;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.utils.Utils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Menu implements Listener {
    private final Location location;
    private TextDisplay select;
    private final PlayingField field;
    private final Map<Gamemode, Integer> personalBests = new HashMap<>();
    private int gamemodeIndex = 0;
    private BukkitTask particleTask;

    public Menu(Location location, PlayingField field) {
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
        select = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        if (field.getHeight() * field.getLength() >= 400) {
            for (int i = 0; i < Gamemode.values().length; i++) {
                if (Gamemode.values()[i] == Gamemode.MEGA) {
                    gamemodeIndex = i;
                    break;
                }
            }
        }
        setMenuGamemode(Gamemode.values()[gamemodeIndex]);
        select.setBillboard(Display.Billboard.CENTER);
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        particleTask = createParticleTask();
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
        setMenuGamemode(Gamemode.values()[gamemodeIndex]);
    }

    private void setMenuGamemode(Gamemode mode) {
        String string = "Select a gamemode\n" +
                mode.getTitle() + "\n" + mode.getDescription() + "\n";
        if (personalBests.containsKey(mode)) {
            if (mode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.SCORE_BY_TIME)) {
                string += ChatColor.AQUA + "Personal best: " + ChatColor.BOLD +
                        Utils.getPreciseFormattedTime(personalBests.get(mode)) + "\n";
            } else {
                string += ChatColor.GOLD + "Personal best: " + ChatColor.BOLD + personalBests.get(mode) + "\n";
            }
        }
        string += "\n" +
                ChatColor.RESET + "Left click to change gamemode\n" +
                "Press [F] or your offhand key to confirm";
        select.setText(string);
    }

    public void confirmAndDespawn() {
        if (field.isLocked()) {
            field.sendMessageToPlayers(ChatColor.RED + "Field is locked - cannot start game");
            despawn();
            return;
        }
        Gamemode mode = Gamemode.values()[gamemodeIndex];
        if (mode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.MULTIPLAYER)) {
            field.sendMessageToPlayers(ChatColor.RED + "You cannot start a multiplayer game through this menu!");
        } else if (mode == Gamemode.MEGA && field.getLength() * field.getHeight() < 400) {
            field.sendMessageToPlayers(ChatColor.RED + "Your board must be at least 400 blocks in total area to play this!");
        } else {
            field.countdownStart(Gamemode.values()[gamemodeIndex]);
        }
        despawn();
    }

    public void despawn() {
        HandlerList.unregisterAll(this);
        select.remove();
        if (particleTask != null) {
            particleTask.cancel();
        }
    }
}
