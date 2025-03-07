import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.FieldKey;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class ShazamApp {
    private static final AudioFingerprinter fingerprinter = new AudioFingerprinter();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("Audio Fingerprinting System");
        System.out.println("==========================");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = getUserChoice();

            switch (choice) {
                case 1: // Add a song
                    addSong();
                    break;
                case 2: // Identify from microphone
                    identifyFromMic();
                    break;
                case 3: // Identify from file
                    identifyFromFile();
                    break;
                case 4: // List all songs
                    listSongs();
                    break;
                case 5: // Add multiple songs from directory
                    addSongsFromDirectory();
                    break;
                case 0: // Exit
                    running = false;
                    fingerprinter.close();
                    System.out.println("Thank you for using the Audio Fingerprinting System!");
                    break;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\nMenu:");
        System.out.println("1. Add a song to database");
        System.out.println("2. Identify song (from microphone)");
        System.out.println("3. Identify song (from file)");
        System.out.println("4. List all songs in database");
        System.out.println("5. Add multiple songs from directory");
        System.out.println("0. Exit");
        System.out.print("\nEnter your choice: ");
    }

    private static int getUserChoice() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void addSong() {
        System.out.print("Enter the path to the audio file: ");
        String filepath = scanner.nextLine();

        if (!Files.exists(Paths.get(filepath))) {
            System.out.println("Error: File does not exist.");
            return;
        }

        System.out.print("Enter the song name: ");
        String songName = scanner.nextLine();

        boolean success = fingerprinter.addSong(filepath, songName);
        if (success) {
            System.out.println("Song added successfully!");
        } else {
            System.out.println("Failed to add song.");
        }
    }

    private static void addSongsFromDirectory() {
        System.out.print("Enter the directory path containing audio files: ");
        String directoryPath = scanner.nextLine();

        Path directory = Paths.get(directoryPath);
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            System.out.println("Error: Invalid directory path.");
            return;
        }

        try {
            int count = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.{wav,mp3}")) {
                for (Path path : stream) {
                    String filename = path.getFileName().toString();
                    String songName;
                    String fileToProcess = path.toString();
                    File tempFile = null;

                    // Extract metadata for MP3 files
                    if (filename.toLowerCase().endsWith(".mp3")) {
                        try {
                            // Using JAudiotagger library to extract metadata
                            AudioFile audioFile = AudioFileIO.read(path.toFile());
                            Tag tag = audioFile.getTag();

                            String title = "";
                            String artist = "";

                            // Get title and artist from metadata if available
                            if (tag != null) {
                                title = tag.getFirst(FieldKey.TITLE);
                                artist = tag.getFirst(FieldKey.ARTIST);
                            }

                            // Format song name as "Artist - Title" if both are available
                            if (title != null && !title.isEmpty()) {
                                if (artist != null && !artist.isEmpty()) {
                                    songName = artist + " - " + title;
                                } else {
                                    songName = title;
                                }
                            } else {
                                // Fallback to filename without extension
                                songName = filename.substring(0, filename.lastIndexOf('.'));
                            }

                            // Convert MP3 to WAV temporarily if needed for processing
                            tempFile = convertMp3ToWav(path.toFile());
                            if (tempFile != null) {
                                fileToProcess = tempFile.getAbsolutePath();
                            }
                        } catch (Exception ex) {
                            System.out.println("Error reading MP3 metadata for " + filename + ": " + ex.getMessage());
                            songName = filename.substring(0, filename.lastIndexOf('.'));
                        }
                    } else {
                        // For WAV files, use the filename approach
                        songName = filename.substring(0, filename.lastIndexOf('.'));
                    }

                    try {
                        System.out.println("Processing: " + filename + " (Song name: " + songName + ")");
                        if (fingerprinter.addSong(fileToProcess, songName)) {
                            count++;
                        }
                    } finally {
                        // Clean up temp file if it was created
                        if (tempFile != null && tempFile.exists()) {
                            tempFile.delete();
                        }
                    }
                }
            }
            System.out.println("Added " + count + " songs from directory.");
        } catch (IOException e) {
            System.out.println("Error reading directory: " + e.getMessage());
        }
    }

    private static void identifyFromMic() {
        System.out.print("Enter recording duration in seconds (default 10): ");
        String input = scanner.nextLine();

        int durationSecs = 10; // Default
        if (!input.isEmpty()) {
            try {
                durationSecs = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input, using default 10 seconds.");
            }
        }

        EnhancedMatcher.MatchResult result = fingerprinter.identifySong(durationSecs * 1000);
        System.out.println("\nResult: " + result);
    }

    private static void identifyFromFile() {
        System.out.print("Enter the path to the audio file: ");
        String filepath = scanner.nextLine();

        if (!Files.exists(Paths.get(filepath))) {
            System.out.println("Error: File does not exist.");
            return;
        }

        EnhancedMatcher.MatchResult result = fingerprinter.identifyFile(filepath);
        System.out.println("\nResult: " + result);
    }

    private static void listSongs() {
        List<String> songs = fingerprinter.listSongs();

        if (songs.isEmpty()) {
            System.out.println("No songs in the database yet.");
        } else {
            System.out.println("\nSongs in the database:");
            for (int i = 0; i < songs.size(); i++) {
                System.out.println((i + 1) + ". " + songs.get(i));
            }
        }
    }

    private static File convertMp3ToWav(File mp3File) {
        try {
            // Create temp file for WAV output
            File wavFile = File.createTempFile("temp_", ".wav");

            // Get AudioInputStream from MP3 file
            AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(mp3File);
            AudioFormat baseFormat = mp3Stream.getFormat();

            // Convert to PCM format that can be processed
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);

            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, mp3Stream);

            // Write to WAV file
            AudioSystem.write(pcmStream, AudioFileFormat.Type.WAVE, wavFile);

            // Close streams
            pcmStream.close();
            mp3Stream.close();

            return wavFile;
        } catch (Exception e) {
            System.out.println("Error converting MP3 to WAV: " + e.getMessage());
            return null;
        }
    }
}