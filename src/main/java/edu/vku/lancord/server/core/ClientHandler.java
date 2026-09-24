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
import java.util.Map;
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
                case REGISTER:
                    handleRegister(payload);
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
                case STREAM_STOP:
                    handleStreamStop(payload);
                    break;
                case CALL_REQUEST:
                    handleCallRequest(payload);
                    break;
                case CALL_ACCEPT:
                    handleCallAccept(payload);
                    break;
                case CALL_REJECT:
                    handleCallReject(payload);
                    break;
                case GET_CHAT_HISTORY:
                    handleGetChatHistory(payload);
                    break;
                case GET_FILES_IN_CONTEXT:
                    handleGetFilesInContext(payload);
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
        String password = payload.get("password").asText();
        User u = UserRepo.findByUsername(username);
        
        if (u == null || !org.mindrot.jbcrypt.BCrypt.checkpw(password, u.getPasswordHash())) {
            sendMessage(new Message(MessageType.ERROR, JsonUtil.valueToTree("Invalid username or password")));
            return;
        }
        
        this.user = u;
        if (ServerManager.registerUser(u, this)) {
            sendMessage(new Message(MessageType.LOGIN_RESP, JsonUtil.valueToTree(u)));
        } else {
            this.user = null;
            sendMessage(new Message(MessageType.ERROR, JsonUtil.valueToTree("User already online")));
        }
    }

    private void handleRegister(JsonNode payload) throws Exception {
        String username = payload.get("username").asText();
        String password = payload.get("password").asText();
        
        User existingUser = UserRepo.findByUsername(username);
        if (existingUser != null) {
            sendMessage(new Message(MessageType.ERROR, JsonUtil.valueToTree("Username already exists")));
            return;
        }
        
        String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());
        User u = UserRepo.createUser(username, passwordHash);
        
        if (u != null) {
            sendMessage(new Message(MessageType.REGISTER_RESP, JsonUtil.valueToTree(u)));
        } else {
            sendMessage(new Message(MessageType.ERROR, JsonUtil.valueToTree("Failed to create user")));
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
            if (receiverId == 1) { // General Channel
                ServerManager.broadcast(notifyMsg);
            } else {
                List<Integer> members = GroupRepo.getGroupMembers(receiverId); // receiverId is groupId here
                ServerManager.sendToUsers(members, notifyMsg);
            }
        }
    }

    // Simplified File Transfer handling (In Phase 1, server receives chunks and writes to disk)
    private void handleUploadInit(JsonNode payload) throws Exception {
        String originalName = payload.get("filename").asText();
        long fileSize = payload.get("size").asLong();
        String contextType = payload.get("contextType").asText();
        int contextId = payload.get("contextId").asInt();

        if (fileSize > FileRepo.MAX_FILE_SIZE) {
            sendMessage(new Message(MessageType.ERROR, JsonUtil.valueToTree("File exceeds 50MB limit")));
            return;
        }

        String storedName = UUID.randomUUID().toString() + "_" + originalName;
        FileMetadata meta = FileRepo.saveFileMetadata(user.getId(), contextType, contextId, originalName, storedName, fileSize);
        meta.setUploaderName(user.getUsername());

        sendMessage(new Message(MessageType.UPLOAD_FILE_RESP, JsonUtil.valueToTree(meta)));
    }

    private void handleFileChunk(JsonNode payload) throws Exception {
        int fileId = payload.get("fileId").asInt();
        byte[] chunk = payload.get("chunk").binaryValue(); // Base64 decoded automatically by Jackson

        FileMetadata meta = FileRepo.getFileMetadata(fileId);
        if (meta != null) {
            Path path = Paths.get(STORAGE_DIR, meta.getStoredName());
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, chunk, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            if (Files.size(path) >= meta.getFileSize()) {
                // Re-fetch to include uploaderName from DB join
                meta = FileRepo.getFileMetadata(fileId);
                ObjectNode notifyNode = (ObjectNode) JsonUtil.valueToTree(meta);

                // Send FILE_UPLOAD_COMPLETE back to uploader
                sendMessage(new Message(MessageType.FILE_UPLOAD_COMPLETE, notifyNode));

                // Broadcast FILE_UPLOAD_NOTIFY to others in the conversation
                Message notifyMsg = new Message(MessageType.FILE_UPLOAD_NOTIFY, notifyNode);
                if (meta.getContextType().equals("DM")) {
                    ServerManager.sendToUser(meta.getContextId(), notifyMsg);
                } else {
                    List<Integer> members = GroupRepo.getGroupMembers(meta.getContextId());
                    // Exclude uploader from notify (they already got FILE_UPLOAD_COMPLETE)
                    for (int memberId : members) {
                        if (memberId != user.getId()) {
                            ServerManager.sendToUser(memberId, notifyMsg);
                        }
                    }
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
        byte senderId = ServerManager.assignSenderId(channelId, user.getId());
        
        ServerManager.addActiveStreamer(channelId, senderId, user.getUsername());
        Map<Byte, String> streamers = ServerManager.getActiveStreamers(channelId);
        
        ObjectNode notifyNode = JsonUtil.createObjectNode();
        notifyNode.put("channelId", channelId);
        notifyNode.put("groupId", groupId);
        notifyNode.put("multicastIp", ip);
        notifyNode.put("multicastPort", port);
        notifyNode.put("senderId", senderId);
        notifyNode.put("userId", user.getId());
        notifyNode.set("streamerNames", JsonUtil.valueToTree(streamers));
        
        List<Integer> members = GroupRepo.getGroupMembers(groupId);
        // Ensure streamer gets notified even if not in members list (though they usually are)
        if (!members.contains(user.getId())) {
            members.add(user.getId());
        }
        
        Message startMsg = new Message(MessageType.STREAM_STARTED, notifyNode);
        if (groupId == 1) { // General Channel
            ServerManager.broadcast(startMsg);
        } else {
            ServerManager.sendToUsers(members, startMsg);
        }
    }

    private void handleStreamStop(JsonNode payload) throws Exception {
        int channelId = payload.get("channelId").asInt();
        int groupId = payload.get("groupId").asInt();
        byte senderId = (byte) payload.get("senderId").asInt();
        String contextType = payload.has("contextType") ? payload.get("contextType").asText() : "GROUP";
        
        ServerManager.removeActiveStreamer(channelId, senderId);
        
        ObjectNode notifyNode = JsonUtil.createObjectNode();
        notifyNode.put("channelId", channelId);
        notifyNode.put("groupId", groupId);
        notifyNode.put("senderId", senderId);
        
        Message msg = new Message(MessageType.STREAM_STOPPED, notifyNode);
        
        if ("DM".equals(contextType)) {
            ServerManager.sendToUser(groupId, msg);
        } else {
            if (groupId == 1) { // General Channel
                ServerManager.broadcast(msg);
            } else {
                List<Integer> members = GroupRepo.getGroupMembers(groupId);
                ServerManager.sendToUsers(members, msg);
            }
        }
    }

    private void handleCallRequest(JsonNode payload) throws Exception {
        int receiverId = payload.get("receiverId").asInt();
        
        ObjectNode notifyNode = JsonUtil.createObjectNode();
        notifyNode.put("callerId", user.getId());
        notifyNode.put("callerName", user.getUsername());
        notifyNode.put("withVideo", payload.has("withVideo") && payload.get("withVideo").asBoolean());
        
        ServerManager.sendToUser(receiverId, new Message(MessageType.CALL_INCOMING, notifyNode));
    }

    private void handleCallAccept(JsonNode payload) throws Exception {
        int callerId = payload.get("callerId").asInt();
        int receiverId = user.getId();
        
        // We use callerId as the channelId for DMs for simplicity
        int channelId = callerId;
        
        String ip = ServerManager.getMulticastIpForChannel(channelId);
        int port = 9999;

        // Notify Caller – their own separate payload with their userId
        ObjectNode callerNode = JsonUtil.createObjectNode();
        callerNode.put("channelId", channelId);
        callerNode.put("groupId", 0);           // 0 = DM
        callerNode.put("multicastIp", ip);
        callerNode.put("multicastPort", port);
        callerNode.put("senderId", ServerManager.assignSenderId(channelId, callerId));
        callerNode.put("userId", callerId);     // tells client this stream session belongs to callerId
        callerNode.put("otherUserId", receiverId); // the other person in the call
        ServerManager.sendToUser(callerId, new Message(MessageType.STREAM_STARTED, callerNode));

        // Notify Receiver – separate payload with receiver's userId
        ObjectNode receiverNode = JsonUtil.createObjectNode();
        receiverNode.put("channelId", channelId);
        receiverNode.put("groupId", 0);         // 0 = DM
        receiverNode.put("multicastIp", ip);
        receiverNode.put("multicastPort", port);
        receiverNode.put("senderId", ServerManager.assignSenderId(channelId, receiverId));
        receiverNode.put("userId", receiverId);  // tells client this stream session belongs to receiverId
        receiverNode.put("otherUserId", callerId); // the other person in the call
        ServerManager.sendToUser(receiverId, new Message(MessageType.STREAM_STARTED, receiverNode));
    }

    private void handleCallReject(JsonNode payload) throws Exception {
        int callerId = payload.get("callerId").asInt();
        
        ObjectNode notifyNode = JsonUtil.createObjectNode();
        notifyNode.put("receiverId", user.getId());
        notifyNode.put("receiverName", user.getUsername());
        
        ServerManager.sendToUser(callerId, new Message(MessageType.CALL_REJECTED, notifyNode));
    }

    private void handleGetChatHistory(JsonNode payload) throws Exception {
        String type = payload.get("type").asText();
        int contextId = payload.get("contextId").asInt();
        List<ChatMessage> history = MessageRepo.getChatHistory(type, user.getId(), contextId);

        ObjectNode resp = JsonUtil.createObjectNode();
        resp.put("type", type);
        resp.put("contextId", contextId);
        resp.set("messages", JsonUtil.valueToTree(history));

        sendMessage(new Message(MessageType.CHAT_HISTORY_RESP, resp));

        // If it's a GROUP and there's an active stream, notify the user so they can join/see the UI
        if (type.equals("GROUP") && ServerManager.isStreamActive(contextId)) {
            String ip = ServerManager.getMulticastIpForChannel(contextId);
            byte senderId = ServerManager.assignSenderId(contextId, user.getId());
            Map<Byte, String> streamers = ServerManager.getActiveStreamers(contextId);
            
            ObjectNode notifyNode = JsonUtil.createObjectNode();
            notifyNode.put("channelId", contextId);
            notifyNode.put("groupId", contextId);
            notifyNode.put("multicastIp", ip);
            notifyNode.put("multicastPort", 9999);
            notifyNode.put("senderId", senderId);
            notifyNode.put("userId", -1); // -1 = join-existing-stream event; client joins as viewer only
            notifyNode.set("streamerNames", JsonUtil.valueToTree(streamers));
            
            sendMessage(new Message(MessageType.STREAM_STARTED, notifyNode));
        }
    }

    private void handleGetFilesInContext(JsonNode payload) throws Exception {
        String contextType = payload.get("contextType").asText();
        int contextId = payload.get("contextId").asInt();
        List<FileMetadata> files = FileRepo.getFilesInContext(contextType, user.getId(), contextId);

        ObjectNode resp = JsonUtil.createObjectNode();
        resp.put("contextType", contextType);
        resp.put("contextId", contextId);
        resp.set("files", JsonUtil.valueToTree(files));

        sendMessage(new Message(MessageType.FILES_IN_CONTEXT_RESP, resp));
    }
}
