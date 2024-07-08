package com.articreep.holeinthewall.menu;

import com.articreep.holeinthewall.HoleInTheWall;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class EndScreen {
    private final Location location;
    private TextDisplay display;
    private final List<String> lines = new ArrayList<>();

    public EndScreen(Location location) {
        this.location = location;
    }

    public void display() {
        display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        new BukkitRunnable() {
            int i = 0;
            int lineLength = 0;
            @Override
            public void run() {
                if (display == null) {
                    cancel();
                    return;
                }
                StringBuilder string = new StringBuilder();
                for (int j = 0; j < i; j++) {
                    string.append(lines.get(j)).append("\n");
                }
                string.append(lines.get(i), 0, lineLength);

                display.setText(string.toString());

                if (lineLength < lines.get(i).length()) {
                    lineLength++;
                } else {
                    i++;
                    lineLength = 0;
                    if (i >= lines.size()) {
                        cancel();
                    }
                }
            }
        }.runTaskTimer(HoleInTheWall.getInstance(), 0, 1);

        // Despawn after 1 minute
        Bukkit.getScheduler().runTaskLater(HoleInTheWall.getInstance(), this::despawn, 20 * 60);
    }

    public void addLine(String string) {
        lines.add(string);
    }

    public void despawn() {
        if (display != null) display.remove();
    }
}
