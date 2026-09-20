package edu.vku.lancord.common.model;

import java.sql.Timestamp;

public class ChatMessage {
    private int id;
    private int senderId;
    private String type; // "DM" or "GROUP"
    private int receiverId; // user_id or channel_id
    private String content;
    private Timestamp createdAt;
    
    // For UI display convenience
    private String senderName;

    public ChatMessage() {}

    public ChatMessage(int id, int senderId, String type, int receiverId, String content) {
        this.id = id;
        this.senderId = senderId;
        this.type = type;
        this.receiverId = receiverId;
        this.content = content;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getReceiverId() { return receiverId; }
    public void setReceiverId(int receiverId) { this.receiverId = receiverId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
}
