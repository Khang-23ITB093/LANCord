package edu.vku.lancord.client.ui.components;

import edu.vku.lancord.client.ui.LoginController;
import edu.vku.lancord.common.model.FileMetadata;
import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;
import edu.vku.lancord.common.protocol.MessageType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

/**
 * Controller for both ChatFileMessage.fxml and ChatVideoMessage.fxml.
 * Handles downloading files and opening videos with in-app player or system default app.
 */
public class ChatFileMessageController {

    @FXML private Circle avatarCircle;
    @FXML private Label avatarInitial;
    @FXML private Label senderLabel;
    @FXML private Label timestampLabel;
    @FXML private Label fileNameLabel;
    @FXML private Label fileSizeLabel;
    @FXML private Label fileIconLabel;
    @FXML private Button actionButton;
    @FXML private Label statusLabel;

    private static final String DOWNLOADS_DIR = "client_downloads/";
    private static final String[] AVATAR_COLORS = {
        "#5865F2", "#23A559", "#E91E63", "#FF9800",
        "#9C27B0", "#00BCD4", "#FAA61A", "#3BA55C"
    };

    private FileMetadata fileMetadata;
    private boolean isVideo;
    private String localSenderPath;

    public void setData(String senderName, FileMetadata meta, Date timestamp, boolean video, String localPath) {
        this.fileMetadata = meta;
        this.isVideo = video;
        this.localSenderPath = localPath;

        senderLabel.setText(senderName);
        fileNameLabel.setText(meta.getOriginalName());
        fileSizeLabel.setText(formatSize(meta.getFileSize()));

        if (timestamp != null) {
            String formatted = new SimpleDateFormat("'Today at' HH:mm").format(timestamp);
            timestampLabel.setText(formatted);
        }
        int colorIdx = Math.abs(senderName.hashCode()) % AVATAR_COLORS.length;
        avatarCircle.setFill(Color.web(AVATAR_COLORS[colorIdx]));
        if (avatarInitial != null) {
            avatarInitial.setText(senderName.substring(0, 1).toUpperCase());
        }

        // Set File Icon
        if (fileIconLabel != null) {
            String ext = getExtension(meta.getOriginalName()).toLowerCase();
            if (Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp").contains(ext)) {
                fileIconLabel.setText("🖼");
            } else if (Set.of("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm").contains(ext)) {
                fileIconLabel.setText("🎬");
            } else if (Set.of("mp3", "wav", "m4a", "aac", "ogg", "flac").contains(ext)) {
                fileIconLabel.setText("🎵");
            } else if (ext.equals("pdf")) {
                fileIconLabel.setText("📕");
            } else if (Set.of("zip", "rar", "7z", "tar", "gz").contains(ext)) {
                fileIconLabel.setText("🗜");
            } else {
                fileIconLabel.setText("📄");
            }
        }

        // Check local sender path
        if (localSenderPath != null && Files.exists(Paths.get(localSenderPath))) {
            actionButton.setText("📂 Mở");
            statusLabel.setText("Already on device");
            return;
        }

        // Check if already downloaded
        Path path = Paths.get(DOWNLOADS_DIR, meta.getOriginalName());
        if (Files.exists(path)) {
            actionButton.setText("📂 Mở");
            statusLabel.setText("Already downloaded");
        } else {
            actionButton.setText("⬇ Tải về");
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    @FXML
    private void onActionClicked() {
        if (fileMetadata == null) return;

        // Check local sender path
        if (localSenderPath != null && Files.exists(Paths.get(localSenderPath))) {
            openFile(new File(localSenderPath));
            return;
        }

        // Check if already on disk
        Path path = Paths.get(DOWNLOADS_DIR, fileMetadata.getOriginalName());
        if (Files.exists(path)) {
            openFile(path.toFile());
            return;
        }

        actionButton.setDisable(true);
        statusLabel.setText("Downloading...");

        // Register callback for when download response arrives
        FileDownloadRegistry.register(fileMetadata.getId(), (byte[] data) -> {
            Platform.runLater(() -> {
                try {
                    Path dir = Paths.get(DOWNLOADS_DIR);
                    if (!Files.exists(dir)) {
                        Files.createDirectories(dir);
                    }
                    Path filePath = dir.resolve(fileMetadata.getOriginalName());
                    try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                        fos.write(data);
                    }
                    statusLabel.setText("Saved to " + DOWNLOADS_DIR);
                    actionButton.setDisable(false);
                    actionButton.setText("📂 Mở");

                    if (isVideo) {
                        openFile(filePath.toFile());
                    }
                } catch (Exception e) {
                    statusLabel.setText("Save failed: " + e.getMessage());
                    actionButton.setDisable(false);
                }
            });
        });

        // Request file from server
        try {
            ObjectNode payload = JsonUtil.createObjectNode();
            payload.put("fileId", fileMetadata.getId());
            LoginController.connection.sendMessage(new Message(MessageType.DOWNLOAD_FILE_REQ, payload));
        } catch (Exception e) {
            Platform.runLater(() -> {
                statusLabel.setText("Request failed.");
                actionButton.setDisable(false);
            });
        }
    }

    private void openFile(File file) {
        if (isVideo) {
            MediaViewer.openVideoPlayer(fileMetadata != null ? fileMetadata.getOriginalName() : file.getName(), file);
        } else {
            MediaViewer.openInSystem(file);
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
