package com.articreep.holeinthewall.display;

import org.bukkit.ChatColor;

public enum ScoreboardEntryType {
    SCORE(ChatColor.YELLOW + "Score: %s"),
    STAGE( "%s"),
    TIME(ChatColor.AQUA + "Time Left: %s"),
    POSITION("Position: %s, %s"),
    TAB_INFO(ChatColor.GRAY + "Press TAB for leaderboard"),
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
