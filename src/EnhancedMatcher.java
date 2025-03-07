import java.util.*;

public class EnhancedMatcher {
    // List of song names; index in the list is the song ID
    private final Map<Integer, String> songs;
    // Database: map from fingerprint hash to list of DataPoints
    private final Map<Long, List<DataPoint>> hashDatabase;
    private final DatabaseManager dbManager;

    public EnhancedMatcher(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.songs = new HashMap<>();
        this.hashDatabase = dbManager.getFingerprints();

        // Load song IDs and names
        List<String> songNames = dbManager.getAllSongs();
        for (int i = 0; i < songNames.size(); i++) {
            songs.put(i + 1, songNames.get(i)); // SQLite IDs start at 1
        }
    }

    // Given a list of fingerprints from a recording, try to match against the database
    public MatchResult match(List<Long> recordingFingerprints) {
        // Map: songId -> (offset -> count)
        Map<Integer, Map<Integer, Integer>> offsetCounts = new HashMap<>();

        for (int recTime = 0; recTime < recordingFingerprints.size(); recTime++) {
            long hash = recordingFingerprints.get(recTime);
            List<DataPoint> matchPoints = hashDatabase.get(hash);

            if (matchPoints != null) {
                for (DataPoint dp : matchPoints) {
                    int songId = dp.getSongId();
                    int offset = dp.getTime() - recTime;

                    Map<Integer, Integer> songOffsets = offsetCounts.computeIfAbsent(songId, k -> new HashMap<>());
                    songOffsets.put(offset, songOffsets.getOrDefault(offset, 0) + 1);
                }
            }
        }

        // Find the song with the highest number of aligned matches
        int bestSongId = -1;
        int bestOffset = 0;
        int bestCount = 0;

        for (Map.Entry<Integer, Map<Integer, Integer>> entry : offsetCounts.entrySet()) {
            int songId = entry.getKey();
            Map<Integer, Integer> offsets = entry.getValue();

            for (Map.Entry<Integer, Integer> offsetEntry : offsets.entrySet()) {
                int offset = offsetEntry.getKey();
                int count = offsetEntry.getValue();

                if (count > bestCount) {
                    bestCount = count;
                    bestSongId = songId;
                    bestOffset = offset;
                }
            }
        }

        if (bestSongId >= 0 && bestCount >= 2) { // Minimum threshold for a match
            String songName = dbManager.getSongNameById(bestSongId);
            return new MatchResult(true, songName, bestCount, bestOffset);
        }

        return new MatchResult(false, "", 0, 0);
    }

    // Adds a song to the in-memory database (useful for newly added songs)
    public void addToMemory(int songId, String songName, List<Long> fingerprints) {
        songs.put(songId, songName);

        for (int time = 0; time < fingerprints.size(); time++) {
            long hash = fingerprints.get(time);
            DataPoint dp = new DataPoint(songId, time);
            hashDatabase.computeIfAbsent(hash, k -> new ArrayList<>()).add(dp);
        }
    }

    // Result class to provide more details about the match
    public static class MatchResult {
        private final boolean matched;
        private final String songName;
        private final int matchCount;
        private final int timeOffset;

        public MatchResult(boolean matched, String songName, int matchCount, int timeOffset) {
            this.matched = matched;
            this.songName = songName;
            this.matchCount = matchCount;
            this.timeOffset = timeOffset;
        }

        public boolean isMatched() {
            return matched;
        }

        public String getSongName() {
            return songName;
        }

        public int getMatchCount() {
            return matchCount;
        }

        public int getTimeOffset() {
            return timeOffset;
        }

        @Override
        public String toString() {
            if (matched) {
                return "Match found: " + songName + " with " + matchCount + " matching points";
            } else {
                return "No match found";
            }
        }
    }
}