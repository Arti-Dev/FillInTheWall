package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.PlayingField;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Scale extends ModifierEvent implements Listener {
    private final Map<Player, Double> playerScales = new HashMap<>();
    private final Map<Player, Double> playerReachDistances = new HashMap<>();

    private final double DEFAULT_BLOCK_INTERACTION_RANGE = 4.5;

    public Scale(PlayingField field, int ticks) {
        super(field, ticks);
    }

    @Override
    public void activate() {
        super.activate();
        // todo roll a roulette of random scale values before actually applying for visual effect
        FillInTheWall.getInstance().getServer().getPluginManager().registerEvents(this, FillInTheWall.getInstance());
        Random random = new Random();
        double scale;
        if (random.nextBoolean()) {
            scale = random.nextDouble(0.0625, 1);
        } else {
            scale = random.nextDouble(1, 5);
        }
        double blockInteractionRange = DEFAULT_BLOCK_INTERACTION_RANGE * Math.max(scale, 1);
        for (Player player : field.getPlayers()) {
            playerScales.put(player, player.getAttribute(Attribute.GENERIC_SCALE).getBaseValue());
            player.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(scale);
            playerReachDistances.put(player, player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE).getBaseValue());
            player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE).setBaseValue(blockInteractionRange);
        }
        field.sendTitleToPlayers("Scale!", "Your player model has been scaled by " + scale + "!", 0, 40, 10);
    }

    @EventHandler
    public void onPlayerDC(PlayerQuitEvent event) {
        resetPlayer(event.getPlayer());
    }

    private void resetPlayer(Player player) {
        if (playerScales.containsKey(player)) {
            double scale = playerScales.get(player);
            player.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(scale);
            playerScales.remove(player);
        }
        if (playerReachDistances.containsKey(player)) {
            double blockInteractionRange = playerReachDistances.get(player);
            player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE).setBaseValue(blockInteractionRange);
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
        field.sendTitleToPlayers("", "Your player model has been reset!", 0, 20, 10);
    }
}
