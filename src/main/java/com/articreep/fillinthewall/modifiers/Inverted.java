package com.articreep.fillinthewall.modifiers;

import com.articreep.fillinthewall.PlayingField;

public class Inverted extends ModifierEvent {
    public Inverted(PlayingField field, int ticks) {
        super(field, ticks);
        fillFieldAfterSubmission = true;
        invertWalls = true;
    }

    @Override
    public void activate() {
        super.activate();
        field.getQueue().instantSend();
        field.sendTitleToPlayers("Inverted!", "Left-click to win..?", 0, 40, 10);
    }

    @Override
    public void end() {
        super.end();
        field.getQueue().instantSend();
        field.sendTitleToPlayers("", "Walls are back to normal!", 0, 20, 10);
    }
}
