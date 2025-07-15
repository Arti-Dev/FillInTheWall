package com.articreep.fillinthewall.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;

public enum Judgement {
    // It's pretty important that these are ordered from best to worst.
    PERFECT(1, "<gradient:gold:yellow><bold>PERFECT!", NamedTextColor.GOLD, Sound.ENTITY_PLAYER_LEVELUP, Material.GLOWSTONE),
    COOL(0.5, "<green>Cool!", NamedTextColor.GREEN, Sound.BLOCK_NOTE_BLOCK_PLING, Material.LIME_CONCRETE),
    MISS(0, "<red>Miss..", NamedTextColor.RED, Sound.BLOCK_ANVIL_LAND, Material.RED_CONCRETE);

    private final double percent;
    private final String text;
    private final TextColor color;
    private final Sound sound;
    private final Material border;

    Judgement(double percent, String text, TextColor color, Sound sound, Material border) {
        this.percent = percent;
        this.text = text;
        this.color = color;
        this.sound = sound;
        this.border = border;
    }

    final MiniMessage miniMessage = MiniMessage.miniMessage();

    public double getPercent() {
        return percent;
    }

    public Component getFormattedText() {
        return miniMessage.deserialize(text);
    }

    public TextColor getColor() {
        return color;
    }

    public Sound getSound() {
        return sound;
    }

    public Material getBorder() {
        return border;
    }
}
