package com.articreep.holeinthewall.display;

import org.bukkit.ChatColor;

public enum ScoreboardEntryType {
    SCORE(ChatColor.YELLOW + "Score: %s"),
    STAGE( "%s"),
    TIME(ChatColor.GREEN + "Time Left: %s"),
    POSITION("Position: No. %s"),
    POINTS_BEHIND(ChatColor.GRAY + "%s points behind No. %s"),
    PLAYERS(ChatColor.DARK_GRAY + "%s-board game"),
    EMPTY("");

    final String text;
    ScoreboardEntryType(String text) {
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
