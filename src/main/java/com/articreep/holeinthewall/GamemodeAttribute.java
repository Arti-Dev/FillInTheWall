package com.articreep.holeinthewall;

import com.articreep.holeinthewall.display.DisplayType;

public enum GamemodeAttribute {
    // todo I'm not going to bother with enforcing types for now, but the types of these attributes are listed here
    TIME_LIMIT(int.class, 0),
    DO_LEVELS(boolean.class, false),
    CONSISTENT_HOLE_COUNT(boolean.class, true),
    RANDOM_HOLE_COUNT(int.class, 2),
    CONNECTED_HOLE_COUNT(int.class, 4),
    STARTING_WALL_ACTIVE_TIME(int.class, 160),
    METER_MAX(int.class, 10),
    DISPLAY_SLOT_0(DisplayType.class, DisplayType.TIME),
    DISPLAY_SLOT_1(DisplayType.class, DisplayType.PERFECT_WALLS),
    DISPLAY_SLOT_2(DisplayType.class, DisplayType.LEVEL),
    DISPLAY_SLOT_3(DisplayType.class, DisplayType.SCORE),
    SINGLEPLAYER(boolean.class, false),
    MULTIPLAYER(boolean.class, false),
    AUTOMATIC_METER(boolean.class, false);

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
