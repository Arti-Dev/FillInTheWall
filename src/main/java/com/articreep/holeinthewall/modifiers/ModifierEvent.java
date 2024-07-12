package com.articreep.holeinthewall.modifiers;

import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.Wall;
import com.articreep.holeinthewall.WallQueue;

/** Represents an event that affects the playing field and/or queue. */
public abstract class ModifierEvent {
    /* Settings. Child classes should change these as fit */
    // todo maybe I'll make a json file for these, because doing this in java is hard
    public boolean overrideScoring = false;
    public boolean overrideGeneration = false;
    public boolean allowMultipleWalls = false;
    public int clearDelay;
    public boolean timeFreeze = false;
    public boolean wallFreeze = false;

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
        if (field == null) return;
        field.setEvent(null);
    }

    public void score(Wall wall) {

    }

    public void onWallScore(Wall wall) {
        // override
    }

    public double getMeterPercentRequired() {
        return meterPercentRequired;
    }

    public boolean isActive() {
        return active;
    }
}
