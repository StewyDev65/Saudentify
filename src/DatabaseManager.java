import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private Connection connection;

    public DatabaseManager() {
        // Initialize the database connection
        try {
            // Use SQLite for simplicity
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:fingerprints.db");
            initDatabase();
        } catch (Exception e) {
            System.err.println("Database connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initDatabase() {
        try (Statement stmt = connection.createStatement()) {
            // Create songs table
            stmt.execute("CREATE TABLE IF NOT EXISTS songs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "path TEXT, " +
                    "added_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Create fingerprints table
            stmt.execute("CREATE TABLE IF NOT EXISTS fingerprints (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "hash BIGINT NOT NULL, " +
                    "song_id INTEGER NOT NULL, " +
                    "time_offset INTEGER NOT NULL, " +
                    "FOREIGN KEY (song_id) REFERENCES songs(id))");

            // Create index on hash for faster lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_fingerprints_hash ON fingerprints (hash)");
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int addSong(String name, String path) {
        try (PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO songs (name, path) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, name);
            pstmt.setString(2, path);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding song: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    public void addFingerprints(int songId, List<Long> fingerprints) {
        try {
            // Use batch processing for better performance
            connection.setAutoCommit(false);

            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO fingerprints (hash, song_id, time_offset) VALUES (?, ?, ?)")) {

                for (int i = 0; i < fingerprints.size(); i++) {
                    pstmt.setLong(1, fingerprints.get(i));
                    pstmt.setInt(2, songId);
                    pstmt.setInt(3, i);
                    pstmt.addBatch();

                    // Execute in batches of 1000
                    if (i % 1000 == 0) {
                        pstmt.executeBatch();
                    }
                }

                pstmt.executeBatch(); // Execute any remaining
                connection.commit();
            }
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException e2) {
                e2.printStackTrace();
            }
            System.err.println("Error adding fingerprints: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public List<String> getAllSongs() {
        List<String> songs = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM songs ORDER BY id")) {

            while (rs.next()) {
                songs.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving songs: " + e.getMessage());
            e.printStackTrace();
        }
        return songs;
    }

    public Map<Long, List<DataPoint>> getFingerprints() {
        Map<Long, List<DataPoint>> fingerprints = new HashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT hash, song_id, time_offset FROM fingerprints")) {

            while (rs.next()) {
                long hash = rs.getLong("hash");
                int songId = rs.getInt("song_id");
                int timeOffset = rs.getInt("time_offset");

                DataPoint dataPoint = new DataPoint(songId, timeOffset);
                fingerprints.computeIfAbsent(hash, k -> new ArrayList<>()).add(dataPoint);
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving fingerprints: " + e.getMessage());
            e.printStackTrace();
        }
        return fingerprints;
    }

    public String getSongNameById(int id) {
        try (PreparedStatement pstmt = connection.prepareStatement(
                "SELECT name FROM songs WHERE id = ?")) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving song name: " + e.getMessage());
            e.printStackTrace();
        }
        return "Unknown";
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}