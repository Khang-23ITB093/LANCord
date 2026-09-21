package edu.vku.lancord.common.model;

import java.sql.Timestamp;

public class FileMetadata {
    private int id;
    private int uploaderId;
    private String contextType; // "DM" or "GROUP"
    private int contextId;
    private String originalName;
    private String storedName;
    private long fileSize;
    private Timestamp createdAt;
    private String uploaderName; // For UI display

    public FileMetadata() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUploaderId() { return uploaderId; }
    public void setUploaderId(int uploaderId) { this.uploaderId = uploaderId; }
    public String getContextType() { return contextType; }
    public void setContextType(String contextType) { this.contextType = contextType; }
    public int getContextId() { return contextId; }
    public void setContextId(int contextId) { this.contextId = contextId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public String getUploaderName() { return uploaderName; }
    public void setUploaderName(String uploaderName) { this.uploaderName = uploaderName; }
}
