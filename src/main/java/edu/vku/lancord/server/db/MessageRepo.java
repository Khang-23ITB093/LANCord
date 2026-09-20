package edu.vku.lancord.server.db;

import edu.vku.lancord.common.model.ChatMessage;

import java.sql.*;

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
}
