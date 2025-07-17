package com.articreep.fillinthewall.menu;

import com.articreep.fillinthewall.FillInTheWall;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class EndScreen {
    private final Location location;
    private TextDisplay display;
    private final List<String> lines = new ArrayList<>();
    private final static MiniMessage miniMessage = MiniMessage.miniMessage();

    public EndScreen(Location location) {
        this.location = location;
    }

    public void display() {
        display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (display == null || display.isDead()) {
                    cancel();
                    return;
                }
                StringBuilder string = new StringBuilder();
                for (int j = 0; j < i; j++) {
                    string.append(lines.get(j)).append("\n").append("<reset>");
                }

                display.getWorld().playSound(display.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_FALL, 1, 1);

                display.text(miniMessage.deserialize(string.toString()));

                if (i >= lines.size()) {
                    cancel();
                }
                i++;
            }
        }.runTaskTimer(FillInTheWall.getInstance(), 0, 5);

        // Despawn after 1 minute
        Bukkit.getScheduler().runTaskLater(FillInTheWall.getInstance(), this::despawn, 20 * 60);
    }

    public void addLine(Component component) {
        lines.add(miniMessage.serialize(component));
    }

    public void despawn() {
        if (display != null) display.remove();
    }
}
