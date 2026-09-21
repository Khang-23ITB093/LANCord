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

/**
 * Controller for both ChatFileMessage.fxml and ChatVideoMessage.fxml.
 * Handles downloading files and opening videos with in-app player or system default app.
 */
public class ChatFileMessageController {

    @FXML private Circle avatarCircle;
    @FXML private Label senderLabel;
    @FXML private Label timestampLabel;
    @FXML private Label fileNameLabel;
    @FXML private Label fileSizeLabel;
    @FXML private Button downloadButton;
    @FXML private Label statusLabel;

    private static final String DOWNLOADS_DIR = "client_downloads/";
    private static final String[] AVATAR_COLORS = {
        "#5865F2", "#57F287", "#FEE75C", "#EB459E", "#ED4245",
        "#23A559", "#3BA55C", "#FAA61A"
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
            timestampLabel.setText(new SimpleDateFormat("HH:mm").format(timestamp));
        }
        int colorIdx = Math.abs(senderName.hashCode()) % AVATAR_COLORS.length;
        avatarCircle.setFill(Color.web(AVATAR_COLORS[colorIdx]));

        // Check local sender path
        if (localSenderPath != null && Files.exists(Paths.get(localSenderPath))) {
            downloadButton.setText(isVideo ? "Watch" : "Open");
            statusLabel.setText("Already on device");
            return;
        }

        // Check if already downloaded
        Path path = Paths.get(DOWNLOADS_DIR, meta.getOriginalName());
        if (Files.exists(path)) {
            downloadButton.setText(isVideo ? "Watch" : "Open");
            statusLabel.setText("Already downloaded");
        }
    }

    @FXML
    private void onDownload() {
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

        downloadButton.setDisable(true);
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
                    downloadButton.setDisable(false);
                    downloadButton.setText(isVideo ? "Watch" : "Open");

                    if (isVideo) {
                        openFile(filePath.toFile());
                    }
                } catch (Exception e) {
                    statusLabel.setText("Save failed: " + e.getMessage());
                    downloadButton.setDisable(false);
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
                downloadButton.setDisable(false);
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
