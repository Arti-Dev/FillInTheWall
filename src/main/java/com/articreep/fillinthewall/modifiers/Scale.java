package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Scale extends ModifierEvent implements Listener {
    private final Map<Player, Double> playerScales = new HashMap<>();
    private final Map<Player, Double> playerReachDistances = new HashMap<>();
    private double scale;

    public Scale(PlayingField field) {
        super(field);
        Random random = new Random();
        if (random.nextBoolean()) {
            scale = random.nextDouble(0.0625, 1);
        } else {
            scale = random.nextDouble(1, 5);
        }
    }

    @Override
    public void activate() {
        super.activate();
        Bukkit.getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        ChatColor color;
        if (scale < 1) {
            color = ChatColor.RED;
        } else {
            color = ChatColor.BLUE;
        }
        final double DEFAULT_BLOCK_INTERACTION_RANGE = 4.5;
        double blockInteractionRange = DEFAULT_BLOCK_INTERACTION_RANGE * Math.max(scale, 1);
        for (Player player : field.getPlayers()) {
            playerScales.put(player, player.getAttribute(Attribute.SCALE).getBaseValue());
            player.getAttribute(Attribute.SCALE).setBaseValue(scale);
            playerReachDistances.put(player, player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getBaseValue());
            player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).setBaseValue(blockInteractionRange);
        }

        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i > 20) {
                    cancel();
                    return;
                }

                double diff = scale - 1;
                double toDisplay = 1 + (diff * i/20.0);

                float pitch;
                if (scale <= 1) {
                    pitch = (float) Math.pow(2, -(1-toDisplay));

                } else {
                    pitch = (float) Math.pow(2, toDisplay/5);
                }
                field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_HARP, 5, pitch);

                field.sendTitleToPlayers("Scale!", "Your player model has been scaled by " +
                        color + String.format("%.2f", toDisplay) + ChatColor.RESET + "!", 0, 40, 10);
                i++;
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
    }

    @EventHandler
    public void onPlayerDC(PlayerQuitEvent event) {
        resetPlayer(event.getPlayer());
    }

    private void resetPlayer(Player player) {
        if (playerScales.containsKey(player)) {
            double scale = playerScales.get(player);
            player.getAttribute(Attribute.SCALE).setBaseValue(scale);
            playerScales.remove(player);
        }
        if (playerReachDistances.containsKey(player)) {
            double blockInteractionRange = playerReachDistances.get(player);
            player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).setBaseValue(blockInteractionRange);
            playerReachDistances.remove(player);
        }
        HandlerList.unregisterAll(this);
    }

    @Override
    public void end() {
        super.end();
        for (Player player : field.getPlayers()) {
            resetPlayer(player);
        }
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= 3) {
                    cancel();
                    return;
                }
                field.playSoundToPlayers(Sound.BLOCK_NOTE_BLOCK_HARP, 5, 1);
                i++;
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 1);
        field.sendTitleToPlayers("", "Your player model has been reset!", 0, 20, 10);
    }

    @Override
    public Scale copy(PlayingField newPlayingField) {
        Scale copy = new Scale(newPlayingField);
        copy.scale = scale;
        return copy;
    }
}
