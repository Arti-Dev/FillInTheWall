package com.articreep.fillinthewall.display;

import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;

public class ScoreboardEntry {
    // todo probably make this per-scoreboard instead of globally static
    private static final ArrayList<String> existingEntries = new ArrayList<>();
    private final ScoreboardEntryType type;
    private String currentText;
    int slot;

    public ScoreboardEntry(ScoreboardEntryType type, int slot) {
        this.type = type;
        this.slot = slot;
        currentText = type.getRawText();
    }

    public String getCurrentText() {
        return currentText;
    }

    public void addToObjective(Objective objective) {
        // scary while loop
        while (existingEntries.contains(currentText)) {
            currentText += " ";
        }
        existingEntries.add(currentText);
        objective.getScore(currentText).setScore(-slot);
    }

    public void update(Scoreboard scoreboard, Objective objective, Object... data) {
        scoreboard.resetScores(currentText);
        existingEntries.remove(currentText);
        currentText = type.getFormattedText(data);
        addToObjective(objective);
    }

    public void forceUpdate(Scoreboard scoreboard, Objective objective, String string) {
        scoreboard.resetScores(currentText);
        existingEntries.remove(currentText);
        currentText = string;
        addToObjective(objective);
    }

    public ScoreboardEntryType getType() {
        return type;
    }

    public void destroy() {
        existingEntries.remove(currentText);
    }
}
