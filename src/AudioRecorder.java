// AudioRecorder.java
import javax.sound.sampled.*;
import java.io.*;

public class AudioRecorder implements Runnable {
    private TargetDataLine line;
    private ByteArrayOutputStream out;
    private boolean running = false;
    private AudioFormat format;

    public AudioRecorder() {
        format = getFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    private AudioFormat getFormat() {
        float sampleRate = 44100;
        int sampleSizeInBits = 8; // Using 8-bit samples (as in the guide)
        int channels = 1;         // Mono
        boolean signed = true;
        boolean bigEndian = true;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    public void startRecording() {
        running = true;
        line.start();
        out = new ByteArrayOutputStream();
        new Thread(this).start();
    }

    public void stopRecording() {
        running = false;
        line.stop();
        line.close();
    }

    public byte[] getAudioData() {
        return out.toByteArray();
    }

    @Override
    public void run() {
        byte[] buffer = new byte[4096];
        while (running) {
            int count = line.read(buffer, 0, buffer.length);
            if (count > 0) {
                out.write(buffer, 0, count);
            }
        }
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
