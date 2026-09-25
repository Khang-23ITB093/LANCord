package edu.vku.lancord.client.network;

import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class TCPConnection {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Consumer<Message> onMessageReceived;

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Start listening thread
        new Thread(this::listen).start();
    }

    public void setOnMessageReceived(Consumer<Message> callback) {
        this.onMessageReceived = callback;
    }

    public void sendMessage(Message message) {
        try {
            String json = JsonUtil.serialize(message);
            out.println(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = JsonUtil.deserialize(line);
                if (onMessageReceived != null) {
                    onMessageReceived.accept(msg);
                }
            }
        } catch (Exception e) {
            System.out.println("Disconnected from server.");
        } finally {
            close();
        }
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    public java.net.InetAddress getLocalAddress() {
        if (socket == null) return null;
        java.net.InetAddress addr = socket.getLocalAddress();
        if (addr != null && addr.isLoopbackAddress()) {
            try {
                java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface ni = interfaces.nextElement();
                    if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;
                    String name = ni.getName().toLowerCase();
                    if (name.contains("vbox") || name.contains("vmware") || name.contains("wsl") || name.contains("docker")) continue;
                    java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress ia = addresses.nextElement();
                        if (ia instanceof java.net.Inet4Address) {
                            return ia;
                        }
                    }
                }
            } catch (java.net.SocketException e) {
                e.printStackTrace();
            }
        }
        return addr;
    }
}
