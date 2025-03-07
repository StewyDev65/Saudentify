// Harvester.java
import java.util.*;
import java.lang.Math;

public class Harvester {
    public static final int CHUNK_SIZE = 4096;
    // Frequency ranges (in FFT bin indices) used for fingerprinting.
    // Here we assume an upper limit of 300 for demonstration.
    public static final int[] RANGE = new int[] {40, 80, 120, 180, 300};

    // Process the raw audio data and return a list of fingerprint hashes.
    public List<Long> processAudio(byte[] audio) {
        int totalSize = audio.length;
        int amountPossible = totalSize / CHUNK_SIZE;
        List<Long> fingerprints = new ArrayList<>();

        for (int t = 0; t < amountPossible; t++) {
            // Create a complex array from one chunk.
            Complex[] complex = new Complex[CHUNK_SIZE];
            for (int i = 0; i < CHUNK_SIZE; i++) {
                // Note: byte is signed; cast to int for sample value.
                int sample = audio[t * CHUNK_SIZE + i];
                complex[i] = new Complex((double) sample, 0);
            }
            // Perform FFT on the chunk.
            Complex[] fftResult = FFT.fft(complex);

            // Extract key points for fingerprinting.
            // For each of the four ranges, find the frequency bin with the highest magnitude.
            int[] keyPoints = new int[4];
            double[] maxMag = new double[4];
            Arrays.fill(maxMag, 0);

            // Only process bins from index 40 up to 300 (or the available half-spectrum).
            int start = RANGE[0];
            int end = Math.min(RANGE[RANGE.length - 1], fftResult.length / 2);
            for (int i = start; i < end; i++) {
                double mag = Math.log(fftResult[i].abs() + 1);
                // Determine in which range this bin falls.
                int rangeIndex = 0;
                for (int r = 0; r < RANGE.length - 1; r++) {
                    if (i >= RANGE[r] && i < RANGE[r+1]) {
                        rangeIndex = r;
                        break;
                    }
                }
                if (mag > maxMag[rangeIndex]) {
                    maxMag[rangeIndex] = mag;
                    keyPoints[rangeIndex] = i;
                }
            }
            // Create a hash from the 4 key points.
            long hash = hashPoints(keyPoints);
            fingerprints.add(hash);
        }
        return fingerprints;
    }

    // Using a simple damping factor as in the guide.
    private static final int FUZ_FACTOR = 2;

    // Create a hash value from 4 frequency key points.
    public long hashPoints(int[] points) {
        // Ensure points[] has length 4.
        if (points.length < 4) return 0;
        long p1 = points[0];
        long p2 = points[1];
        long p3 = points[2];
        long p4 = points[3];
        return (p4 - (p4 % FUZ_FACTOR)) * 100000000L +
                (p3 - (p3 % FUZ_FACTOR)) * 100000L +
                (p2 - (p2 % FUZ_FACTOR)) * 100 +
                (p1 - (p1 % FUZ_FACTOR));
    }
}
