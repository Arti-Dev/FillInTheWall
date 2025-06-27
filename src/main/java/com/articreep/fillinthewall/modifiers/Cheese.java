package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.game.Wall;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;

public class Cheese extends ModifierEvent {

    public static final Material cheeseMaterial = Material.SPONGE;
    @Override
    public ModifierEvent copy() {
        return new Cheese();
    }

    @Override
    public void playActivateSound() {
        field.playSoundToPlayers(Sound.ENTITY_SLIME_SQUISH, 1, 1);
    }

    @Override
    public void playDeactivateSound() {

    }

    @Override
    public void activate() {
        super.activate();
        for (int i = 0; i < 5; i++) {
            Wall wall = new Wall(field.getLength(), field.getHeight());
            wall.insertRandomNewHoles(6, 0);
            wall.setMaterial(Material.SPONGE);
            field.getScorer().addGarbageToQueue(wall);
        }

        field.sendTitleToPlayers(miniMessage.deserialize("<yellow>Cheese"),
                Component.text("Extra walls have formed..."), 0, 40, 10);
        end();

    }

    @Override
    public void end() {
        super.end();
    }
}
