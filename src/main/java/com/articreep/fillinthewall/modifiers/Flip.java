package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.Wall;
import org.bukkit.Sound;

public class Flip extends ModifierEvent {
    public Flip() {
        super();
        modifyWalls = true;
    }

    @Override
    public void modifyWall(Wall wall) {
        wall.setDoFlip(true);
    }

    @Override
    public void activate() {
        super.activate();
        for (Wall wall : field.getQueue().getActiveWalls()) {
            wall.flip();
        }
    }

    @Override
    public void end() {
        super.end();
        field.sendTitleToPlayers("", "Walls don't flip out anymore!", 0, 20, 10);
        for (Wall wall : field.getQueue().getActiveWalls()) {
            wall.setDoFlip(false);
        }
    }

    public Flip copy() {
        return new Flip();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_BREEZE_WHIRL, 1);
    }

    @Override
    public void playDeactivateSound() {

    }
}
