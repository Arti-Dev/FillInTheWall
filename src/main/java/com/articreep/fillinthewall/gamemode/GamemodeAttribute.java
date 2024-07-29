package com.articreep.fillinthewall.gamemode;

import com.articreep.fillinthewall.display.DisplayType;
import com.articreep.fillinthewall.modifiers.ModifierEvent;

public enum GamemodeAttribute {
    // todo I'm not going to bother with enforcing types for now, but the types of these attributes are listed here
    TIME_LIMIT(Integer.class, 0),
    FINALS_TIME_LIMIT(Integer.class, 20*120),
    DO_LEVELS(Boolean.class, false),
    WALL_TIME_DECREASE_AMOUNT(Integer.class, 20),
    CONSISTENT_HOLE_COUNT(Boolean.class, true),
    RANDOM_HOLE_COUNT(Integer.class, 2),
    CONNECTED_HOLE_COUNT(Integer.class, 4),
    STARTING_WALL_ACTIVE_TIME(Integer.class, 160),
    METER_MAX(Integer.class, 10),
    DISPLAY_SLOT_0(DisplayType.class, DisplayType.TIME),
    DISPLAY_SLOT_1(DisplayType.class, DisplayType.PERFECT_WALLS),
    DISPLAY_SLOT_2(DisplayType.class, DisplayType.LEVEL),
    DISPLAY_SLOT_3(DisplayType.class, DisplayType.SCORE),
    SINGLEPLAYER(Boolean.class, false),
    MULTIPLAYER(Boolean.class, false),
    AUTOMATIC_METER(Boolean.class, false),
    DO_GARBAGE_WALLS(Boolean.class, false),
    DO_GARBAGE_ATTACK(Boolean.class, false),
    GARBAGE_WALL_HARDNESS(Integer.class, 3),
    DO_CLEARING_MODES(Boolean.class, false),
  /**
     * Amount of modifier events that can be activated until the game ends.
     */
    MODIFIER_EVENT_CAP(Integer.class, -1),
    HIGHLIGHT_INCORRECT_BLOCKS(Boolean.class, false),
    INFINITE_BLOCK_REACH(Boolean.class, false),
    ABILITY_EVENT(ModifierEvent.Type.class, null),
    MULTI_EVENT_0(ModifierEvent.Type.class, null),
    MULTI_EVENT_1(ModifierEvent.Type.class, null),
    SINGULAR_EVENT(ModifierEvent.Type.class, null);

    private final Class<?> type;
    private final Object defaultValue;
    GamemodeAttribute(Class<?> type, Object defaultValue) {
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public Class<?> getType() {
        return type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }
}
