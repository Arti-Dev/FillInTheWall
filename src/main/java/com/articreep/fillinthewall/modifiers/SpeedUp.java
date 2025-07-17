package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.game.Wall;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;

public class SpeedUp extends ModifierEvent {
    public SpeedUp() {
        modifyWalls = true;
    }


    @Override
    public ModifierEvent copy() {
        return new SpeedUp();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_ARROW_SHOOT, 1, 2);
    }

    @Override
    public void playDeactivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_ARROW_SHOOT, 1, 2);
    }

    @Override
    public void activate() {
        super.activate();
        field.sendTitleToPlayers(miniMessage.deserialize("<color:#D3D3D3>Speed up!"),
                Component.text("Walls move a lot quicker!"), 0, 40, 10);
    }

    @Override
    public void end() {
        super.end();
        field.sendTitleToPlayers(Component.empty(), Component.text("Slowing back down..."), 0, 20, 10);
    }

    @Override
    public void modifyWall(Wall wall) {
        wall.setTimeRemaining((int) (wall.getTimeRemaining() * (1/4f)));
    }
}
