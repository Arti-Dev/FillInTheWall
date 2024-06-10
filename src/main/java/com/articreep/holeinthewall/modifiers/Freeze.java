package com.articreep.holeinthewall.modifiers;

import com.articreep.holeinthewall.PlayingField;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class Freeze extends ModifierEvent {
    public Freeze(PlayingField field, int ticks) {
        super(field, ticks);
        wallFreeze = true;
        timeFreeze = true;
    }

    @Override
    public void activate() {
        for (Player player : field.getPlayers()) {
            player.sendTitle(ChatColor.AQUA + "FREEZE!", ChatColor.DARK_AQUA + "Walls are temporarily frozen!", 0, 40, 10);
            player.playSound(player, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5F, 1);
        }
    }

    @Override
    public void end() {
        for (Player player : field.getPlayers()) {
            player.sendTitle("", ChatColor.GREEN + "Walls are no longer frozen!", 0, 20, 10);
            player.playSound(player, Sound.BLOCK_LAVA_EXTINGUISH, 0.5F, 1);
            player.setFreezeTicks(0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        for (Player player : field.getPlayers()) {
            player.setFreezeTicks(ticksRemaining);
            // todo ground movement speed is impeded for the time being - should fix that
            // todo add particle effects to the walls to show that they're frozen
        }
    }

    @Override
    public String actionBarOverride() {
        return ChatColor.AQUA + "" + ChatColor.BOLD + "Frozen for " + ticksRemaining/20 + " seconds";
    }

}
