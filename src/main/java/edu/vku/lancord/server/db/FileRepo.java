package edu.vku.lancord.server.db;

import edu.vku.lancord.common.model.FileMetadata;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FileRepo {

    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB

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
        String sql = "SELECT f.*, u.username AS uploader_name FROM files f LEFT JOIN users u ON f.uploader_id = u.id WHERE f.id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fileId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public static List<FileMetadata> getFilesInContext(String contextType, int currentUserId, int contextId) throws SQLException {
        List<FileMetadata> files = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        String sql;
        if ("DM".equalsIgnoreCase(contextType)) {
            sql = "SELECT f.*, u.username AS uploader_name " +
                  "FROM files f LEFT JOIN users u ON f.uploader_id = u.id " +
                  "WHERE f.context_type = 'DM' AND (" +
                  "  (f.uploader_id = ? AND f.context_id = ?) OR " +
                  "  (f.uploader_id = ? AND f.context_id = ?)" +
                  ") ORDER BY f.created_at ASC, f.id ASC";
        } else {
            sql = "SELECT f.*, u.username AS uploader_name " +
                  "FROM files f LEFT JOIN users u ON f.uploader_id = u.id " +
                  "WHERE f.context_type = 'GROUP' AND f.context_id = ? " +
                  "ORDER BY f.created_at ASC, f.id ASC";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if ("DM".equalsIgnoreCase(contextType)) {
                stmt.setInt(1, currentUserId);
                stmt.setInt(2, contextId);
                stmt.setInt(3, contextId);
                stmt.setInt(4, currentUserId);
            } else {
                stmt.setInt(1, contextId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapRow(rs));
                }
            }
        }
        return files;
    }

    private static FileMetadata mapRow(ResultSet rs) throws SQLException {
        FileMetadata metadata = new FileMetadata();
        metadata.setId(rs.getInt("id"));
        metadata.setUploaderId(rs.getInt("uploader_id"));
        metadata.setContextType(rs.getString("context_type"));
        metadata.setContextId(rs.getInt("context_id"));
        metadata.setOriginalName(rs.getString("original_name"));
        metadata.setStoredName(rs.getString("stored_name"));
        metadata.setFileSize(rs.getLong("file_size"));
        metadata.setCreatedAt(rs.getTimestamp("created_at"));
        metadata.setUploaderName(rs.getString("uploader_name"));
        return metadata;
    }
}

