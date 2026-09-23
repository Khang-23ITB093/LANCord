package edu.vku.lancord.server.db;

import edu.vku.lancord.common.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserRepo {
    public static User findByUsername(String username) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    user.setCreatedAt(rs.getTimestamp("created_at"));
                    return user;
                }
            }
        }
        return null;
    }

    public static User createUser(String username, String passwordHash) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new User(rs.getInt(1), username, passwordHash);
                }
            }
        }
        return null;
    }
}
