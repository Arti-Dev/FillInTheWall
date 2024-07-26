package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.PlayingField;
import com.articreep.fillinthewall.Wall;

public class Flip extends ModifierEvent {
    public Flip(PlayingField field, int ticks) {
        super(field, ticks);
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
}
