package com.articreep.fillinthewall;

import com.articreep.fillinthewall.gamemode.Gamemode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ScoreDatabase {
    private static final HashSet<Gamemode> supportedGamemodes = new HashSet<>();

    static {
        supportedGamemodes.add(Gamemode.SCORE_ATTACK);
        supportedGamemodes.add(Gamemode.RUSH_SCORE_ATTACK);
        supportedGamemodes.add(Gamemode.MARATHON);
    }

    private static void addPlayer(UUID uuid) throws SQLException {
        // Adds a new UUID into the database
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO scores(uuid) VALUES(?)"
        )) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException("Error while adding new user to database!");
        }

    }

    // The gamemode has to be concatenated into the SQL query, or else exceptions will be thrown:
    // https://www.spigotmc.org/threads/mysql-invalid-value-for-getint-mining.361748/
    public static int getRecord(UUID uuid, Gamemode gamemode) throws SQLException {
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "SELECT " + gamemode.toString() + " FROM scores WHERE uuid = ?"
        )) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                return result.getInt(gamemode.toString());
            } else {
                // If they didn't exist before, add them!
                addPlayer(uuid);
                return 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error while getting user score from database!");
        }
    }

    public static void updateRecord(UUID uuid, Gamemode gamemode, int score) {
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "UPDATE scores SET " + gamemode.toString() + " = ? WHERE uuid = ?"
        )) {
            stmt.setInt(1, score);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static LinkedHashMap<UUID, Integer> getTopScores(Gamemode gamemode) throws SQLException {
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "SELECT uuid, " + gamemode.toString() + " FROM scores ORDER BY ? DESC LIMIT 10"
        )) {
            stmt.setString(1, gamemode.toString());
            ResultSet result = stmt.executeQuery();
            Map<UUID, Integer> topScores = new HashMap<>();
            while (result.next()) {
                topScores.put(UUID.fromString(result.getString("uuid")), result.getInt(gamemode.toString()));
            }
            // For some reason, the result ordering isn't ordered. So we have to order it ourselves.
            ArrayList<UUID> uuids = new ArrayList<>(topScores.keySet());
            uuids.sort((o1, o2) -> {
                int score1 = topScores.get(o1);
                int score2 = topScores.get(o2);
                return Integer.compare(score2, score1);
            });
            LinkedHashMap<UUID, Integer> topScoresOrdered = new LinkedHashMap<>();
            for (UUID uuid : uuids) {
                topScoresOrdered.put(uuid, topScores.get(uuid));
            }
            return topScoresOrdered;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error while getting top scores from database!");
        }
    }

    public static boolean isSupported(Gamemode gamemode) {
        return supportedGamemodes.contains(gamemode);
    }

    public static Gamemode[] getSupportedGamemodes() {
        return supportedGamemodes.toArray(new Gamemode[0]);
    }
}
