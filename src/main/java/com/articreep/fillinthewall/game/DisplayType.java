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
    EVENTS("<gray>Events: %s%s"),
    SCORE_TO_NEXT_LEVEL("<gray>Next level at %s points");

    final String text;
    DisplayType(String text) {
        this.text = text;
    }

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Component getFormattedText(Component arg) {
        String serialized = miniMessage.serialize(arg);
        String formatted = String.format(text, serialized);
        return miniMessage.deserialize(formatted);
    }

    public Component getFormattedText(ArrayList<Component> args) {
        // Convert to a string array
        String[] stringArgs = new String[args.size()];
        for (int i = 0; i < args.size(); i++) {
            stringArgs[i] = miniMessage.serialize(args.get(i));
        }
        String formatted = String.format(text, (Object[]) stringArgs);
        return miniMessage.deserialize(formatted);
    }
}
