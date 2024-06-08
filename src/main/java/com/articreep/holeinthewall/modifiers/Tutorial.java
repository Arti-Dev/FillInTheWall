package com.articreep.holeinthewall.modifiers;

import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.Wall;
import org.bukkit.ChatColor;
import org.bukkit.Sound;

public class Tutorial extends ModifierEvent {
    public Tutorial(PlayingField field, int ticks) {
        super(field, ticks);
        overrideGeneration = true;
        allowMultipleWalls = true;
    }

    @Override
    public void activate() {
        field.sendTitleToPlayers(ChatColor.BOLD + "Tutorial", "Let's learn how to play!", 10, 40, 20);
        field.playSoundToPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1);
    }

    @Override
    public void end() {
        field.sendTitleToPlayers("", "Good luck!", 10, 40, 20);
    }

    @Override
    public void score(Wall wall) {

    }

    @Override
    public String actionBarOverride() {
        return ChatColor.BOLD + "Press F to insta-send walls";
    }

    @Override
    public void tick() {

    }
}
