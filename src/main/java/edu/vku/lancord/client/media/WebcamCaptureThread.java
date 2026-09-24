package edu.vku.lancord.client.media;

import com.github.sarxos.webcam.Webcam;
import edu.vku.lancord.client.udp.UDPStreamSender;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class WebcamCaptureThread extends Thread {
    private static final int TARGET_WIDTH  = 640;
    private static final int TARGET_HEIGHT = 480;
    private static final int FPS_LIMIT     = 15;

    private final UDPStreamSender sender;
    private volatile boolean running;
    // volatile so stopCapture() on the main thread can see and close this immediately
    private volatile Webcam webcam;

    public WebcamCaptureThread(UDPStreamSender sender) {
        this.sender = sender;
        setDaemon(true); // Don't block JVM exit
    }

    @Override
    public void run() {
        running = true;
        Webcam cam = Webcam.getDefault();
        if (cam == null) {
            System.err.println("[WebcamCaptureThread] No webcam found!");
            return;
        }
        // Store in volatile field BEFORE opening, so stopCapture() can close it
        webcam = cam;

        try {
            if (cam.isOpen()) cam.close();
            cam.setViewSize(new java.awt.Dimension(TARGET_WIDTH, TARGET_HEIGHT));
            cam.open();

            long frameDurationMs = 1000L / FPS_LIMIT;
            int frameIndex = 0;

            while (running) {
                long start = System.currentTimeMillis();
                BufferedImage frame = cam.getImage();
                if (frame != null) {
                    byte[] jpeg = toJpeg(frame, 0.6f);
                    boolean isKeyframe = (frameIndex % 30 == 0);
                    try {
                        sender.sendWebcam(jpeg, isKeyframe);
                    } catch (java.net.SocketException e) {
                        break; // Socket closed — call ended, exit cleanly
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    frameIndex++;
                }
                long elapsed = System.currentTimeMillis() - start;
                long sleep = frameDurationMs - elapsed;
                if (sleep > 0) {
                    try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
                }
            }
        } finally {
            // Always close the hardware, no matter how we exit
            Webcam w = webcam;
            if (w != null && w.isOpen()) {
                w.close();
            }
            webcam = null;
        }
    }

    /**
     * Stops capture and immediately releases the webcam hardware.
     * Safe to call from any thread.
     */
    public void stopCapture() {
        running = false;
        // Close webcam hardware directly — don't wait for the loop to iterate
        Webcam w = webcam;
        if (w != null && w.isOpen()) {
            w.close();
        }
        interrupt();
    }

    private byte[] toJpeg(BufferedImage img, float quality) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            var iter = ImageIO.getImageWritersByFormatName("jpeg");
            if (!iter.hasNext()) return new byte[0];
            var jpegWriter = iter.next();
            var jpegParams = jpegWriter.getDefaultWriteParam();
            jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            jpegParams.setCompressionQuality(quality);
            try (var ios = ImageIO.createImageOutputStream(baos)) {
                jpegWriter.setOutput(ios);
                jpegWriter.write(null, new IIOImage(img, null, null), jpegParams);
            }
            jpegWriter.dispose();
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
