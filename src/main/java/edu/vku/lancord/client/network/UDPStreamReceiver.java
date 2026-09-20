package edu.vku.lancord.client.network;

import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class UDPStreamReceiver {
    private String multicastIp;
    private int port;
    private volatile boolean receiving = false;
    private Thread receiveThread;
    private MulticastSocket socket;
    private Consumer<Image> onFrameReceived;

    public UDPStreamReceiver(String multicastIp, int port) {
        this.multicastIp = multicastIp;
        this.port = port;
    }

    public void setOnFrameReceived(Consumer<Image> callback) {
        this.onFrameReceived = callback;
    }

    public void start() {
        if (receiving) return;
        receiving = true;
        receiveThread = new Thread(() -> {
            try {
                socket = new MulticastSocket(port);
                InetAddress group = InetAddress.getByName(multicastIp);
                socket.joinGroup(group);

                byte[] buffer = new byte[65000];
                Map<Integer, byte[]> chunkMap = new HashMap<>();
                int expectedChunks = -1;
                int currentBytesReceived = 0;

                while (receiving) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    byte[] data = packet.getData();
                    int length = packet.getLength();
                    
                    int totalChunks = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                    int chunkIndex = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
                    
                    if (expectedChunks == -1 || expectedChunks != totalChunks) {
                        expectedChunks = totalChunks;
                        chunkMap.clear();
                        currentBytesReceived = 0;
                    }
                    
                    byte[] chunkData = new byte[length - 8];
                    System.arraycopy(data, 8, chunkData, 0, length - 8);
                    chunkMap.put(chunkIndex, chunkData);
                    currentBytesReceived += chunkData.length;
                    
                    if (chunkMap.size() == totalChunks) {
                        // All chunks received, assemble
                        byte[] imageBytes = new byte[currentBytesReceived];
                        int offset = 0;
                        for (int i = 0; i < totalChunks; i++) {
                            byte[] c = chunkMap.get(i);
                            if (c != null) {
                                System.arraycopy(c, 0, imageBytes, offset, c.length);
                                offset += c.length;
                            }
                        }
                        
                        try {
                            Image image = new Image(new ByteArrayInputStream(imageBytes));
                            if (onFrameReceived != null) {
                                onFrameReceived.accept(image);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        
                        // Reset for next frame
                        chunkMap.clear();
                        expectedChunks = -1;
                        currentBytesReceived = 0;
                    }
                }
            } catch (Exception e) {
                if (receiving) {
                    e.printStackTrace();
                }
            }
        });
        receiveThread.start();
    }

    public void stop() {
        receiving = false;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(InetAddress.getByName(multicastIp));
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
