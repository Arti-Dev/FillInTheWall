package com.articreep.fillinthewall.infodisplay;

import com.articreep.fillinthewall.Database;
import com.articreep.fillinthewall.FillInTheWall;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.articreep.fillinthewall.gamemode.GamemodeAttribute;
import com.articreep.fillinthewall.playerinfo.PlayerLevels;
import com.articreep.fillinthewall.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import java.util.function.BiFunction;

public class Leaderboards {

    private static final Map<TextDisplay, Gamemode> scoreLeaderboards = new HashMap<>();
    private static TextDisplay levelLeaderboard = null;
    private static TextDisplay playtimeLeaderboard = null;
    private static TextDisplay perfectWallsLeaderboard = null;
    private static TextDisplay multiplayerLeaderboard = null;
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public static void spawnLeaderboards(FileConfiguration config) {
        removeLeaderboards();

        Location scoreAttackLocation = config.getLocation("leaderboards.score-attack");
        Location rushScoreAttackLocation = config.getLocation("leaderboards.rush-score-attack");
        Location marathonLocation = config.getLocation("leaderboards.marathon");
        Location sprintLocation = config.getLocation("leaderboards.sprint");
        Location megaLocation = config.getLocation("leaderboards.mega");
        Location levelLocation = config.getLocation("leaderboards.level");
        Location playtimeLocation = config.getLocation("leaderboards.playtime");
        Location perfectWallsLocation = config.getLocation("leaderboards.perfect-walls");
        Location cappedMarathonLocation = config.getLocation("leaderboards.capped-marathon");
        Location multiplayerLocation = config.getLocation("leaderboards.multiplayer");

        if (scoreAttackLocation != null) {
            TextDisplay scoreAttackDisplay = (TextDisplay) scoreAttackLocation.getWorld().spawnEntity(
                    scoreAttackLocation, EntityType.TEXT_DISPLAY);
            scoreAttackDisplay.text(Component.text("Score Attack Leaderboard"));
            scoreAttackDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(scoreAttackDisplay, Gamemode.SCORE_ATTACK);
        }
        if (rushScoreAttackLocation != null) {
            TextDisplay rushScoreAttackDisplay = (TextDisplay) rushScoreAttackLocation.getWorld().spawnEntity(
                    rushScoreAttackLocation, EntityType.TEXT_DISPLAY);
            rushScoreAttackDisplay.text(Component.text("Rush Score Attack Leaderboard"));
            rushScoreAttackDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(rushScoreAttackDisplay, Gamemode.RUSH_SCORE_ATTACK);
        }
        if (marathonLocation != null) {
            TextDisplay marathonDisplay = (TextDisplay) marathonLocation.getWorld().spawnEntity(
                    marathonLocation, EntityType.TEXT_DISPLAY);
            marathonDisplay.text(Component.text("Endless Survival Leaderboard"));
            marathonDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(marathonDisplay, Gamemode.MARATHON);
        }
        if (sprintLocation != null) {
            TextDisplay sprintDisplay = (TextDisplay) sprintLocation.getWorld().spawnEntity(
                    sprintLocation, EntityType.TEXT_DISPLAY);
            sprintDisplay.text(Component.text("Sprint Leaderboard"));
            sprintDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(sprintDisplay, Gamemode.SPRINT);
        }
        if (megaLocation != null) {
            TextDisplay megaDisplay = (TextDisplay) megaLocation.getWorld().spawnEntity(
                    megaLocation, EntityType.TEXT_DISPLAY);
            megaDisplay.text(Component.text("Mega Leaderboard"));
            megaDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(megaDisplay, Gamemode.MEGA);
        }

        if (levelLocation != null) {
            levelLeaderboard = (TextDisplay) levelLocation.getWorld().spawnEntity(
                    levelLocation, EntityType.TEXT_DISPLAY);
            levelLeaderboard.text(Component.text("Level Leaderboard"));
            levelLeaderboard.setBillboard(Display.Billboard.VERTICAL);
        }

        if (playtimeLocation != null) {
            playtimeLeaderboard = (TextDisplay) playtimeLocation.getWorld().spawnEntity(
                    playtimeLocation, EntityType.TEXT_DISPLAY);
            playtimeLeaderboard.text(Component.text("Playtime Leaderboard"));
            playtimeLeaderboard.setBillboard(Display.Billboard.VERTICAL);
        }

        if (perfectWallsLocation != null) {
            perfectWallsLeaderboard = (TextDisplay) perfectWallsLocation.getWorld().spawnEntity(
                    perfectWallsLocation, EntityType.TEXT_DISPLAY);
            perfectWallsLeaderboard.text(Component.text("Perfect Walls Cleared Leaderboard"));
            perfectWallsLeaderboard.setBillboard(Display.Billboard.VERTICAL);
        }

        if (cappedMarathonLocation != null) {
            TextDisplay cappedMarathonDisplay = (TextDisplay) cappedMarathonLocation.getWorld().spawnEntity(
                    cappedMarathonLocation, EntityType.TEXT_DISPLAY);
            cappedMarathonDisplay.text(Component.text("Marathon Leaderboard"));
            cappedMarathonDisplay.setBillboard(Display.Billboard.VERTICAL);
            scoreLeaderboards.put(cappedMarathonDisplay, Gamemode.CAPPED_MARATHON);
        }

        if (multiplayerLocation != null) {
            multiplayerLeaderboard = (TextDisplay) multiplayerLocation.getWorld().spawnEntity(
                    multiplayerLocation, EntityType.TEXT_DISPLAY);
            multiplayerLeaderboard.text(Component.text("Multiplayer Leaderboard"));
            multiplayerLeaderboard.setBillboard(Display.Billboard.VERTICAL);
        }


        Bukkit.getScheduler().runTaskAsynchronously(FillInTheWall.getInstance(), Leaderboards::updateLeaderboards);
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
        if (playtimeLeaderboard != null) {
            playtimeLeaderboard.remove();
            playtimeLeaderboard = null;
        }
        if (perfectWallsLeaderboard != null) {
            perfectWallsLeaderboard.remove();
            perfectWallsLeaderboard = null;
        }

        if (multiplayerLeaderboard != null) {
            multiplayerLeaderboard.remove();
            multiplayerLeaderboard = null;
        }
    }

