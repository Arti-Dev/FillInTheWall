package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.PlayingField;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class Freeze extends ModifierEvent {
    /** Should only be used to obtain information about this event */
    public static final Freeze singletonInstance = new Freeze(null, 0);

    public Freeze(PlayingField field, int ticks) {
        super(field, ticks);
        wallFreeze = true;
        timeFreeze = true;
        meterPercentRequired = 0.5;
    }

    @Override
    public void activate() {
        super.activate();
        for (Player player : field.getPlayers()) {
            player.sendTitle(ChatColor.AQUA + "FREEZE!", ChatColor.DARK_AQUA + "Walls are temporarily frozen!", 0, 40, 10);
            player.playSound(player, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5F, 1);
        }
        field.getQueue().correctAllWalls();
    }

    @Override
    public void end() {
        super.end();
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
