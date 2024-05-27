package com.articreep.holeinthewall.display;

import org.bukkit.ChatColor;

public enum DisplayType {
    SCORE(ChatColor.GREEN + "Score: %s"),
    ACCURACY(ChatColor.DARK_RED + "Accuracy: %s"),
    SPEED(ChatColor.WHITE + "Speed: %s"),
    PERFECT_WALLS(ChatColor.GOLD + "Perfect Walls: %s"),
    TIME(ChatColor.AQUA + "Time: %s"),
    LEVEL(ChatColor.DARK_AQUA + "Level %s"),
    NAME("%s"),
    GAMEMODE("Playing %s");

    final String text;
    DisplayType(String text) {
        this.text = text;
    }

    public String getRawText() {
        return text;
    }

    public String getFormattedText(Object... args) {
        return String.format(text, args);
    }
}