    public static void updateLeaderboards() {
        for (Map.Entry<TextDisplay, Gamemode> entry : scoreLeaderboards.entrySet()) {
            TextDisplay display = entry.getKey();
            Gamemode gamemode = entry.getValue();
            TextComponent title = (TextComponent) miniMessage.deserialize(
                    miniMessage.serialize(gamemode.getTitle()) + "\n<gray>Top Scores</gray>\n");

            if (Database.isOfflineMode()) {
                offlineLeaderboard(display, title);
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
                populateLeaderboard(display, topScores, title,
                        (score, playerName) -> {
                    StringBuilder builder = new StringBuilder("<yellow>" + playerName + ": ");
                    if (scoreByTime) {
                        builder.append(Utils.getPreciseFormattedTime(score));
                    } else {
                        builder.append(score);
                    }
                    builder.append("</yellow>");
                    return miniMessage.deserialize(builder.toString());
                        });
            } catch (SQLException e) {
                e.printStackTrace();
                errorLeaderboard(display, title);
            }
        }

        // Level leaderboard
        TextComponent levelTitle = Component.text("Level Leaderboard\n", NamedTextColor.AQUA);
        if (Database.isOfflineMode()) {
            offlineLeaderboard(levelLeaderboard, levelTitle);
        } else {
            try {
                LinkedHashMap<UUID, Integer> topLevels = Database.getTopXP();
                populateLeaderboard(levelLeaderboard, topLevels, levelTitle,
                        (xp, playerName) -> miniMessage.deserialize(
                                miniMessage.serialize(PlayerLevels.getPrefix(xp)) + " <yellow>" + playerName + ": <aqua>" + xp + "XP"));
            } catch (SQLException e) {
                e.printStackTrace();
                errorLeaderboard(levelLeaderboard, levelTitle);
            }
        }

        // Playtime leaderboard

        TextComponent playtimeTitle = (TextComponent) miniMessage.deserialize("<gray>Playtime Leaderboard\n");
        if (Database.isOfflineMode()) {
            offlineLeaderboard(playtimeLeaderboard, playtimeTitle);
        } else {
            try {
                LinkedHashMap<UUID, Long> topPlaytime = Database.getTopPlaytime();
                populateLeaderboard(playtimeLeaderboard, topPlaytime, playtimeTitle,
                        (playtime, playerName) -> miniMessage.deserialize(
                                "<yellow>" + playerName + ": <gray>" + Utils.secondsTohms(playtime)));
            } catch (SQLException e) {
                e.printStackTrace();
                errorLeaderboard(playtimeLeaderboard, playtimeTitle);
            }
        }

        // Perfect walls leaderboard

        TextComponent perfectWallsTitle = (TextComponent) miniMessage.deserialize("<gradient:gold:yellow>Perfect Walls Cleared Leaderboard\n");
        if (Database.isOfflineMode()) {
            offlineLeaderboard(perfectWallsLeaderboard, perfectWallsTitle);
        } else {
            try {
                LinkedHashMap<UUID, Integer> topPerfectWalls = Database.getTopPerfectWalls();
                populateLeaderboard(perfectWallsLeaderboard, topPerfectWalls, perfectWallsTitle,
                        (perfectWalls, playerName) -> miniMessage.deserialize(
                                "<yellow>" + playerName + ": <gold>" + perfectWalls + " walls"));
            } catch (SQLException e) {
                e.printStackTrace();
                errorLeaderboard(perfectWallsLeaderboard, perfectWallsTitle);
            }
        }

        // Multiplayer leaderboard

        TextComponent multiplayerTitle = (TextComponent) miniMessage.deserialize("<aqua>Multiplayer Leaderboard\n<gray>Top Combined Scores\n");
        if (Database.isOfflineMode()) {
            offlineLeaderboard(multiplayerLeaderboard, multiplayerTitle);
        } else {
            try {
                LinkedHashMap<UUID, Integer> topMultiplayerScores = Database.getTopMultiplayerScores();
                populateLeaderboard(multiplayerLeaderboard, topMultiplayerScores, multiplayerTitle,
                        (score, playerName) -> miniMessage.deserialize(
                                "<yellow>" + playerName + ": <aqua>" + score + " points"));
            } catch (SQLException e) {
                e.printStackTrace();
                errorLeaderboard(multiplayerLeaderboard, multiplayerTitle);
            }
        }
    }

