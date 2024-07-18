package com.articreep.fillinthewall.display;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;

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
    GAMEMODE("Playing %s"),
    EVENTS(ChatColor.GRAY + "Events: %s%s");

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

    public String getFormattedText(ArrayList<?> args) {
        return String.format(text, args.toArray());
    }
}
