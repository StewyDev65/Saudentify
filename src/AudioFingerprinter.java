import java.io.*;
import java.util.*;

public class AudioFingerprinter {
    private final DatabaseManager dbManager;
    private final AudioFileProcessor fileProcessor;
    private final Harvester harvester;
    private final EnhancedMatcher matcher;

    public AudioFingerprinter() {
        this.dbManager = new DatabaseManager();
        this.fileProcessor = new AudioFileProcessor();
        this.harvester = new Harvester();
        this.matcher = new EnhancedMatcher(dbManager);
    }

    /**
     * Adds a song to the database by fingerprinting an audio file
     * @param filepath Path to the audio file
     * @param songName Name to identify the song
     * @return True if successfully added
     */
    public boolean addSong(String filepath, String songName) {
        try {
            System.out.println("Processing file: " + filepath);
            List<Long> fingerprints = fileProcessor.processFile(filepath);
            System.out.println("Generated " + fingerprints.size() + " fingerprints");

            // Add to database
            int songId = dbManager.addSong(songName, filepath);
            if (songId > 0) {
                dbManager.addFingerprints(songId, fingerprints);
                // Also add to in-memory store for immediate matching
                matcher.addToMemory(songId, songName, fingerprints);
                System.out.println("Successfully added song: " + songName);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Error adding song: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Records audio from microphone and tries to match it
     * @param durationMs Recording duration in milliseconds
     * @return Match result
     */
    public EnhancedMatcher.MatchResult identifySong(int durationMs) {
        try {
            System.out.println("Recording audio for " + (durationMs / 1000) + " seconds...");
            AudioRecorder recorder = new AudioRecorder();
            recorder.startRecording();

            try {
                Thread.sleep(durationMs);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            recorder.stopRecording();
            System.out.println("Recording complete, processing...");

            byte[] audioData = recorder.getAudioData();
            List<Long> fingerprints = harvester.processAudio(audioData);

            System.out.println("Generated " + fingerprints.size() + " fingerprints from recording");
            System.out.println("Matching against database...");

            return matcher.match(fingerprints);
        } catch (Exception e) {
            System.err.println("Error during recording/matching: " + e.getMessage());
            e.printStackTrace();
            return new EnhancedMatcher.MatchResult(false, "", 0, 0);
        }
    }

    /**
     * Identifies a song from an audio file
     * @param filepath Path to the audio file
     * @return Match result
     */
    public EnhancedMatcher.MatchResult identifyFile(String filepath) {
        try {
            System.out.println("Processing file for identification: " + filepath);
            List<Long> fingerprints = fileProcessor.processFile(filepath);

            System.out.println("Generated " + fingerprints.size() + " fingerprints");
            System.out.println("Matching against database...");

            return matcher.match(fingerprints);
        } catch (Exception e) {
            System.err.println("Error identifying file: " + e.getMessage());
            e.printStackTrace();
            return new EnhancedMatcher.MatchResult(false, "", 0, 0);
        }
    }

    /**
     * Lists all songs in the database
     * @return List of song names
     */
    public List<String> listSongs() {
        return dbManager.getAllSongs();
    }

    /**
     * Closes resources
     */
    public void close() {
        dbManager.close();
    }
}