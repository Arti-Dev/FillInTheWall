package com.articreep.fillinthewall.infodisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;

public class ScoreboardEntry {
    // todo probably make this per-scoreboard instead of globally static
    private static final ArrayList<Component> existingEntries = new ArrayList<>();
    private final ScoreboardEntryType type;
    private Component currentText;
    int slot;

    public ScoreboardEntry(ScoreboardEntryType type, int slot) {
        this.type = type;
        this.slot = slot;
        currentText = type.getRawText();
    }

    public Component getCurrentText() {
        return currentText;
    }

    public void addToObjective(Objective objective) {
        // scary while loop
        // made to avoid duplicate entries
        while (existingEntries.contains(currentText)) {
            currentText = currentText.append(Component.text(" "));
        }
        existingEntries.add(currentText);
        // have to serialize back to legacy
        String currentTextSerialized = LegacyComponentSerializer.legacySection().serialize(currentText);
        objective.getScore(currentTextSerialized).setScore(-slot);
    }


    public void update(Scoreboard scoreboard, Objective objective, Component... data) {
        String currentTextSerialized = LegacyComponentSerializer.legacySection().serialize(currentText);
        scoreboard.resetScores(currentTextSerialized);
        existingEntries.remove(currentText);
        currentText = type.getFormattedText(data);
        addToObjective(objective);
    }

    public void forceUpdate(Scoreboard scoreboard, Objective objective, Component component) {
        String serialized = LegacyComponentSerializer.legacySection().serialize(currentText);
        scoreboard.resetScores(serialized);
        existingEntries.remove(currentText);
        currentText = component;
        addToObjective(objective);
    }

    public ScoreboardEntryType getType() {
        return type;
    }

    public void destroy() {
        existingEntries.remove(currentText);
    }
}
