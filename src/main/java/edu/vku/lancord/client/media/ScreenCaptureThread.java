package edu.vku.lancord.client.media;

import edu.vku.lancord.client.udp.UDPStreamSender;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ScreenCaptureThread extends Thread {
    private static final int FPS_LIMIT = 10; // 10 FPS for screen sharing
    private static final float JPEG_QUALITY = 0.5f; // 50% quality

    private final UDPStreamSender sender;
    private volatile boolean running;
    private Robot robot;
    private final Rectangle screenRect;

    public ScreenCaptureThread(UDPStreamSender sender) throws AWTException {
        this.sender     = sender;
        this.robot      = new Robot();
        this.screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    public void run() {
        running = true;
        long frameDurationMs = 1000L / FPS_LIMIT;
        int frameIndex = 0;
        while (running) {
            long start = System.currentTimeMillis();
            BufferedImage screenshot = robot.createScreenCapture(screenRect);
            // Scale down to 1280x720 to reduce data size
            BufferedImage scaled = scaleImage(screenshot, 1280, 720);
            byte[] jpeg      = toJpeg(scaled, JPEG_QUALITY);
            boolean keyframe = (frameIndex % 20 == 0);
            try {
                sender.sendScreen(jpeg, keyframe);
            } catch (java.net.SocketException e) {
                break; // Socket closed, exit gracefully
            } catch (IOException e) {
                e.printStackTrace();
            }
            frameIndex++;
            long elapsed = System.currentTimeMillis() - start;
            long sleep   = frameDurationMs - elapsed;
            if (sleep > 0) try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
        }
    }

    private BufferedImage scaleImage(BufferedImage src, int w, int h) {
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = dest.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return dest;
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
