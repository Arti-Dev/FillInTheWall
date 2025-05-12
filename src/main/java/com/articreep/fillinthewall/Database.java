package com.articreep.fillinthewall;

import com.articreep.fillinthewall.game.PlayingField;
import com.articreep.fillinthewall.gamemode.Gamemode;
import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class Database {
    private static final HashSet<Gamemode> supportedGamemodes = new HashSet<>();
    private final static MysqlDataSource dataSource = new MysqlConnectionPoolDataSource();
    private static boolean offlineMode = false;

    private Database() {
        // prevent instantiation
    }

    static {
        supportedGamemodes.add(Gamemode.SCORE_ATTACK);
        supportedGamemodes.add(Gamemode.RUSH_SCORE_ATTACK);
        supportedGamemodes.add(Gamemode.MARATHON);
        supportedGamemodes.add(Gamemode.SPRINT);
        supportedGamemodes.add(Gamemode.MEGA);
    }

    public static boolean loadSQL() {
        FileConfiguration config = FillInTheWall.getInstance().getConfig();
        dataSource.setServerName(config.getString("database.host"));
        dataSource.setPortNumber(config.getInt("database.port"));
        dataSource.setDatabaseName(config.getString("database.database"));
        dataSource.setUser(config.getString("database.username"));
        dataSource.setPassword(config.getString("database.password"));


        // Test the connection
        try {
            Connection conn = dataSource.getConnection();
            if (!conn.isValid(1)) {
                throw new SQLException("Could not establish database connection.");
            }
        } catch (SQLException e) {
            FillInTheWall.getInstance().getSLF4JLogger().error("FillInTheWall: Could not establish database connection. " +
                    "Please make sure you are using a MySQL server and that the config.yml is set up correctly." +
                    "\nThe plugin will still work, but leaderboards will be disabled, scores will not submit, and player-saved " +
                    "hotbars will not load");
            e.printStackTrace();
            offlineMode = true;
            return false;
        }

        String sqlScores = "CREATE TABLE IF NOT EXISTS scores(" +
                "uuid CHAR(36) NOT NULL," +
                "SCORE_ATTACK INT DEFAULT 0 NOT NULL," +
                "RUSH_SCORE_ATTACK INT DEFAULT 0 NOT NULL," +
                "MARATHON INT DEFAULT 0 NOT NULL," +
                "SPRINT INT DEFAULT 12000 NOT NULL," +
                "MEGA INT DEFAULT 12000 NOT NULL," +
                "PRIMARY KEY (uuid));";
        String sqlHotbars = "CREATE TABLE IF NOT EXISTS hotbars(" +
                "uuid CHAR(36) NOT NULL," +
                "hotbar CHAR(9) DEFAULT ? NOT NULL," +
                "FOREIGN KEY (uuid) REFERENCES scores(uuid) ON DELETE CASCADE);";
        String sqlPlayerInfo = "CREATE TABLE IF NOT EXISTS playerInfo(" +
                "uuid CHAR(36) NOT NULL," +
                "newcomer BIT DEFAULT 1 NOT NULL," +
                "xp INT DEFAULT 0 NOT NULL," +
                "FOREIGN KEY (uuid) REFERENCES scores(uuid) ON DELETE CASCADE);";
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(sqlScores);
            stmt.executeUpdate();
            stmt = conn.prepareStatement(sqlHotbars);
            stmt.setString(1, PlayingField.DEFAULT_HOTBAR);
            stmt.executeUpdate();
            stmt = conn.prepareStatement(sqlPlayerInfo);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        offlineMode = false;
        return true;
    }

    public static Connection getSQLConnection() throws SQLException {
        return dataSource.getConnection();
    }

    protected static void setOfflineMode(boolean boo) {
        offlineMode = boo;
    }

    public static boolean isOfflineMode() {
        return offlineMode;
    }

    private static void addPlayerToScores(UUID uuid) throws SQLException {
        // Adds a new UUID into the database
        try (Connection connection = getSQLConnection();
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
        try (Connection connection = getSQLConnection();
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
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
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
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
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
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
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
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
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
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
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
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
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

    public static boolean isNewcomer(UUID uuid) throws SQLException {
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "SELECT newcomer FROM playerInfo WHERE uuid = ?"
        )) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                return result.getBoolean("newcomer");
            } else {
                // If they didn't exist before, add them!
                addPlayerInfo(uuid);
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error while checking if user is newcomer!");
        }
    }

    public static void setNewcomer(UUID uuid, boolean newcomer) {
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "UPDATE playerInfo SET newcomer = ? WHERE uuid = ?"
        )) {
            stmt.setBoolean(1, newcomer);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void addPlayerInfo(UUID uuid) {
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO playerInfo(uuid, newcomer) VALUES(?, ?)"
        )) {
            stmt.setString(1, uuid.toString());
            stmt.setBoolean(2, true);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static int getXP(UUID uuid) throws SQLException {
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "SELECT xp FROM playerInfo WHERE uuid = ?"
        )) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                return result.getInt("xp");
            } else {
                // If they didn't exist before, add them!
                addPlayerInfo(uuid);
                return 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SQLException("Error while getting user XP from database!");
        }
    }

    public static void setXP(UUID uuid, int amount) {
        try (Connection connection = getSQLConnection(); PreparedStatement stmt = connection.prepareStatement(
                "UPDATE playerInfo SET xp = ? WHERE uuid = ?"
        )) {
            stmt.setInt(1, amount);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean isSupported(Gamemode gamemode) {
        return supportedGamemodes.contains(gamemode);
    }

    public static Gamemode[] getSupportedGamemodes() {
        return supportedGamemodes.toArray(new Gamemode[0]);
    }
}
