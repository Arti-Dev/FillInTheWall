package com.articreep.holeinthewall;

import com.articreep.holeinthewall.display.DisplayType;
import com.articreep.holeinthewall.modifiers.ModifierEvent;
import com.articreep.holeinthewall.modifiers.Rush;
import org.bukkit.ChatColor;

import java.util.HashMap;

public enum Gamemode {

    INFINITE(ChatColor.LIGHT_PURPLE + "Infinite", ChatColor.GRAY + "Step off the playing field to stop playing.", new HashMap<>(), Rush.class),
    SCORE_ATTACK(ChatColor.GOLD + "Score Attack", ChatColor.GRAY + "Score as much as you can in 2 minutes!", new HashMap<>(),null),
    RAPID_SCORE_ATTACK(ChatColor.RED + "Rapid Score Attack", ChatColor.GRAY + "Rapid fire version of Score Attack", new HashMap<>(),Rush.class),
    MULTIPLAYER_SCORE_ATTACK(ChatColor.AQUA + "Multiplayer Score Attack", ChatColor.GRAY + "Hypixel-style game", new HashMap<>(),null);

    static {
        INFINITE.addAttribute(GamemodeAttribute.CONSISTENT_HOLE_COUNT, false);
        INFINITE.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 2);
        INFINITE.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 4);
        INFINITE.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 160);
        INFINITE.addAttribute(GamemodeAttribute.METER_MAX, 10);
        INFINITE.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        INFINITE.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        INFINITE.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.NONE);
        INFINITE.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        INFINITE.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);

        SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20*120);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DO_LEVELS, true);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.LEVEL);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);

        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20*60);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DO_LEVELS, false);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 1);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 2);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.NONE);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);

        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20*120);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 2);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 4);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 160);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.METER_MAX, 10);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.NONE);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.MULTIPLAYER, true);


    }

    final String title;
    final String description;
    final Class<? extends ModifierEvent> modifier;
    final HashMap<GamemodeAttribute, Object> attributes;
    Gamemode(String title, String description, HashMap<GamemodeAttribute, Object> map,
             Class<? extends ModifierEvent> modifier) {
        this.title = title;
        this.description = description;
        this.modifier = modifier;
        this.attributes = map;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    private void addAttribute(GamemodeAttribute attribute, Object value) {
        attributes.put(attribute, value);
    }

    public Class<? extends ModifierEvent> getModifier() {
        return modifier;
    }

    public Object getAttribute(GamemodeAttribute attribute) {
        if (!hasAttribute(attribute)) return attribute.getDefaultValue();
        return attributes.get(attribute);
    }

    public boolean hasAttribute(GamemodeAttribute attribute) {
        return attributes.containsKey(attribute);
    }
}
