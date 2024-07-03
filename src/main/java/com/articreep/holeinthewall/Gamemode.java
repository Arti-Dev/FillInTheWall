package com.articreep.holeinthewall;

import com.articreep.holeinthewall.display.DisplayType;
import com.articreep.holeinthewall.modifiers.Freeze;
import com.articreep.holeinthewall.modifiers.ModifierEvent;
import com.articreep.holeinthewall.modifiers.Rush;
import com.articreep.holeinthewall.modifiers.Tutorial;
import org.bukkit.ChatColor;

import java.util.HashMap;

public enum Gamemode {

    INFINITE(ChatColor.LIGHT_PURPLE + "Infinite", ChatColor.GRAY + "Step off the playing field to stop playing.", Rush.class),
    TUTORIAL("Tutorial", ChatColor.GRAY + "Learn how to play!", Tutorial.class),
    SCORE_ATTACK(ChatColor.GOLD + "Score Attack", ChatColor.GRAY + "Score as much as you can in 2 minutes!",null),
    RAPID_SCORE_ATTACK(ChatColor.RED + "Rapid Score Attack", ChatColor.GRAY + "Rapid fire version of Score Attack", Rush.class),
    MULTIPLAYER_SCORE_ATTACK(ChatColor.AQUA + "Multiplayer Score Attack", ChatColor.GRAY + "Hypixel-style game", Freeze.class),
    MARATHON(ChatColor.GRAY + "Marathon", ChatColor.GRAY + "Survive as long as you can!", null),
    VERSUS(ChatColor.BLUE + "2-player Versus", ChatColor.GRAY + "Experimental versus system with garbage walls", null);

    static {
        INFINITE.addAttribute(GamemodeAttribute.CONSISTENT_HOLE_COUNT, false);
        INFINITE.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 2);
        INFINITE.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 4);
        INFINITE.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 160);
        INFINITE.addAttribute(GamemodeAttribute.METER_MAX, 10);
        INFINITE.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        INFINITE.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        INFINITE.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        INFINITE.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        INFINITE.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        INFINITE.addAttribute(GamemodeAttribute.AUTOMATIC_METER, true);

        SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20*120);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DO_LEVELS, true);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.LEVEL);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.WALL_TIME_DECREASE_AMOUNT, 20);

        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20*60);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DO_LEVELS, false);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 1);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 2);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.METER_MAX, 5);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        RAPID_SCORE_ATTACK.addAttribute(GamemodeAttribute.AUTOMATIC_METER, true);

        // todo this doesn't affect the game length at all right now
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20*120);
        // todo These attributes probably shouldn't be here, because every playing field will modify the generator which is bad
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 3);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 0);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 160);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.METER_MAX, 5);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.MULTIPLAYER, true);

        TUTORIAL.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 20*30);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.NONE);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        TUTORIAL.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);

        MARATHON.addAttribute(GamemodeAttribute.DO_LEVELS, true);
        MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.LEVEL);
        MARATHON.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        MARATHON.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        MARATHON.addAttribute(GamemodeAttribute.DO_GARBAGE_WALLS, true);
        MARATHON.addAttribute(GamemodeAttribute.WALL_TIME_DECREASE_AMOUNT, 14);

        VERSUS.addAttribute(GamemodeAttribute.CONSISTENT_HOLE_COUNT, false);
        VERSUS.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 2000);
        VERSUS.addAttribute(GamemodeAttribute.DO_GARBAGE_WALLS, true);
        VERSUS.addAttribute(GamemodeAttribute.DO_GARBAGE_ATTACK, true);
        VERSUS.addAttribute(GamemodeAttribute.MULTIPLAYER, true);
        VERSUS.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 2);
        VERSUS.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 6);
        VERSUS.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        VERSUS.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        VERSUS.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        VERSUS.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        VERSUS.addAttribute(GamemodeAttribute.GARBAGE_WALL_HARDNESS, 2);



    }

    final String title;
    final String description;
    final Class<? extends ModifierEvent> modifier;
    final HashMap<GamemodeAttribute, Object> attributes;
    Gamemode(String title, String description,
             Class<? extends ModifierEvent> modifier) {
        this.title = title;
        this.description = description;
        this.modifier = modifier;
        this.attributes = new HashMap<>();
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
