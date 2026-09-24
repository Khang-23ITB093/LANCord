package edu.vku.lancord.client.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.atomic.AtomicInteger;

public class UDPStreamSender {
    private static final int MAX_PAYLOAD = 1388; // 1400 - 12 (header)

    private final MulticastSocket socket;
    private final InetAddress group;
    private final int port;
    private volatile byte senderId;
    private final AtomicInteger seqIdCounter = new AtomicInteger(0);

    public UDPStreamSender(String multicastIp, int port, byte senderId) throws IOException {
        this.group    = InetAddress.getByName(multicastIp);
        this.port     = port;
        this.senderId = senderId;
        this.socket   = new MulticastSocket();
        this.socket.setTimeToLive(32); // limit to LAN; increase for WAN
    }

    public String getIp() {
        return this.group.getHostAddress();
    }

    public byte getSenderId() {
        return this.senderId;
    }

    public void setSenderId(byte senderId) {
        this.senderId = senderId;
    }

    public void sendAudio(byte[] pcmData) throws IOException {
        sendFragmented(UDPHeader.MEDIA_AUDIO, pcmData, false);
    }

    public void sendWebcam(byte[] jpegData, boolean isKeyframe) throws IOException {
        sendFragmented(UDPHeader.MEDIA_WEBCAM, jpegData, isKeyframe);
    }

    public void sendScreen(byte[] jpegData, boolean isKeyframe) throws IOException {
        sendFragmented(UDPHeader.MEDIA_SCREEN, jpegData, isKeyframe);
    }

    private void sendFragmented(byte mediaType, byte[] data, boolean isKeyframe) throws IOException {
        if (senderId == 0) {
            // Cannot send if we haven't been assigned a senderId by the server
            return;
        }
        int totalFrags = (int) Math.ceil((double) data.length / MAX_PAYLOAD);
        short seqId    = (short) (seqIdCounter.getAndIncrement() & 0xFFFF);

        for (int i = 0; i < totalFrags; i++) {
            int offset = i * MAX_PAYLOAD;
            int len    = Math.min(MAX_PAYLOAD, data.length - offset);

            byte flags = UDPHeader.FLAG_IS_FRAGMENT;
            if (i == totalFrags - 1) flags |= UDPHeader.FLAG_IS_LAST_FRAGMENT;
            if (isKeyframe)          flags |= UDPHeader.FLAG_IS_KEYFRAME;

            byte[] header  = UDPHeader.encode(mediaType, flags, seqId,
                                              (short) i, (short) totalFrags,
                                              (short) len, senderId);
            byte[] packet  = new byte[UDPHeader.HEADER_SIZE + len];
            System.arraycopy(header, 0, packet, 0, UDPHeader.HEADER_SIZE);
            System.arraycopy(data,   offset, packet, UDPHeader.HEADER_SIZE, len);

            socket.send(new DatagramPacket(packet, packet.length, group, port));
        }
    }

    public void close() {
        socket.close();
    }
}