    public static <T> void populateLeaderboard(TextDisplay leaderboard, LinkedHashMap<UUID, T> topScores, TextComponent title,
                                               BiFunction<T, String, Component> func) {
        Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
            TextComponent.Builder builder = title.toBuilder();
            int i = 1;
            for (Map.Entry<UUID, T> entry : topScores.entrySet()) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = "null";
                builder.append(miniMessage.deserialize("\n<yellow>#" + i + " "));
                builder.append(func.apply(entry.getValue(), name));
                i++;
            }
            builder.append(Component.text("\n\nUpdates every 30 seconds", NamedTextColor.GRAY));
            leaderboard.text(builder.build());
        });
    }

    public static void offlineLeaderboard(TextDisplay leaderboard, TextComponent title) {
        Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
            TextComponent.Builder builder = title.toBuilder();
            builder.append(Component.text("\nDatabase is currently offline.\nPlease check back later.", NamedTextColor.GRAY));
            leaderboard.text(builder.build());
        });
    }

    public static void errorLeaderboard(TextDisplay leaderboard, TextComponent title) {
        Bukkit.getScheduler().runTask(FillInTheWall.getInstance(), () -> {
            TextComponent.Builder builder = title.toBuilder();
            builder.append(Component.text("\nError loading scores", NamedTextColor.RED));
            leaderboard.text(builder.build());
        });
    }
}
