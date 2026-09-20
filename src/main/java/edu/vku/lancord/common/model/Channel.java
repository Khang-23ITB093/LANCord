package edu.vku.lancord.common.model;

import java.sql.Timestamp;

public class Channel {
    private int id;
    private int groupId;
    private String name;
    private Timestamp createdAt;

    public Channel() {}

    public Channel(int id, int groupId, String name) {
        this.id = id;
        this.groupId = groupId;
        this.name = name;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
