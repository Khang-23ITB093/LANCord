package edu.vku.lancord.common.model;

import java.sql.Timestamp;

public class User {
    private int id;
    private String username;
    private Timestamp createdAt;

    public User() {}

    public User(int id, String username) {
        this.id = id;
        this.username = username;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
