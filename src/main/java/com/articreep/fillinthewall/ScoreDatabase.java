package com.articreep.fillinthewall;

import com.articreep.fillinthewall.gamemode.Gamemode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreDatabase {

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


    public static int getRecord(UUID uuid, Gamemode gamemode) throws SQLException {
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "SELECT ? FROM scores WHERE uuid = ?"
        )) {
            stmt.setString(1, gamemode.toString());
            stmt.setString(2, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                return result.getInt(gamemode.toString());
            } else {
                // If they didn't exist before, add them!
                addPlayer(uuid);
                return 0;
            }
        }
    }

    public static void updateRecord(UUID uuid, Gamemode gamemode, int score) throws SQLException {
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "UPDATE scores SET ? = ? WHERE uuid = ?"
        )) {
            stmt.setString(1, gamemode.toString());
            stmt.setInt(2, score);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException("Error while saving score to database");
        }
    }

    public static Map<UUID, Integer> getTopScores(Gamemode gamemode) throws SQLException {
        try (Connection connection = FillInTheWall.getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "SELECT uuid, ? FROM scores ORDER BY ? DESC LIMIT 10"
        )) {
            stmt.setString(1, gamemode.toString());
            stmt.setString(2, gamemode.toString());
            ResultSet result = stmt.executeQuery();
            Map<UUID, Integer> topScores = new LinkedHashMap<>();
            while (result.next()) {
                topScores.put(UUID.fromString(result.getString("uuid")), result.getInt(gamemode.toString()));
            }
            return topScores;
        } catch (SQLException e) {
            throw new SQLException("Error while getting top scores from database");
        }
    }
}
