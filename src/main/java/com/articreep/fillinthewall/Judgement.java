package com.articreep.fillinthewall;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;

public enum Judgement {
    // It's pretty important that these are ordered from best to worst.
    PERFECT(1, ChatColor.BOLD + "PERFECT!", ChatColor.GOLD, Sound.ENTITY_PLAYER_LEVELUP, Material.GLOWSTONE),
    COOL(0.5, "Cool!", ChatColor.GREEN, Sound.BLOCK_NOTE_BLOCK_PLING, Material.LIME_CONCRETE),
    MISS(0, "Miss..", ChatColor.RED, Sound.BLOCK_ANVIL_LAND, Material.RED_CONCRETE);

    private final double percent;
    private final String text;
    private final ChatColor color;
    private final Sound sound;
    private final Material border;

    Judgement(double percent, String text, ChatColor color, Sound sound, Material border) {
        this.percent = percent;
        this.text = text;
        this.color = color;
        this.sound = sound;
        this.border = border;
    }

    public double getPercent() {
        return percent;
    }

    public String getText() {
        return text;
    }

    public ChatColor getColor() {
        return color;
    }

    public Sound getSound() {
        return sound;
    }

    public Material getBorder() {
        return border;
    }
}
