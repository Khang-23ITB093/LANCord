package edu.vku.lancord.client.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.*;

public class UDPStreamReceiver {
    private static final int BUFFER_SIZE     = 1412; // 1400 + 12 header
    private static final int STALE_TIMEOUT_MS = 2000;

    private final MulticastSocket socket;
    private final InetAddress group;
    private final int port;
    private final ConcurrentHashMap<String, FragmentBuffer> fragmentMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService staleCleanup;
    private volatile boolean running = false;
    private Thread receiveThread;

    private MediaDispatcher dispatcher;

    public UDPStreamReceiver(String multicastIp, int port, MediaDispatcher dispatcher) throws IOException {
        this.group      = InetAddress.getByName(multicastIp);
        this.port       = port;
        this.dispatcher = dispatcher;
        this.socket     = new MulticastSocket(port);
        this.socket.setReuseAddress(true);
        this.socket.joinGroup(group);
        this.staleCleanup = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        running = true;
        receiveThread = new Thread(this::receiveLoop, "UDP-Receive-Thread");
        receiveThread.setDaemon(true);
        receiveThread.start();
        staleCleanup.scheduleAtFixedRate(this::cleanStaleBuffers, 500, 500, TimeUnit.MILLISECONDS);
    }

    private void receiveLoop() {
        byte[] buf = new byte[BUFFER_SIZE];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        while (running) {
            try {
                socket.receive(pkt);
                processPacket(pkt);
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }
    }

    private void processPacket(DatagramPacket pkt) {
        byte[] data = pkt.getData();
        if (pkt.getLength() < UDPHeader.HEADER_SIZE) return; // malformed

        UDPHeader.ParsedHeader h = UDPHeader.decode(data);
        int payloadStart = UDPHeader.HEADER_SIZE;
        int payloadLen   = Short.toUnsignedInt(h.payloadLen);

        byte[] payload = new byte[payloadLen];
        System.arraycopy(data, payloadStart, payload, 0, payloadLen);

        String key = h.senderId + ":" + Short.toUnsignedInt(h.seqId);

        if (h.totalFrags == 1) {
            // No fragmentation - dispatch immediately
            dispatcher.onMedia(h.senderId, h.mediaType, payload);
            return;
        }

        FragmentBuffer buf = fragmentMap.computeIfAbsent(key, k -> new FragmentBuffer(h));
        synchronized (buf) {
            int idx = Short.toUnsignedInt(h.fragIndex);
            if (buf.fragments[idx] == null) {
                buf.fragments[idx] = payload;
                buf.receivedCount++;
            }
            if (buf.receivedCount == Short.toUnsignedInt(buf.totalFrags)) {
                // All fragments received - reassemble
                int totalSize = 0;
                for (byte[] frag : buf.fragments) totalSize += frag.length;
                byte[] full = new byte[totalSize];
                int offset = 0;
                for (byte[] frag : buf.fragments) {
                    System.arraycopy(frag, 0, full, offset, frag.length);
                    offset += frag.length;
                }
                fragmentMap.remove(key);
                dispatcher.onMedia(h.senderId, h.mediaType, full);
            }
        }
    }

    private void cleanStaleBuffers() {
        long now = System.currentTimeMillis();
        fragmentMap.entrySet().removeIf(e -> now - e.getValue().createdAt > STALE_TIMEOUT_MS);
    }

    public void stop() {
        running = false;
        try {
            socket.leaveGroup(group);
        } catch (IOException ignored) {}
        socket.close();
        staleCleanup.shutdown();
    }
}
