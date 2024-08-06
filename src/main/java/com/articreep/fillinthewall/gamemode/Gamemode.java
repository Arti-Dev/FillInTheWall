package com.articreep.fillinthewall.gamemode;

import com.articreep.fillinthewall.display.DisplayType;
import com.articreep.fillinthewall.modifiers.*;
import net.md_5.bungee.api.ChatColor;

public enum Gamemode {

    TUTORIAL("Tutorial", ChatColor.GRAY + "Learn how to play!"),
    INFINITE(ChatColor.LIGHT_PURPLE + "Infinite", ChatColor.GRAY + "Step off the playing field to stop playing."),
    SCORE_ATTACK(ChatColor.GOLD + "Score Attack", ChatColor.GRAY + "Score as much as you can in 2 minutes!"),
    RUSH_SCORE_ATTACK(ChatColor.RED + "Rush Score Attack", ChatColor.GRAY + "Use Rush Attacks to score as much as you can!"),
    SPRINT(ChatColor.AQUA + "Sprint", ChatColor.GRAY + "Perfect clear 20 walls as fast as you can!"),
    MULTIPLAYER_SCORE_ATTACK(ChatColor.AQUA + "Multiplayer Score Attack", ChatColor.GRAY + "Hypixel-style game"),
    MARATHON(ChatColor.GRAY + "Marathon", ChatColor.GRAY + "Survive as long as you can!"),
    VERSUS(ChatColor.BLUE + "2-player Versus", ChatColor.GRAY + "Experimental versus system with garbage walls"),
    CUSTOM(ChatColor.GREEN + "Custom Walls", ChatColor.GRAY + "Load a custom wall pack"),
    MEGA(ChatColor.DARK_AQUA + "Mega", ChatColor.GRAY + "Work with others to fill 200 holes!"),
    ;

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
        INFINITE.addAttribute(GamemodeAttribute.ABILITY_EVENT, ModifierEvent.Type.RUSH);

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
        RUSH_SCORE_ATTACK.addAttribute(GamemodeAttribute.ABILITY_EVENT, ModifierEvent.Type.RUSH);

        SPRINT.addAttribute(GamemodeAttribute.CONSISTENT_HOLE_COUNT, true);
        SPRINT.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 1);
        SPRINT.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 4);
        SPRINT.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 160);
        SPRINT.addAttribute(GamemodeAttribute.METER_MAX, 0);
        SPRINT.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        SPRINT.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        SPRINT.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        SPRINT.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.NONE);
        SPRINT.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        SPRINT.addAttribute(GamemodeAttribute.PERFECT_WALL_CAP, 20);
        SPRINT.addAttribute(GamemodeAttribute.SCORE_BY_TIME, true);
        SPRINT.addAttribute(GamemodeAttribute.REFUSE_IMPERFECT_WALLS, true);

        MEGA.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        MEGA.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 20*60*5);
        MEGA.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 50);
        MEGA.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 150);
        MEGA.addAttribute(GamemodeAttribute.METER_MAX, 0);
        MEGA.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        MEGA.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.NONE);
        MEGA.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        MEGA.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.NONE);
        MEGA.addAttribute(GamemodeAttribute.PERFECT_WALL_CAP, 1);
        MEGA.addAttribute(GamemodeAttribute.SCORE_BY_TIME, true);
        MEGA.addAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS, true);
        MEGA.addAttribute(GamemodeAttribute.REFUSE_IMPERFECT_WALLS, true);
        MEGA.addAttribute(GamemodeAttribute.TEAM_EFFORT, true);

        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.TIME_LIMIT, 20*150);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.RANDOM_HOLE_COUNT, 3);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.CONNECTED_HOLE_COUNT, 0);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 160);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.METER_MAX, 5);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.SPEED);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.WALL_TIME_DECREASE_AMOUNT, 10);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.MULTIPLAYER, true);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.ABILITY_EVENT, ModifierEvent.Type.FREEZE);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.MULTI_EVENT_0, ModifierEvent.Type.RANDOM);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.MULTI_EVENT_1, ModifierEvent.Type.RANDOM);
        MULTIPLAYER_SCORE_ATTACK.addAttribute(GamemodeAttribute.FINALS_TIME_LIMIT, 20*150);

        TUTORIAL.addAttribute(GamemodeAttribute.STARTING_WALL_ACTIVE_TIME, 20*30);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_0, DisplayType.TIME);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_1, DisplayType.PERFECT_WALLS);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_2, DisplayType.NONE);
        TUTORIAL.addAttribute(GamemodeAttribute.DISPLAY_SLOT_3, DisplayType.SCORE);
        TUTORIAL.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);
        TUTORIAL.addAttribute(GamemodeAttribute.ABILITY_EVENT, ModifierEvent.Type.TUTORIAL);
        TUTORIAL.addAttribute(GamemodeAttribute.HIGHLIGHT_INCORRECT_BLOCKS, true);
        TUTORIAL.addAttribute(GamemodeAttribute.SINGULAR_EVENT, ModifierEvent.Type.TUTORIAL);

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

        CUSTOM.addAttribute(GamemodeAttribute.SINGLEPLAYER, true);


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
        return settings.copy();
    }
}
