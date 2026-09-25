package edu.vku.lancord.client.media;

import edu.vku.lancord.client.udp.UDPStreamSender;
import javax.sound.sampled.*;
import java.util.Arrays;

public class AudioCaptureThread extends Thread {
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        16000f,    // sample rate: 16 kHz
        16,        // sample size in bits
        1,         // channels: mono
        2,         // frame size
        16000f,    // frame rate = sample rate
        false      // little-endian
    );

    private static final int CHUNK_MS   = 20;
    private static final int BUFFER_SIZE = (int)(AUDIO_FORMAT.getFrameRate()
                                                  * AUDIO_FORMAT.getFrameSize()
                                                  * CHUNK_MS / 1000);
    private final UDPStreamSender sender;
    private volatile boolean running;
    private TargetDataLine line;

    public AudioCaptureThread(UDPStreamSender sender) {
        this.sender = sender;
    }

    public void run() {
        running = true;
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(AUDIO_FORMAT, BUFFER_SIZE * 4);
            line.start();
            byte[] buf = new byte[BUFFER_SIZE];
            while (running) {
                int read = line.read(buf, 0, buf.length);
                if (read > 0) {
                    try {
                        sender.sendAudio(Arrays.copyOf(buf, read));
                    } catch (java.net.SocketException e) {
                        break; // Socket closed, exit gracefully
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        } finally {
            if (line != null) { line.stop(); line.close(); }
        }
    }

    public void stopCapture() {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        interrupt();
    }
}
