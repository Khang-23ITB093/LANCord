package edu.vku.lancord.server.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.vku.lancord.common.model.*;
import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;
import edu.vku.lancord.common.protocol.MessageType;
import edu.vku.lancord.server.db.FileRepo;
import edu.vku.lancord.server.db.GroupRepo;
import edu.vku.lancord.server.db.MessageRepo;
import edu.vku.lancord.server.db.UserRepo;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private User user;
    
    private static final String STORAGE_DIR = "server_storage/";

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            // Ensure storage dir exists
            Files.createDirectories(Paths.get(STORAGE_DIR));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public User getUser() {
        return user;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = JsonUtil.deserialize(line);
                handleMessage(msg);
            }
        } catch (Exception e) {
            System.out.println("Client disconnected.");
        } finally {
            if (user != null) {
                ServerManager.removeUser(user.getId());
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(Message message) {
        try {
            String json = JsonUtil.serialize(message);
            out.println(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(Message msg) {
        try {
            JsonNode payload = msg.getPayload();
            switch (msg.getType()) {
                case LOGIN:
                    handleLogin(payload);
                    break;
                case CREATE_GROUP:
                    handleCreateGroup(payload);
                    break;
                case GROUP_INVITE:
                    handleGroupInvite(payload);
                    break;
                case CREATE_CHANNEL:
                    handleCreateChannel(payload);
                    break;
                case SEND_DM:
                case SEND_GROUP_MSG:
                    handleChatMessage(msg.getType(), payload);
                    break;
                case UPLOAD_FILE_INIT:
                    handleUploadInit(payload);
                    break;
                case FILE_CHUNK:
                    handleFileChunk(payload);
                    break;
                case DOWNLOAD_FILE_REQ:
                    handleDownloadReq(payload);
                    break;
                case STREAM_START:
                    handleStreamStart(payload);
                    break;
                default:
                    System.out.println("Unknown message type: " + msg.getType());
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(new Message(MessageType.ERROR, JsonUtil.valueToTree("Internal server error")));
        }
    }

    private void handleLogin(JsonNode payload) throws Exception {
        String username = payload.get("username").asText();
        User u = UserRepo.findByUsername(username);
        if (u == null) {
            u = UserRepo.createUser(username);
        }
        
        if (ServerManager.registerUser(u, this)) {
            this.user = u;
            sendMessage(new Message(MessageType.LOGIN_RESP, JsonUtil.valueToTree(u)));
        } else {
            sendMessage(new Message(MessageType.ERROR, JsonUtil.valueToTree("User already online")));
        }
    }

    private void handleCreateGroup(JsonNode payload) throws Exception {
        String name = payload.get("name").asText();
        Group group = GroupRepo.createGroup(name, user.getId());
        sendMessage(new Message(MessageType.CREATE_GROUP_RESP, JsonUtil.valueToTree(group)));
    }

    private void handleGroupInvite(JsonNode payload) throws Exception {
        int groupId = payload.get("groupId").asInt();
        int userId = payload.get("userId").asInt();
        GroupRepo.addUserToGroup(groupId, userId);
        
        // Notify the invited user if online
        ObjectNode notifyNode = JsonUtil.valueToTree(new Object()).deepCopy();
        notifyNode.put("groupId", groupId);
        ServerManager.sendToUser(userId, new Message(MessageType.GROUP_INVITE_RESP, notifyNode));
    }

    private void handleCreateChannel(JsonNode payload) throws Exception {
        int groupId = payload.get("groupId").asInt();
        String name = payload.get("name").asText();
        Channel channel = GroupRepo.createChannel(groupId, name);
        
        List<Integer> members = GroupRepo.getGroupMembers(groupId);
        ServerManager.sendToUsers(members, new Message(MessageType.CREATE_CHANNEL_RESP, JsonUtil.valueToTree(channel)));
    }

    private void handleChatMessage(MessageType type, JsonNode payload) throws Exception {
        int receiverId = payload.get("receiverId").asInt();
        String content = payload.get("content").asText();
        String t = type == MessageType.SEND_DM ? "DM" : "GROUP";
        
        ChatMessage chatMsg = MessageRepo.saveMessage(user.getId(), t, receiverId, content);
        chatMsg.setSenderName(user.getUsername());
        
        Message notifyMsg = new Message(MessageType.NEW_MESSAGE_NOTIFY, JsonUtil.valueToTree(chatMsg));
        
        if (type == MessageType.SEND_DM) {
            ServerManager.sendToUser(receiverId, notifyMsg);
            // Send back to sender too
            sendMessage(notifyMsg);
        } else {
            List<Integer> members = GroupRepo.getGroupMembers(receiverId); // receiverId is groupId here
            ServerManager.sendToUsers(members, notifyMsg);
        }
    }

    // Simplified File Transfer handling (In Phase 1, server receives chunks and writes to disk)
    private void handleUploadInit(JsonNode payload) throws Exception {
        String originalName = payload.get("filename").asText();
        long fileSize = payload.get("size").asLong();
        String contextType = payload.get("contextType").asText();
        int contextId = payload.get("contextId").asInt();
        
        String storedName = UUID.randomUUID().toString() + "_" + originalName;
        FileMetadata meta = FileRepo.saveFileMetadata(user.getId(), contextType, contextId, originalName, storedName, fileSize);
        
        sendMessage(new Message(MessageType.UPLOAD_FILE_RESP, JsonUtil.valueToTree(meta)));
    }

    private void handleFileChunk(JsonNode payload) throws Exception {
        int fileId = payload.get("fileId").asInt();
        byte[] chunk = payload.get("chunk").binaryValue(); // Base64 decoded automatically by Jackson
        
        FileMetadata meta = FileRepo.getFileMetadata(fileId);
        if (meta != null) {
            Path path = Paths.get(STORAGE_DIR, meta.getStoredName());
            Files.write(path, chunk, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            
            // Check if file size on disk == meta.getFileSize()
            if (Files.size(path) >= meta.getFileSize()) {
                // Notify relevant users
                ObjectNode notifyNode = (ObjectNode) JsonUtil.valueToTree(meta);
                Message notifyMsg = new Message(MessageType.FILE_UPLOAD_COMPLETE, notifyNode);
                
                if (meta.getContextType().equals("DM")) {
                    ServerManager.sendToUser(meta.getContextId(), notifyMsg);
                    sendMessage(notifyMsg); // send back to uploader
                } else {
                    List<Integer> members = GroupRepo.getGroupMembers(meta.getContextId());
                    ServerManager.sendToUsers(members, notifyMsg);
                }
            }
        }
    }

    private void handleDownloadReq(JsonNode payload) throws Exception {
        int fileId = payload.get("fileId").asInt();
        FileMetadata meta = FileRepo.getFileMetadata(fileId);
        if (meta != null) {
            Path path = Paths.get(STORAGE_DIR, meta.getStoredName());
            if (Files.exists(path)) {
                // Read and send in chunks (simplified: send whole file in one chunk for phase 1 demo, or small chunks)
                byte[] data = Files.readAllBytes(path);
                ObjectNode respNode = (ObjectNode) JsonUtil.valueToTree(meta);
                respNode.put("data", data);
                sendMessage(new Message(MessageType.DOWNLOAD_FILE_RESP, respNode));
            }
        }
    }

    private void handleStreamStart(JsonNode payload) throws Exception {
        int channelId = payload.get("channelId").asInt();
        int groupId = payload.get("groupId").asInt();
        
        String ip = ServerManager.getMulticastIpForChannel(channelId);
        int port = 9999;
        
        ObjectNode notifyNode = JsonUtil.valueToTree(new Object()).deepCopy();
        notifyNode.put("channelId", channelId);
        notifyNode.put("groupId", groupId);
        notifyNode.put("multicastIp", ip);
        notifyNode.put("multicastPort", port);
        notifyNode.put("streamerName", user.getUsername());
        
        List<Integer> members = GroupRepo.getGroupMembers(groupId);
        ServerManager.sendToUsers(members, new Message(MessageType.STREAM_STARTED, notifyNode));
    }
}
