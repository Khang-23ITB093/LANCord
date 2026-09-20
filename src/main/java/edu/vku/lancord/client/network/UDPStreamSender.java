package edu.vku.lancord.client.network;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class UDPStreamSender {
    private String multicastIp;
    private int port;
    private volatile boolean streaming = false;
    private Thread streamThread;
    private MulticastSocket socket;

    public UDPStreamSender(String multicastIp, int port) {
        this.multicastIp = multicastIp;
        this.port = port;
    }

    public void start() {
        if (streaming) return;
        streaming = true;
        streamThread = new Thread(() -> {
            try {
                socket = new MulticastSocket();
                InetAddress group = InetAddress.getByName(multicastIp);
                Robot robot = new Robot();
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

                while (streaming) {
                    BufferedImage capture = robot.createScreenCapture(screenRect);
                    
                    // Compress to JPEG
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(capture, "jpg", baos);
                    byte[] imageBytes = baos.toByteArray();

                    // Note: In real-world, we'd need to split into <64KB chunks for UDP.
                    // For phase 1 LAN demo, we might just send it if it's small enough,
                    // but screen captures are large. Let's do a basic chunking.
                    
                    int maxChunkSize = 60000;
                    int totalChunks = (int) Math.ceil((double) imageBytes.length / maxChunkSize);
                    
                    for (int i = 0; i < totalChunks; i++) {
                        int offset = i * maxChunkSize;
                        int length = Math.min(imageBytes.length - offset, maxChunkSize);
                        
                        // Header: [totalChunks(4 bytes)] [chunkIndex(4 bytes)] [data...]
                        byte[] chunk = new byte[8 + length];
                        chunk[0] = (byte) (totalChunks >> 24);
                        chunk[1] = (byte) (totalChunks >> 16);
                        chunk[2] = (byte) (totalChunks >> 8);
                        chunk[3] = (byte) (totalChunks);
                        
                        chunk[4] = (byte) (i >> 24);
                        chunk[5] = (byte) (i >> 16);
                        chunk[6] = (byte) (i >> 8);
                        chunk[7] = (byte) (i);
                        
                        System.arraycopy(imageBytes, offset, chunk, 8, length);
                        
                        DatagramPacket packet = new DatagramPacket(chunk, chunk.length, group, port);
                        socket.send(packet);
                    }
                    
                    Thread.sleep(100); // ~10fps
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        streamThread.start();
    }

    public void stop() {
        streaming = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
