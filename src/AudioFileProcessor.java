import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.util.Arrays;
import java.util.List;

public class AudioFileProcessor {
    private final Harvester harvester;

    public AudioFileProcessor() {
        this.harvester = new Harvester();
    }

    /**
     * Reads an audio file and processes it to generate fingerprints.
     * If the file is a FLAC file, it is first converted to PCM format.
     * @param filePath Path to the audio file
     * @return List of fingerprint hashes
     */
    public List<Long> processFile(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Audio file not found: " + filePath);
        }

        AudioInputStream audioInputStream;
        // Check if file is a FLAC file
        if (filePath.toLowerCase().endsWith(".flac")) {
            // Convert FLAC file to PCM using a conversion method or third-party library
            audioInputStream = convertFlacToPcm(file);
        } else {
            audioInputStream = AudioSystem.getAudioInputStream(file);
        }

        AudioFormat format = audioInputStream.getFormat();

        // Convert to the format expected by the Harvester
        AudioFormat targetFormat = new AudioFormat(
                44100, // Sample rate
                8,     // Sample size in bits
                1,     // Channels (mono)
                true,  // Signed
                true   // Big endian
        );

        AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream);

        // Read all bytes from the audio file
        byte[] audioData = readAllBytes(convertedStream);

        // Process the audio data to generate fingerprints
        return harvester.processAudio(audioData);
    }

    /**
     * Converts a FLAC file to a PCM AudioInputStream.
     * This method assumes the existence of a FLAC decoding library such as JFLAC.
     * You need to add the appropriate library to your project.
     *
     * @param flacFile The FLAC file to convert.
     * @return AudioInputStream in PCM format.
     * @throws Exception if conversion fails.
     */
    private AudioInputStream convertFlacToPcm(File flacFile) throws Exception {
        // Build the ffmpeg command:
        // -i [input file]
        // -f wav            -> output in WAV format
        // -ar 44100         -> sample rate 44100 Hz
        // -ac 1             -> mono channel
        // -sample_fmt s8    -> 8-bit signed samples
        // pipe:1           -> write output to stdout
        List<String> command = Arrays.asList(
                "ffmpeg",
                "-i", flacFile.getAbsolutePath(),
                "-f", "wav",
                "-ar", "44100",
                "-ac", "1",
                "-sample_fmt", "s8",
                "pipe:1"
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        // Merge ffmpeg's stderr into stdout to capture all output
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Wrap the ffmpeg output in a BufferedInputStream.
        // ffmpeg writes a WAV header which AudioSystem can use to create an AudioInputStream.
        BufferedInputStream bis = new BufferedInputStream(process.getInputStream());
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bis);

        // Optionally, you may want to handle process termination and errors.
        // For example, you could spawn a thread to wait for the process:
        new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("ffmpeg exited with error code: " + exitCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return audioInputStream;
    }


    private byte[] readAllBytes(AudioInputStream audioInputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = audioInputStream.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        return out.toByteArray();
    }
}
