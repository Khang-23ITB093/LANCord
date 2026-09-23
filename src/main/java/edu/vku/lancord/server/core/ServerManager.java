package edu.vku.lancord.server.core;

import edu.vku.lancord.common.model.User;
import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;
import edu.vku.lancord.common.protocol.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerManager {
    // Map of userId to their connected ClientHandler
    private static final Map<Integer, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    // A simple way to generate multicast IPs for channels.
    // E.g. starts at 230.0.0.1, increments the last octet.
    private static int nextMulticastOctet = 1;
    private static final Map<Integer, String> channelMulticastMap = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<Integer, Byte>> channelSenderIds = new ConcurrentHashMap<>();
    
    // Tracks currently active streamers and their usernames for UI mapping: channelId -> {senderId -> username}
    private static final Map<Integer, Map<Byte, String>> activeStreamersByChannel = new ConcurrentHashMap<>();

    public static synchronized boolean registerUser(User user, ClientHandler handler) {
        // Prevent duplicate login
        if (onlineUsers.containsKey(user.getId())) {
            return false;
        }
        onlineUsers.put(user.getId(), handler);
        broadcastOnlineUsers();
        return true;
    }

    public static void removeUser(int userId) {
        onlineUsers.remove(userId);
        broadcastOnlineUsers();
    }

    public static void broadcastOnlineUsers() {
        try {
            List<User> userList = new ArrayList<>();
            for (ClientHandler h : onlineUsers.values()) {
                if (h.getUser() != null) {
                    userList.add(h.getUser());
                }
            }
            Message msg = new Message(MessageType.ONLINE_USERS_UPDATE, JsonUtil.valueToTree(userList));
            broadcast(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void broadcast(Message message) {
        for (ClientHandler handler : onlineUsers.values()) {
            handler.sendMessage(message);
        }
    }

    public static void sendToUser(int userId, Message message) {
        ClientHandler handler = onlineUsers.get(userId);
        if (handler != null) {
            handler.sendMessage(message);
        }
    }

    public static void sendToUsers(List<Integer> userIds, Message message) {
        for (int userId : userIds) {
            sendToUser(userId, message);
        }
    }

    public static synchronized String getMulticastIpForChannel(int channelId) {
        if (!channelMulticastMap.containsKey(channelId)) {
            String ip = "230.0.0." + nextMulticastOctet;
            nextMulticastOctet++;
            if (nextMulticastOctet > 254) nextMulticastOctet = 1;
            channelMulticastMap.put(channelId, ip);
        }
        return channelMulticastMap.get(channelId);
    }

    public static synchronized byte assignSenderId(int channelId, int userId) {
        channelSenderIds.putIfAbsent(channelId, new ConcurrentHashMap<>());
        Map<Integer, Byte> senders = channelSenderIds.get(channelId);
        if (senders.containsKey(userId)) {
            return senders.get(userId);
        }
        byte newId = (byte) (senders.size() + 1);
        senders.put(userId, newId);
        return newId;
    }

    public static synchronized void addActiveStreamer(int channelId, byte senderId, String username) {
        activeStreamersByChannel.putIfAbsent(channelId, new ConcurrentHashMap<>());
        activeStreamersByChannel.get(channelId).put(senderId, username);
    }

    public static synchronized void removeActiveStreamer(int channelId, byte senderId) {
        if (activeStreamersByChannel.containsKey(channelId)) {
            activeStreamersByChannel.get(channelId).remove(senderId);
            if (activeStreamersByChannel.get(channelId).isEmpty()) {
                activeStreamersByChannel.remove(channelId);
            }
        }
    }

    public static synchronized Map<Byte, String> getActiveStreamers(int channelId) {
        return activeStreamersByChannel.getOrDefault(channelId, new ConcurrentHashMap<>());
    }

    public static synchronized boolean isStreamActive(int channelId) {
        return activeStreamersByChannel.containsKey(channelId) && !activeStreamersByChannel.get(channelId).isEmpty();
    }
}
