package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.game.Wall;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;

public class Flip extends ModifierEvent {
    public Flip() {
        super();
        modifyWalls = true;
    }

    @Override
    public void modifyWall(Wall wall) {
        if (ticksRemaining <= 0) wall.setDoFlip(false);
        else wall.setDoFlip(true);
    }

    @Override
    public void activate() {
        super.activate();
        field.sendTitleToPlayers(Component.text("Flip!"), Component.text("\uD83D\uDD04\uD83D\uDD04\uD83D\uDD04"), 0, 40, 10);
        for (Wall wall : field.getQueue().getActiveWalls()) {
            wall.flip();
        }
    }

    @Override
    public void end() {
        super.end();
        field.sendTitleToPlayers(Component.empty(), Component.text("Walls don't flip out anymore!"), 0, 20, 10);
        for (Wall wall : field.getQueue().getActiveWalls()) {
            wall.setDoFlip(false);
        }
    }

    public Flip copy() {
        return new Flip();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_BREEZE_CHARGE, 1);
    }

    @Override
    public void playDeactivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_BREEZE_DEFLECT, 1);
    }
}
