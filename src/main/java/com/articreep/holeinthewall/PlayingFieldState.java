package com.articreep.holeinthewall;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/** WIP wrapper class to represent a playing field state */
public class PlayingFieldState {
    Material borderMaterial;
    ChatColor titleColor;
    String title;
    int score;
    Judgement judgement;

    public PlayingFieldState(Material borderMaterial, ChatColor titleColor, String title, int score, Judgement judgement) {
        this.borderMaterial = borderMaterial;
        this.titleColor = titleColor;
        this.title = title;
        this.score = score;
        this.judgement = judgement;
    }
}
