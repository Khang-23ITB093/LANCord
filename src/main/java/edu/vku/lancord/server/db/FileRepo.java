package edu.vku.lancord.server.db;

import edu.vku.lancord.common.model.FileMetadata;

import java.sql.*;

public class FileRepo {
    public static FileMetadata saveFileMetadata(int uploaderId, String contextType, int contextId, String originalName, String storedName, long fileSize) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        String sql = "INSERT INTO files (uploader_id, context_type, context_id, original_name, stored_name, file_size) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, uploaderId);
            stmt.setString(2, contextType);
            stmt.setInt(3, contextId);
            stmt.setString(4, originalName);
            stmt.setString(5, storedName);
            stmt.setLong(6, fileSize);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    FileMetadata metadata = new FileMetadata();
                    metadata.setId(rs.getInt(1));
                    metadata.setUploaderId(uploaderId);
                    metadata.setContextType(contextType);
                    metadata.setContextId(contextId);
                    metadata.setOriginalName(originalName);
                    metadata.setStoredName(storedName);
                    metadata.setFileSize(fileSize);
                    return metadata;
                }
            }
        }
        return null;
    }

    public static FileMetadata getFileMetadata(int fileId) throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        String sql = "SELECT * FROM files WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FileMetadata metadata = new FileMetadata();
                    metadata.setId(rs.getInt("id"));
                    metadata.setUploaderId(rs.getInt("uploader_id"));
                    metadata.setContextType(rs.getString("context_type"));
                    metadata.setContextId(rs.getInt("context_id"));
                    metadata.setOriginalName(rs.getString("original_name"));
                    metadata.setStoredName(rs.getString("stored_name"));
                    metadata.setFileSize(rs.getLong("file_size"));
                    metadata.setCreatedAt(rs.getTimestamp("created_at"));
                    return metadata;
                }
            }
        }
        return null;
    }
}
