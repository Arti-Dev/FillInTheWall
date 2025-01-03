package com.articreep.fillinthewall;

import com.articreep.fillinthewall.gamemode.Gamemode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class Database {
    private static final HashSet<Gamemode> supportedGamemodes = new HashSet<>();
    private static boolean offlineMode = false;

    static {
        supportedGamemodes.add(Gamemode.SCORE_ATTACK);
        supportedGamemodes.add(Gamemode.RUSH_SCORE_ATTACK);
        supportedGamemodes.add(Gamemode.MARATHON);
        supportedGamemodes.add(Gamemode.SPRINT);
        supportedGamemodes.add(Gamemode.MEGA);
    }

    protected static void setOfflineMode(boolean boo) {
        offlineMode = boo;
    }

    private static void addPlayerToScores(UUID uuid) throws SQLException {
        // Adds a new UUID into the database
        try (Connection connection = FillInTheWall.getSQLConnection();
             PreparedStatement stmt1 = connection.prepareStatement(
                "INSERT INTO scores(uuid) VALUES(?)"
        )) {
            stmt1.setString(1, uuid.toString());
            stmt1.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error while adding new user to database!");
        }
    }

    private static void addPlayerToHotbars(UUID uuid) throws SQLException {
        try (Connection connection = FillInTheWall.getSQLConnection();
             PreparedStatement stmt1 = connection.prepareStatement(
                     "INSERT INTO hotbars(uuid) VALUES(?)"
             )) {
            stmt1.setString(1, uuid.toString());
            stmt1.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
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
                addPlayerToScores(uuid);
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
                "SELECT uuid, " + gamemode.toString() + " FROM scores ORDER BY " + gamemode.toString() + " DESC LIMIT 10"
        )) {
            ResultSet result = stmt.executeQuery();
            LinkedHashMap<UUID, Integer> topScoresOrdered = new LinkedHashMap<>();
            while (result.next()) {
                topScoresOrdered.put(UUID.fromString(result.getString("uuid")), result.getInt(gamemode.toString()));
            }
            return topScoresOrdered;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error while getting top scores from database!");
        }
    }

    public static LinkedHashMap<UUID, Integer> getTopTimes(Gamemode gamemode) throws SQLException {
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "SELECT uuid, " + gamemode.toString() + " FROM scores ORDER BY " + gamemode.toString() + " ASC LIMIT 10"
        )) {
            ResultSet result = stmt.executeQuery();
            LinkedHashMap<UUID, Integer> topScoresOrdered = new LinkedHashMap<>();
            while (result.next()) {
                topScoresOrdered.put(UUID.fromString(result.getString("uuid")), result.getInt(gamemode.toString()));
            }
            return topScoresOrdered;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error while getting top times from database!");
        }
    }

    public static void updateHotbar(UUID uuid, String hotbar) {
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "UPDATE hotbars SET hotbar = ? WHERE uuid = ?"
        )) {
            stmt.setString(1, hotbar);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String getHotbar(UUID uuid) throws SQLException {
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "SELECT hotbar FROM hotbars WHERE uuid = ?"
        )) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                return result.getString("hotbar");
            } else {
                // If they didn't exist before, add them!
                addPlayerToHotbars(uuid);
                return PlayingField.DEFAULT_HOTBAR;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error while getting user hotbar from database!");
        }
    }



    public static boolean isSupported(Gamemode gamemode) {
        return supportedGamemodes.contains(gamemode);
    }

    public static Gamemode[] getSupportedGamemodes() {
        return supportedGamemodes.toArray(new Gamemode[0]);
    }
}
