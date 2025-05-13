package com.articreep.fillinthewall.leveling;

import com.articreep.fillinthewall.Database;
import com.articreep.fillinthewall.FillInTheWall;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.javatuples.Pair;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerLevels {
    private final static Component prefix = Component.text("Lv", NamedTextColor.GRAY);
    private final static NamedTextColor[] levelColors =
            {NamedTextColor.GRAY, NamedTextColor.RED,
            NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.DARK_GREEN,
            NamedTextColor.GREEN, NamedTextColor.DARK_AQUA, NamedTextColor.AQUA,
            NamedTextColor.DARK_PURPLE, NamedTextColor.DARK_RED, NamedTextColor.LIGHT_PURPLE};

    private final static Map<UUID, Integer> xpCache = new HashMap<>();

    // Brackets are 10 levels per (0-9, 10-19, etc)
    private final static int[] levelBracketXP = {50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 100};

    // todo this doesn't guard for race conditions idk
    public static synchronized void addXP(UUID uuid, int amount) {
        if (amount <= 0) return;
        int newXP = getRawXP(uuid) + amount;
        xpCache.put(uuid, newXP);
        Bukkit.getScheduler().runTaskAsynchronously(FillInTheWall.getInstance(), () -> Database.setXP(uuid, newXP));
    }

    public static synchronized void resetXP(UUID uuid) {
        xpCache.put(uuid, 0);
        Bukkit.getScheduler().runTaskAsynchronously(FillInTheWall.getInstance(), () -> Database.setXP(uuid, 0));
    }

    public static Pair<Integer, Integer> getLevel(UUID uuid) {
        int xp = getRawXP(uuid);
        int level = 0;
        int levelBracket = 0;
        while (xp >= levelBracketXP[levelBracket]) {
            xp -= levelBracketXP[levelBracket];
            level++;
            levelBracket = Math.min(level / 10, 11);
        }
        return Pair.with(level, xp);
    }

    public static int getRawXP(UUID uuid) {
        if (!xpCache.containsKey(uuid)) {
            try {
                xpCache.put(uuid, Database.getXP(uuid));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return xpCache.get(uuid);
    }

    public static Component getPrefix(UUID uuid) {
        Pair<Integer, Integer> level = getLevel(uuid);
        int levelNum = level.getValue0();
        int levelBracket = Math.min(levelNum / 10, 11);
        return prefix.append(Component.text(levelNum, levelColors[levelBracket]));
    }
}
