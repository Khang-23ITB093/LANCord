package edu.vku.lancord.client.ui.components;

import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.vku.lancord.client.ui.LoginController;
import edu.vku.lancord.common.model.FileMetadata;
import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;
import edu.vku.lancord.common.protocol.MessageType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatImageMessageController {

    @FXML private Circle avatarCircle;
    @FXML private Label senderLabel;
    @FXML private Label timestampLabel;
    @FXML private ImageView imageView;
    @FXML private Label statusLabel;

    private static final String DOWNLOADS_DIR = "client_downloads/";
    private static final String[] AVATAR_COLORS = {
        "#5865F2", "#57F287", "#FEE75C", "#EB459E", "#ED4245",
        "#23A559", "#3BA55C", "#FAA61A"
    };

    /** Called when image data is already available. */
    public void setData(String senderName, byte[] imageData, Date timestamp) {
        senderLabel.setText(senderName);
        if (timestamp != null) {
            timestampLabel.setText(new SimpleDateFormat("HH:mm").format(timestamp));
        }
        int colorIdx = Math.abs(senderName.hashCode()) % AVATAR_COLORS.length;
        avatarCircle.setFill(Color.web(AVATAR_COLORS[colorIdx]));

        loadImageFromBytes(imageData, null, "Image");
    }

    /** Called when we have metadata – checks local cache first, otherwise requests download. */
    public void setDataAndDownload(String senderName, FileMetadata meta, Date timestamp, String localPath) {
        senderLabel.setText(senderName);
        if (timestamp != null) {
            timestampLabel.setText(new SimpleDateFormat("HH:mm").format(timestamp));
        }
        int colorIdx = Math.abs(senderName.hashCode()) % AVATAR_COLORS.length;
        avatarCircle.setFill(Color.web(AVATAR_COLORS[colorIdx]));

        if (localPath != null) {
            Path lp = Paths.get(localPath);
            if (Files.exists(lp)) {
                try {
                    byte[] data = Files.readAllBytes(lp);
                    loadImageFromBytes(data, lp.toFile(), meta.getOriginalName());
                    return;
                } catch (Exception ignored) {}
            }
        }

        Path fallbackPath = Paths.get(DOWNLOADS_DIR, meta.getOriginalName());
        if (Files.exists(fallbackPath)) {
            try {
                byte[] data = Files.readAllBytes(fallbackPath);
                loadImageFromBytes(data, fallbackPath.toFile(), meta.getOriginalName());
                return;
            } catch (Exception ignored) {}
        }

        statusLabel.setText("Downloading image...");

        // Register one-shot download callback
        ImageDownloadRegistry.register(meta.getId(), imageData -> {
            Platform.runLater(() -> {
                File savedFile = null;
                try {
                    Path dir = Paths.get(DOWNLOADS_DIR);
                    if (!Files.exists(dir)) {
                        Files.createDirectories(dir);
                    }
                    Path filePath = dir.resolve(meta.getOriginalName());
                    Files.write(filePath, imageData);
                    savedFile = filePath.toFile();
                } catch (Exception e) {
                    System.err.println("Could not cache image to disk: " + e.getMessage());
                }
                loadImageFromBytes(imageData, savedFile, meta.getOriginalName());
            });
        });

        // Request file from server
        try {
            ObjectNode payload = JsonUtil.createObjectNode();
            payload.put("fileId", meta.getId());
            LoginController.connection.sendMessage(new Message(MessageType.DOWNLOAD_FILE_REQ, payload));
        } catch (Exception e) {
            Platform.runLater(() -> statusLabel.setText("Failed to load image."));
        }
    }

    private void loadImageFromBytes(byte[] data, File localFile, String title) {
        if (data == null || data.length == 0) {
            statusLabel.setText("Image unavailable.");
            return;
        }
        try {
            Image img = new Image(new ByteArrayInputStream(data));
            imageView.setImage(img);
            imageView.setCursor(Cursor.HAND);
            Tooltip.install(imageView, new Tooltip("Click to view full image"));
            imageView.setOnMouseClicked(e -> MediaViewer.openImageViewer(title, img, localFile));
            statusLabel.setText("");
        } catch (Exception e) {
            statusLabel.setText("Failed to decode image.");
        }
    }
}
