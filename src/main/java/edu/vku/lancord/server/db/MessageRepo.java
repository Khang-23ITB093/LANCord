package edu.vku.lancord.server.db;

import edu.vku.lancord.common.model.ChatMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageRepo {
    public static ChatMessage saveMessage(int senderId, String type, int receiverId, String content) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        String sql = "INSERT INTO messages (sender_id, type, receiver_id, content) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, senderId);
            stmt.setString(2, type);
            stmt.setInt(3, receiverId);
            stmt.setString(4, content);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new ChatMessage(rs.getInt(1), senderId, type, receiverId, content);
                }
            }
        }
        return null;
    }

    public static List<ChatMessage> getChatHistory(String type, int currentUserId, int contextId) throws SQLException {
        List<ChatMessage> history = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        String sql;
        if ("DM".equalsIgnoreCase(type)) {
            sql = "SELECT m.*, u.username AS sender_name " +
                  "FROM messages m " +
                  "LEFT JOIN users u ON m.sender_id = u.id " +
                  "WHERE m.type = 'DM' AND (" +
                  "  (m.sender_id = ? AND m.receiver_id = ?) OR " +
                  "  (m.sender_id = ? AND m.receiver_id = ?)" +
                  ") ORDER BY m.created_at ASC, m.id ASC";
        } else {
            sql = "SELECT m.*, u.username AS sender_name " +
                  "FROM messages m " +
                  "LEFT JOIN users u ON m.sender_id = u.id " +
                  "WHERE m.type = 'GROUP' AND m.receiver_id = ? " +
                  "ORDER BY m.created_at ASC, m.id ASC";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if ("DM".equalsIgnoreCase(type)) {
                stmt.setInt(1, currentUserId);
                stmt.setInt(2, contextId);
                stmt.setInt(3, contextId);
                stmt.setInt(4, currentUserId);
            } else {
                stmt.setInt(1, contextId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChatMessage msg = new ChatMessage(
                        rs.getInt("id"),
                        rs.getInt("sender_id"),
                        rs.getString("type"),
                        rs.getInt("receiver_id"),
                        rs.getString("content")
                    );
                    msg.setCreatedAt(rs.getTimestamp("created_at"));
                    msg.setSenderName(rs.getString("sender_name"));
                    history.add(msg);
                }
            }
        }
        return history;
    }
}
