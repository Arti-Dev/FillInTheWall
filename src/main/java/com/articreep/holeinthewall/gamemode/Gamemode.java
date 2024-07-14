package com.articreep.holeinthewall.gamemode;

import com.articreep.holeinthewall.display.DisplayType;
import com.articreep.holeinthewall.modifiers.Freeze;
import com.articreep.holeinthewall.modifiers.ModifierEvent;
import com.articreep.holeinthewall.modifiers.Rush;
import com.articreep.holeinthewall.modifiers.Tutorial;
import org.bukkit.ChatColor;

public enum Gamemode {

    INFINITE(ChatColor.LIGHT_PURPLE + "Infinite", ChatColor.GRAY + "Step off the playing field to stop playing."),
    TUTORIAL("Tutorial", ChatColor.GRAY + "Learn how to play!"),
    SCORE_ATTACK(ChatColor.GOLD + "Score Attack", ChatColor.GRAY + "Score as much as you can in 2 minutes!"),
    RUSH_SCORE_ATTACK(ChatColor.RED + "Rush Score Attack", ChatColor.GRAY + "Use Rush Attacks to score as much as you can!"),
    MULTIPLAYER_SCORE_ATTACK(ChatColor.AQUA + "Multiplayer Score Attack", ChatColor.GRAY + "Hypixel-style game"),
    MARATHON(ChatColor.GRAY + "Marathon", ChatColor.GRAY + "Survive as long as you can!"),
    VERSUS(ChatColor.BLUE + "2-player Versus", ChatColor.GRAY + "Experimental versus system with garbage walls");

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
        INFINITE.settings.setEventClass(Rush.class);

        SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20*120);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DO_LEVELS, true);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.LEVEL);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        SCORE_ATTACK.addAttribute(GamemodeAttribute.WALL_TIME_DECREASE_AMOUNT, 20);

        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DO_LEVELS, false);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.MODIFIER_EVENT_CAP, 5);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 1);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 0);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.METER_MAX, 1);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.EVENTS);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.AUTOMATIC_METER, true);
        RUSH_SCORE_ATTACK.settings.setEventClass(Rush.class);

        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20*120);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 3);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 0);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 160);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.METER_MAX, 5);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.MULTIPLAYER, true);
        MULTIPLAYER_SCORE_ATTACK.settings.setEventClass(Freeze.class);

        TUTORIAL.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 20*30);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.NONE);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        TUTORIAL.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        TUTORIAL.settings.setEventClass(Tutorial.class);

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
        VERSUS.addAttribute(GamemodeAttribute.DO_CLEARING_MODES, true);



    }

    final String title;
    final String description;
    final GamemodeSettings settings = new GamemodeSettings();
    Gamemode(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    private void addAttribute(GamemodeAttribute attribute, Object value) {
        settings.setAttribute(attribute, value);
    }

    public GamemodeSettings getDefaultSettings() {
        return settings;
    }
}
