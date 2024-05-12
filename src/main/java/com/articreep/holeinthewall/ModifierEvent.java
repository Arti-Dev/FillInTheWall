package com.articreep.holeinthewall;

import org.bukkit.entity.Player;

/** Represents an event that affects the playing field and/or queue. */
public abstract class ModifierEvent {
    public final boolean pauseScoring;
    public final boolean allowMultipleWalls;
    protected int ticksRemaining;
    public final int pauseTime;

    protected WallQueue queue;
    protected PlayingField field;
    protected Player player;

    protected ModifierEvent(PlayingField field, int ticks, int pauseTime, boolean pauseScoring, boolean allowMultipleWalls) {
        this.field = field;
        this.queue = field.getQueue();
        this.pauseScoring = pauseScoring;
        this.ticksRemaining = ticks;
        this.pauseTime = pauseTime;
        this.allowMultipleWalls = allowMultipleWalls;
        this.player = field.getPlayer();
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
}
