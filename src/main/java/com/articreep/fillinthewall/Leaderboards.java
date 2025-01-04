package com.articreep.fillinthewall;

import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.utils.Utils;
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

    private static Map<TextDisplay, Gamemode> leaderboards = new HashMap<>();

    protected static void spawnLeaderboards(FileConfiguration config) {
        removeLeaderboards();

        Location scoreAttackLocation = config.getLocation("leaderboards.score-attack");
        Location rushScoreAttackLocation = config.getLocation("leaderboards.rush-score-attack");
        Location marathonLocation = config.getLocation("leaderboards.marathon");
        Location sprintLocation = config.getLocation("leaderboards.sprint");
        Location megaLocation = config.getLocation("leaderboards.mega");

        if (scoreAttackLocation != null) {
            TextDisplay scoreAttackDisplay = (TextDisplay) scoreAttackLocation.getWorld().spawnEntity(
                    scoreAttackLocation, EntityType.TEXT_DISPLAY);
            scoreAttackDisplay.setText("Score Attack Leaderboard");
            scoreAttackDisplay.setBillboard(Display.Billboard.VERTICAL);
            leaderboards.put(scoreAttackDisplay, Gamemode.SCORE_ATTACK);
        }
        if (rushScoreAttackLocation != null) {
            TextDisplay rushScoreAttackDisplay = (TextDisplay) rushScoreAttackLocation.getWorld().spawnEntity(
                    rushScoreAttackLocation, EntityType.TEXT_DISPLAY);
            rushScoreAttackDisplay.setText("Rush Score Attack Leaderboard");
            rushScoreAttackDisplay.setBillboard(Display.Billboard.VERTICAL);
            leaderboards.put(rushScoreAttackDisplay, Gamemode.RUSH_SCORE_ATTACK);
        }
        if (marathonLocation != null) {
            TextDisplay marathonDisplay = (TextDisplay) marathonLocation.getWorld().spawnEntity(
                    marathonLocation, EntityType.TEXT_DISPLAY);
            marathonDisplay.setText("Marathon Leaderboard");
            marathonDisplay.setBillboard(Display.Billboard.VERTICAL);
            leaderboards.put(marathonDisplay, Gamemode.MARATHON);
        }
        if (sprintLocation != null) {
            TextDisplay sprintDisplay = (TextDisplay) sprintLocation.getWorld().spawnEntity(
                    sprintLocation, EntityType.TEXT_DISPLAY);
            sprintDisplay.setText("Sprint Leaderboard");
            sprintDisplay.setBillboard(Display.Billboard.VERTICAL);
            leaderboards.put(sprintDisplay, Gamemode.SPRINT);
        }
        if (megaLocation != null) {
            TextDisplay megaDisplay = (TextDisplay) megaLocation.getWorld().spawnEntity(
                    megaLocation, EntityType.TEXT_DISPLAY);
            megaDisplay.setText("Mega Leaderboard");
            megaDisplay.setBillboard(Display.Billboard.VERTICAL);
            leaderboards.put(megaDisplay, Gamemode.MEGA);
        }
        updateLeaderboards();
    }

    protected static void removeLeaderboards() {
        for (TextDisplay display : leaderboards.keySet()) {
            display.remove();
        }
        leaderboards.clear();
    }

    protected static void updateLeaderboards() {
        for (Map.Entry<TextDisplay, Gamemode> entry : leaderboards.entrySet()) {
            TextDisplay display = entry.getKey();
            Gamemode gamemode = entry.getValue();
            StringBuilder stringBuilder = new StringBuilder(gamemode.getTitle());
            stringBuilder.append("\n").append(ChatColor.GRAY).append("Top Scores\n");
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
    }
}
