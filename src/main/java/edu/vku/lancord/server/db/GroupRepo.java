package edu.vku.lancord.server.db;

import edu.vku.lancord.common.model.Channel;
import edu.vku.lancord.common.model.Group;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupRepo {
    public static Group createGroup(String name, int createdBy) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        String sql = "INSERT INTO groups (name, created_by) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setInt(2, createdBy);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int groupId = rs.getInt(1);
                    addUserToGroup(groupId, createdBy);
                    return new Group(groupId, name, createdBy);
                }
            }
        }
        return null;
    }

    public static void addUserToGroup(int groupId, int userId) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        String sql = "INSERT IGNORE INTO group_members (group_id, user_id) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
    }

    public static List<Integer> getGroupMembers(int groupId) throws SQLException {
        List<Integer> members = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        String sql = "SELECT user_id FROM group_members WHERE group_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    members.add(rs.getInt("user_id"));
                }
            }
        }
        return members;
    }

    public static Channel createChannel(int groupId, String name) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        String sql = "INSERT INTO channels (group_id, name) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, groupId);
            stmt.setString(2, name);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new Channel(rs.getInt(1), groupId, name);
                }
            }
        }
        return null;
    }
}
