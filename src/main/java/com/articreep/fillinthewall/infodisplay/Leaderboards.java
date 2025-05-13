package com.articreep.fillinthewall.infodisplay;

import com.articreep.fillinthewall.Database;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.leveling.PlayerLevels;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Leaderboards {

    private static final Map<TextDisplay, Gamemode> scoreLeaderboards = new HashMap<>();
    private static TextDisplay levelLeaderboard = null;

    public static void spawnLeaderboards(FileConfiguration config) {
        removeLeaderboards();

        Location scoreAttackLocation = config.getLocation("leaderboards.score-attack");
        Location rushScoreAttackLocation = config.getLocation("leaderboards.rush-score-attack");
        Location marathonLocation = config.getLocation("leaderboards.marathon");
        Location sprintLocation = config.getLocation("leaderboards.sprint");
        Location megaLocation = config.getLocation("leaderboards.mega");
        Location levelLocation = config.getLocation("leaderboards.level");

        if (scoreAttackLocation != null) {
            TextDisplay scoreAttackDisplay = (TextDisplay) scoreAttackLocation.getWorld().spawnEntity(
                    scoreAttackLocation, EntityType.TEXT_DISPLAY);
            scoreAttackDisplay.setText("Score Attack Leaderboard");
            scoreAttackDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(scoreAttackDisplay, Gamemode.SCORE_ATTACK);
        }
        if (rushScoreAttackLocation != null) {
            TextDisplay rushScoreAttackDisplay = (TextDisplay) rushScoreAttackLocation.getWorld().spawnEntity(
                    rushScoreAttackLocation, EntityType.TEXT_DISPLAY);
            rushScoreAttackDisplay.setText("Rush Score Attack Leaderboard");
            rushScoreAttackDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(rushScoreAttackDisplay, Gamemode.RUSH_SCORE_ATTACK);
        }
        if (marathonLocation != null) {
            TextDisplay marathonDisplay = (TextDisplay) marathonLocation.getWorld().spawnEntity(
                    marathonLocation, EntityType.TEXT_DISPLAY);
            marathonDisplay.setText("Marathon Leaderboard");
            marathonDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(marathonDisplay, Gamemode.MARATHON);
        }
        if (sprintLocation != null) {
            TextDisplay sprintDisplay = (TextDisplay) sprintLocation.getWorld().spawnEntity(
                    sprintLocation, EntityType.TEXT_DISPLAY);
            sprintDisplay.setText("Sprint Leaderboard");
            sprintDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(sprintDisplay, Gamemode.SPRINT);
        }
        if (megaLocation != null) {
            TextDisplay megaDisplay = (TextDisplay) megaLocation.getWorld().spawnEntity(
                    megaLocation, EntityType.TEXT_DISPLAY);
            megaDisplay.setText("Mega Leaderboard");
            megaDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(megaDisplay, Gamemode.MEGA);
        }

        if (levelLocation != null) {
            levelLeaderboard = (TextDisplay) levelLocation.getWorld().spawnEntity(
                    levelLocation, EntityType.TEXT_DISPLAY);
            levelLeaderboard.setText("Level Leaderboard");
            levelLeaderboard.setBillboard(Display.Billboard.VERTICAL);
        }
        updateLeaderboards();
    }

    public static void removeLeaderboards() {
        for (TextDisplay display : scoreLeaderboards.keySet()) {
            display.remove();
        }
        scoreLeaderboards.clear();

        if (levelLeaderboard != null) {
            levelLeaderboard.remove();
            levelLeaderboard = null;
        }
    }

    public static void updateLeaderboards() {
        for (Map.Entry<TextDisplay, Gamemode> entry : scoreLeaderboards.entrySet()) {
            TextDisplay display = entry.getKey();
            Gamemode gamemode = entry.getValue();
            StringBuilder stringBuilder = new StringBuilder(gamemode.getTitle());
            stringBuilder.append("\n").append(ChatColor.GRAY).append("Top Scores\n");

            if (Database.isOfflineMode()) {
                stringBuilder.append(ChatColor.GRAY).append("\nDatabase is currently offline.\nPlease check back later.");
                display.setText(stringBuilder.toString());
                continue;
            }

            try {
                boolean scoreByTime = gamemode.getDefaultSettings().getBooleanAttribute(GamemodeAttribute.SCORE_BY_TIME);
                LinkedHashMap<UUID, Integer> topScores;
                if (scoreByTime) {
                    topScores = Database.getTopTimes(gamemode);
                } else {
                    topScores = Database.getTopScores(gamemode);
                }
                int i = 1;
                for (Map.Entry<UUID, Integer> score : topScores.entrySet()) {
                    stringBuilder.append("\n")
                            .append(ChatColor.YELLOW)
                            .append("#").append(i).append(" ")
                            .append(Bukkit.getOfflinePlayer(score.getKey()).getName()).append(": ");
                    if (scoreByTime) {
                        stringBuilder.append(Utils.getPreciseFormattedTime(score.getValue()));
                    } else {
                        stringBuilder.append(score.getValue());
                    }
                    i++;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                stringBuilder.append("\n").append(ChatColor.RED).append("Error loading scores");
            } finally {
                stringBuilder.append("\n\n").append(ChatColor.GRAY).append("Updates every 30 seconds");
                display.setText(stringBuilder.toString());
            }
        }

        // Level leaderboard
        // todo this is ad-hoc - make a more robust system
        TextComponent.Builder builder = Component.text("Level Leaderboard\n", NamedTextColor.AQUA).toBuilder();
        if (Database.isOfflineMode()) {
            builder.append(Component.text("\nDatabase is currently offline.\nPlease check back later.", NamedTextColor.GRAY));
            levelLeaderboard.text(builder.build());
            return;
        }

        try {
            LinkedHashMap<UUID, Integer> topLevels = Database.getTopXP();
            int i = 1;
            for (Map.Entry<UUID, Integer> entry : topLevels.entrySet()) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = "null";
                builder.append(Component.text("\n")
                        .append(Component.text("#", NamedTextColor.YELLOW).append(Component.text(i, NamedTextColor.YELLOW)))
                        .append(Component.text(" "))
                        .append(PlayerLevels.getPrefix(entry.getValue()))
                        .append(Component.text(" "))
                        .append(Component.text(name, NamedTextColor.YELLOW))
                        .append(Component.text(": ", NamedTextColor.YELLOW))
                        .append(Component.text(entry.getValue(), NamedTextColor.AQUA))
                        .append(Component.text("XP", NamedTextColor.AQUA)));
                i++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            builder.append(Component.text("\nError loading scores", NamedTextColor.RED));
        } finally {
            builder.append(Component.text("\n\nUpdates every 30 seconds", NamedTextColor.GRAY));
            levelLeaderboard.text(builder.build());
        }

    }
}
