package com.articreep.holeinthewall.multiplayer;

import com.articreep.holeinthewall.PlayingField;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class Pregame {
    private World world;
    private int countdown;
    private List<PlayingField> availablePlayingFields = new ArrayList<>();

    private void start() {
        world.getPlayers();
    }
}
