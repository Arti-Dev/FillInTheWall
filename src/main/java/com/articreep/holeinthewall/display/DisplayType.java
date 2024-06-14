package com.articreep.holeinthewall.display;

import org.bukkit.ChatColor;

public enum DisplayType {
    NONE(""),
    SCORE(ChatColor.GREEN + "Score: %s"),
    ACCURACY(ChatColor.DARK_RED + "Accuracy: %s"),
    SPEED(ChatColor.WHITE + "%s blocks/sec"),
    PERFECT_WALLS(ChatColor.GOLD + "Perfect Walls: %s"),
    TIME(ChatColor.AQUA + "Time: %s"),
    LEVEL(ChatColor.DARK_AQUA + "Level %s"),
    POSITION(ChatColor.YELLOW + "Position: %s\n%s"),
    NAME("%s"),
    GAMEMODE("Playing %s");

    final String text;
    DisplayType(String text) {
        this.text = text;
    }

    public String getRawText() {
        return text;
    }

    public String getFormattedText(Object arg) {
        return String.format(text, arg);
    }

    public String getFormattedText(Object[] args) {
        return String.format(text, args);
    }
}
