package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.PlayingField;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class Freeze extends ModifierEvent {

    public Freeze(PlayingField field) {
        super(field);
        wallFreeze = true;
        timeFreeze = true;
        meterPercentRequired = 0.5;
        shelveEvent = true;
    }

    @Override
    public void activate() {
        super.activate();
        // Reduce time based on percent filled
        ticksRemaining = (int) (200 * field.getScorer().getMeterPercentFilled());
        for (Player player : field.getPlayers()) {
            player.sendTitle(ChatColor.AQUA + "FREEZE!", "Walls and gimmicks are temporarily frozen!", 0, 40, 10);
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
            player.setFreezeTicks(Math.max(ticksRemaining, 0));
            // todo ground movement speed is impeded for the time being - should fix that
            // todo add particle effects to the walls to show that they're frozen
        }
    }

    @Override
    public String actionBarOverride() {
        return ChatColor.AQUA + "" + ChatColor.BOLD + "Frozen for " + ticksRemaining/20 + " seconds";
    }

    public Freeze copy(PlayingField newPlayingField) {
        return new Freeze(newPlayingField);
    }

}
