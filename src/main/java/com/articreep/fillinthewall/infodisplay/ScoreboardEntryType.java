package com.articreep.fillinthewall.infodisplay;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;

public enum ScoreboardEntryType {
    SCORE("<yellow>Score: %s"),
    STAGE( "%s"),
    TIME("<green>Time Left: %s"),
    POSITION("Position: No. %s"),
    POINTS_BEHIND("<gray>%s points behind No. %s"),
    PLAYERS("<dark_gray>%s-board game"),
    EMPTY(""),
    START_TIMER("<green>Game starting in %s"),
    PREGAME_PLAYERCOUNT("<yellow>Players: %s");

    final String text;
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();
    ScoreboardEntryType(String text) {
        this.text = text;
    }

    public Component getRawText() {
        return miniMessage.deserialize(text);
    }

    public Component getFormattedText(Component arg) {
        String serialized = miniMessage.serialize(arg);
        return miniMessage.deserialize(String.format(text, serialized));
    }

    public Component getFormattedText(Component[] args) {
        // Convert to a string array
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = miniMessage.serialize(args[i]);
        }
        return miniMessage.deserialize(String.format(text, (Object[]) stringArgs));
    }
}
