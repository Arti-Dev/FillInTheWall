package com.articreep.fillinthewall.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;

public enum DisplayType {
    NONE(""),
    SCORE("<green>Score: %s"),
    ACCURACY("<dark_red>Accuracy: %s"),
    SPEED("<white>%s blocks/sec"),
    PERFECT_WALLS("<gold>Perfect Walls: %s%s"),
    TIME("<aqua>Time: %s"),
    LEVEL("<dark_aqua>Level %s"),
    POSITION("<yellow>Position: %s\n%s"),
    NAME("%s"),
    GAMEMODE("Playing %s"),
    EVENTS("<gray>Events: %s%s");

    final String text;
    DisplayType(String text) {
        this.text = text;
    }

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Component getFormattedText(Object arg) {
        String formatted = String.format(text, arg);
        return miniMessage.deserialize(formatted);
    }

    public Component getFormattedText(ArrayList<?> args) {
        String formatted = String.format(text, args.toArray());
        return miniMessage.deserialize(formatted);
    }
}
