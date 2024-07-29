package com.articreep.fillinthewall.gamemode;

import com.articreep.fillinthewall.display.DisplayType;
import com.articreep.fillinthewall.modifiers.ModifierEvent;

import java.util.HashMap;

// Flexible class that allows overriding default settings
public class GamemodeSettings {
    private final HashMap<GamemodeAttribute, Object> settings = new HashMap<>();

    public GamemodeSettings() {
        for (GamemodeAttribute attribute : GamemodeAttribute.values()) {
            setAttribute(attribute, attribute.getDefaultValue());
        }
    }

    public void setAttribute(GamemodeAttribute attribute, Object value) {
        // Try casting value
        try {
            attribute.getType().cast(value);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Value " + value + " is not of type " + attribute.getType().getSimpleName());
        }
        settings.put(attribute, value);
    }

    public Object getAttribute(GamemodeAttribute attribute) {
        if (!settings.containsKey(attribute)) return attribute.getDefaultValue();
        return settings.get(attribute);
    }

    public int getIntAttribute(GamemodeAttribute attribute) {
        return (int) getAttribute(attribute);
    }

    public boolean getBooleanAttribute(GamemodeAttribute attribute) {
        return (boolean) getAttribute(attribute);
    }

    public ModifierEvent.Type getModifierEventTypeAttribute(GamemodeAttribute attribute) {
        return (ModifierEvent.Type) getAttribute(attribute);
    }

    public DisplayType getDisplayTypeAttribute(GamemodeAttribute attribute) {
        return (DisplayType) getAttribute(attribute);
    }


    public GamemodeSettings copy() {
        GamemodeSettings clone = new GamemodeSettings();
        for (GamemodeAttribute attribute : settings.keySet()) {
            clone.setAttribute(attribute, settings.get(attribute));
        }
        return clone;
    }
}
