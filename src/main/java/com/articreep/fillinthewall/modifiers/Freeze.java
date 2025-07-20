package com.articreep.fillinthewall.modifiers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class Freeze extends ModifierEvent {

    public Freeze() {
        super();
        wallFreeze = true;
        timeFreeze = true;
        shelveEvent = true;
    }

    @Override
    public void activate() {
        super.activate();
        field.sendTitleToPlayers(miniMessage.deserialize("<aqua>FREEZE!"),
                Component.text("Walls and gimmicks are temporarily frozen!"), 0, 40, 10);
        field.getQueue().correctAllWalls();
    }

    @Override
    public void end() {
        super.end();
        field.sendTitleToPlayers(Component.empty(), miniMessage.deserialize("<green>Walls are no longer frozen!"), 0, 20, 10);
        for (Player player : field.getPlayers()) {
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
        if (ticksRemaining <= 0) {
            end();
        }
    }

    @Override
    public Component actionBarOverride() {
        return MiniMessage.miniMessage().deserialize("<aqua><bold>Frozen for " + ticksRemaining / 20 + " seconds");
    }

    public Freeze copy() {
        return new Freeze();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5F, 1);
    }

    @Override
    public void playDeactivateSound() {
        field.playSoundToPlayers(Sound.BLOCK_LAVA_EXTINGUISH, 0.5F, 1);
    }

}
