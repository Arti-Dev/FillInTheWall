package com.articreep.holeinthewall.modifiers;

import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.Wall;
import com.articreep.holeinthewall.WallQueue;

/** Represents an event that affects the playing field and/or queue. */
public abstract class ModifierEvent {
    /* Settings. Child classes should change these as fit */
    public boolean overrideScoring = false;
    public boolean overrideGeneration = false;
    public boolean allowMultipleWalls = false;
    public int clearDelay;
    public boolean timeFreeze = false;
    public boolean wallFreeze = false;

    protected WallQueue queue;
    protected int ticksRemaining;
    protected PlayingField field;


    protected ModifierEvent(PlayingField field, int ticks) {
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

    public abstract void activate();

    public abstract void end();

    public abstract void score(Wall wall);
}
