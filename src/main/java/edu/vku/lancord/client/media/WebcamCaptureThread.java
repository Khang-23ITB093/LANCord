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
    private Webcam webcam;

    public WebcamCaptureThread(UDPStreamSender sender) {
        this.sender = sender;
    }

    public void run() {
        running = true;
        webcam = Webcam.getDefault();
        if (webcam == null) {
            System.err.println("No webcam found!");
            return;
        }
        try {
            if (webcam.isOpen()) {
                webcam.close();
            }
            webcam.setViewSize(new java.awt.Dimension(TARGET_WIDTH, TARGET_HEIGHT));
            webcam.open();
        long frameDurationMs = 1000L / FPS_LIMIT;
        int frameIndex = 0;
        while (running) {
            long start = System.currentTimeMillis();
            BufferedImage frame = webcam.getImage();
            if (frame != null) {
                byte[] jpeg = toJpeg(frame, 0.6f); // 60% quality
                boolean isKeyframe = (frameIndex % 30 == 0); // every 30 frames
                try {
                    sender.sendWebcam(jpeg, isKeyframe);
                } catch (java.net.SocketException e) {
                    break; // Socket closed, exit gracefully
                } catch (IOException e) {
                    e.printStackTrace();
                }
                frameIndex++;
            }
            long elapsed = System.currentTimeMillis() - start;
            long sleep   = frameDurationMs - elapsed;
            if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
        }
        } finally {
            webcam.close();
        }
    }

    private byte[] toJpeg(BufferedImage img, float quality) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            var iter = ImageIO.getImageWritersByFormatName("jpeg");
            if (!iter.hasNext()) return new byte[0];
            var jpegWriter   = iter.next();
            var jpegParams   = jpegWriter.getDefaultWriteParam();
            jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            jpegParams.setCompressionQuality(quality);
            try (var ios = ImageIO.createImageOutputStream(baos)) {
                jpegWriter.setOutput(ios);
                jpegWriter.write(null, new IIOImage(img, null, null), jpegParams);
            }
            jpegWriter.dispose();
            return baos.toByteArray();
        } catch (IOException e) { return new byte[0]; }
    }

    public void stopCapture() {
        running = false;
        interrupt();
    }
}
