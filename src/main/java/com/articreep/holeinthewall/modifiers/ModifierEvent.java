package com.articreep.holeinthewall.modifiers;

import com.articreep.holeinthewall.PlayingField;
import com.articreep.holeinthewall.Wall;
import com.articreep.holeinthewall.WallQueue;
import org.bukkit.entity.Player;

/** Represents an event that affects the playing field and/or queue. */
public abstract class ModifierEvent {
    public final boolean overrideScoring;
    public final boolean allowMultipleWalls;
    public final int pauseTime;

    protected WallQueue queue;
    protected int ticksRemaining;
    protected PlayingField field;
    protected Player player;


    // todo this constructor is yucky
    protected ModifierEvent(PlayingField field, int ticks, int pauseTime, boolean pauseScoring, boolean allowMultipleWalls) {
        this.field = field;
        this.queue = field.getQueue();
        this.overrideScoring = pauseScoring;
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

    public abstract void score(Wall wall);
}
