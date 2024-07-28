package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;

/** Represents an event that affects the playing field and/or queue. */
public abstract class ModifierEvent {
    /* Settings. Child classes should change these as fit */
    // todo maybe I'll make a json file for these, because doing this in java is hard
    /**
     * Completely override the scorer's scoring method.
     */
    public boolean overrideCompleteScoring = false;
    // Override only some parts of the scoring method.
    public boolean overrideScoreCalculation = false;
    public boolean overridePercentCalculation = false;
    public boolean overrideBonusCalculation = false;
    public boolean overrideScoreTitle = false;

    public boolean overrideCorrectBlocksVisual = false;
    public boolean overrideGeneration = false;
    public boolean allowMultipleWalls = false;
    public int clearDelay;
    public boolean timeFreeze = false;
    public boolean wallFreeze = false;
    public boolean fillFieldAfterSubmission = false;
    public boolean modifyWalls = false;

    protected double meterPercentRequired = 1;
    protected WallQueue queue;
    protected int ticksRemaining;
    protected PlayingField field;

    protected boolean active = false;


    protected ModifierEvent(PlayingField field, int ticks) {
        if (field == null) return;
        this.field = field;
        this.queue = field.getQueue();
        clearDelay = field.getClearDelay();

        this.ticksRemaining = ticks;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public void tick() {
        ticksRemaining--;
    }

    /** If returns null, the default action bar will be used. */
    public String actionBarOverride() {
        return null;
    }

    public void activate() {
        if (field == null) return;
        field.setEvent(this);
        active = true;
    }

    public void end() {
        active = false;
        for (Player player : field.getPlayers()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null || !item.hasItemMeta()) continue;
                if (item.getItemMeta().getPersistentDataContainer().has(PlayingField.variableKey)) {
                    inventory.setItem(i, PlayingField.variableItem());
                }
            }
        }
        if (field == null) return;
        field.setEvent(null);
    }

    /**
     * If the overrideCompleteScoring variable is true, this method will be called instead of PlayingFieldScorer#score
     * @param wall Wall to be scored
     */
    public void score(Wall wall) {
        field.getScorer().scoreWall(wall, field);
    }

    /**
     * This is called whenever a wall is scored if the event is active.
     * @param wall Wall being scored
     */
    public void onWallScore(Wall wall) {
        // override
    }

    /**
     * If the overrideScoreCalculation variable is true, this method will be called instead of PlayingFieldScorer#calculateScore
     * @return The score to award the player
     */
    public int calculateScore(Wall wall) {
        return field.getScorer().calculateScore(wall, field);
    }

    public double calculatePercent(Wall wall) {
        return field.getScorer().calculatePercent(wall, field);
    }

    public HashMap<PlayingFieldScorer.BonusType, Integer> evaluateBonus(double percent, Wall wall) {
        return field.getScorer().evaluateBonus(percent);
    }

    public void displayScoreTitle(Judgement judgement, int score, HashMap<PlayingFieldScorer.BonusType, Integer> bonus) {
        field.getScorer().displayScoreTitle(judgement, score, bonus);
    }

    public void correctBlocksVisual(Wall wall) {
        field.correctBlocksVisual(wall);
    }

    public void modifyWall(Wall wall) {
        // override
    }

    public double getMeterPercentRequired() {
        return meterPercentRequired;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Adds a temporary item to the player's inventory.
     * The item should have an entry in the persistent data container with the key PlayingField.variableKey
     * @param itemToAdd item to add
     */
    protected void addTemporaryItemToPlayers(ItemStack itemToAdd) {
        for (Player player : field.getPlayers()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents();
            boolean replaced = false;
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null || !item.hasItemMeta()) continue;
                if (item.getItemMeta().getPersistentDataContainer().has(PlayingField.variableKey)) {
                    replaced = true;
                    inventory.setItem(i, itemToAdd);
                }
            }

            if (!replaced) {
                inventory.addItem(itemToAdd);
            }
        }
    }
}
