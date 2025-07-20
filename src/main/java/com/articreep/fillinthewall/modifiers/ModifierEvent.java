package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.game.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
    public int clearDelayOverride = -1;
    public boolean timeFreeze = false;
    public boolean wallFreeze = false;
    public boolean fillFieldAfterSubmission = false;
    public boolean modifyWalls = false;
    public boolean allowMeterAccumulation = true;
    public boolean isChargeEvent = false;
    /**
     * For use with endless mode. Some events (like Multiplace) come with specially designed walls
     * This setting tells them to double the amount of priority walls added to the queue
     * todo currently have to set this before calling additionalInit() which is stupid
     */
    public boolean doublePriorityWalls = false;

    public boolean shelveEvent = false;
    protected ModifierEvent shelvedEvent;

    protected WallQueue queue;
    protected int ticksRemaining;
    protected boolean infinite = false;
    protected PlayingField field;

    protected final int DEFAULT_TICKS = 20*20;

    protected boolean active = false;

    protected final static MiniMessage miniMessage = MiniMessage.miniMessage();


    protected ModifierEvent() {
        this.ticksRemaining = DEFAULT_TICKS;
    }

    public static ModifierEvent createEvent(Class<? extends ModifierEvent> clazz) {
        if (clazz == null) return null;
        Constructor<? extends ModifierEvent> constructor;
        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
        try {
            return constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Register all modifier events here
    public enum Type {
        CHAIN(Chain.class),
        CHEESE(Cheese.class),
        FIREINTHEHOLE(FireInTheHole.class),
        FLIP(Flip.class),
        FREEZE(Freeze.class, 0.5),
        GRAVITY(Gravity.class),
        INVERTED(Inverted.class),
        LINES(Lines.class),
        MULTIPLACE(Multiplace.class),
        PLAYERINTHEWALL(PlayerInTheWall.class),
        POPIN(PopIn.class),
        RUSH(Rush.class),
        SCALE(Scale.class),
        STRIPES(Stripes.class),
        SPEEDUP(SpeedUp.class),
        // The tutorial uses a fake meter
        TUTORIAL(Tutorial.class, 0),
        UNSUPPORTED(Unsupported.class),
        RANDOM(null),
        // Exclusively for endless mode
        RANDOM_ENDLESS(null),
        RANDOM_FINALS(null),
        NONE(null);

        final Class<? extends ModifierEvent> clazz;
        double meterPercentRequired = 1;
        Type(Class<? extends ModifierEvent> clazz) {
            this.clazz = clazz;
        }

        Type(Class<? extends ModifierEvent> clazz, double meterPercentRequired) {
            this.clazz = clazz;
            this.meterPercentRequired = meterPercentRequired;
        }

        public ModifierEvent createEvent() {
            if (this == RANDOM) {
                ArrayList<Type> types = new ArrayList<>(List.of(values()));
                types.remove(RANDOM);
                types.remove(RANDOM_ENDLESS);
                types.remove(RANDOM_FINALS);
                types.remove(TUTORIAL);
                types.remove(FREEZE);
                types.remove(UNSUPPORTED);
                types.remove(CHEESE);
                types.remove(CHAIN);
                types.remove(NONE);
                types.remove(RUSH);
                Type type = types.get((int) (Math.random() * types.size()));
                return type.createEvent();
            } else if (this == RANDOM_ENDLESS) {
                ArrayList<Type> types = new ArrayList<>(List.of(values()));
                types.remove(RANDOM);
                types.remove(RANDOM_ENDLESS);
                types.remove(RANDOM_FINALS);
                types.remove(TUTORIAL);
                types.remove(FREEZE);
                types.remove(NONE);
                types.remove(RUSH);
                Type type = types.get((int) (Math.random() * types.size()));
                return type.createEvent();
            } else if (this == RANDOM_FINALS) {
                // 50% chance of no event
                if (Math.random() < 0.5) return null;
                ArrayList<Type> types = new ArrayList<>();
                types.add(FIREINTHEHOLE);
                types.add(INVERTED);
                types.add(POPIN);
                types.add(SCALE);
                Type type = types.get((int) (Math.random() * types.size()));
                return type.createEvent();
            }
            return ModifierEvent.createEvent(clazz);
        }

        public Class<? extends ModifierEvent> getClazz() {
            return clazz;
        }

        public double getMeterPercentRequired() {
            return meterPercentRequired;
        }
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public void tick() {
        if (ticksRemaining <= 0) return;
        if (!infinite) ticksRemaining--;
    }

    /** If returns null, the default action bar will be used. */
    public Component actionBarOverride() {
        return null;
    }

    /**
     * Set the playing field before doing this with setPlayingField()
     */
    public void activate() {
        if (field == null) return;
        field.setEvent(this);
        active = true;
        playActivateSound();
    }

    public void setShelvedEvent(ModifierEvent event) {
        // Prevent "infinite shelving"
        if (createEvent(event.getClass()).shelveEvent) shelvedEvent = null;
        else shelvedEvent = event;
    }

    public void end() {
        active = false;
        playDeactivateSound();
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

    public ModifierEvent getShelvedEvent() {
        return shelvedEvent;
    }

    public void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
    }

    public void setInfinite(boolean infinite) {
        this.infinite = infinite;
    }

    public abstract ModifierEvent copy();

    public abstract void playActivateSound();

    public abstract void playDeactivateSound();

    public void setPlayingField(PlayingField field) {
        this.field = field;
        this.queue = field.getQueue();
    }

    public PlayingField getPlayingField() {
        return field;
    }

    /**
     * Additional initialization that requires the length and height of the playing field.
     * @param length length
     * @param height height
     */
    public void additionalInit(int length, int height) {
        // override
    }

    public void setDoublePriorityWalls(boolean bool) {
        this.doublePriorityWalls = bool;
    }
}
