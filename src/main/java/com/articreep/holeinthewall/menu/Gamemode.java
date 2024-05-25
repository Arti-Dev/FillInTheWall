package com.articreep.holeinthewall.menu;

import org.bukkit.ChatColor;

public enum Gamemode {

    INFINITE(ChatColor.LIGHT_PURPLE + "Infinite", ChatColor.GRAY + "Step off the playing field to stop playing."),
    SCORE_ATTACK(ChatColor.GOLD + "Score Attack", ChatColor.GRAY + "Score as much as you can in 2 minutes!"),
    RAPID_SCORE_ATTACK(ChatColor.RED + "Rapid Score Attack", ChatColor.GRAY + "Rapid fire version of Score Attack"),
    MULTIPLAYER_SCORE_ATTACK(ChatColor.AQUA + "Multiplayer Score Attack", ChatColor.GRAY + "Hypixel-style game");

    final String title;
    final String description;
    Gamemode(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
