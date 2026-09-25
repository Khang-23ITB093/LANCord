package edu.vku.lancord.client.media;

import javax.sound.sampled.*;
import java.util.concurrent.*;

public class AudioPlaybackRenderer {
    private SourceDataLine line;
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(50);
    private Thread playbackThread;
    private volatile boolean running = false;

    public AudioPlaybackRenderer() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, AudioCaptureThread.AUDIO_FORMAT);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(AudioCaptureThread.AUDIO_FORMAT);
        line.start();
        
        running = true;
        playbackThread = new Thread(this::playbackLoop, "Audio-Playback-Thread");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    // Called from UDPStreamReceiver dispatcher (non-UI thread)
    public void enqueue(byte[] pcmData) {
        queue.offer(pcmData); // non-blocking; drops if full (jitter protection)
    }

    // Run on a dedicated playback thread
    private void playbackLoop() {
        while (running) {
            try {
                byte[] data = queue.take();
                line.write(data, 0, data.length);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void close() {
        running = false;
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
        if (line != null) {
            line.stop();
            line.close();
        }
    }
}
