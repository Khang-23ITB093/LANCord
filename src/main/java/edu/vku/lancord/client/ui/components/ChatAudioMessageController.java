package edu.vku.lancord.client.ui.components;

import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.vku.lancord.client.ui.LoginController;
import edu.vku.lancord.common.model.FileMetadata;
import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;
import edu.vku.lancord.common.protocol.MessageType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatAudioMessageController {

    @FXML private Circle avatarCircle;
    @FXML private Label senderLabel;
    @FXML private Label timestampLabel;
    @FXML private Label fileNameLabel;
    @FXML private Label fileSizeLabel;
    @FXML private Button playButton;
    @FXML private Slider timeSlider;
    @FXML private Label timeLabel;
    @FXML private Button downloadButton;
    @FXML private Label statusLabel;

    private static final String DOWNLOADS_DIR = "client_downloads/";
    private static final String[] AVATAR_COLORS = {
        "#5865F2", "#57F287", "#FEE75C", "#EB459E", "#ED4245",
        "#23A559", "#3BA55C", "#FAA61A"
    };

    private FileMetadata fileMetadata;
    private MediaPlayer mediaPlayer;
    private boolean isDownloading = false;
    private String localSenderPath;

    public void setData(String senderName, FileMetadata meta, Date timestamp, String localPath) {
        this.fileMetadata = meta;
        this.localSenderPath = localPath;

        senderLabel.setText(senderName);
        fileNameLabel.setText(meta.getOriginalName());
        fileSizeLabel.setText(formatSize(meta.getFileSize()));

        if (timestamp != null) {
            timestampLabel.setText(new SimpleDateFormat("HH:mm").format(timestamp));
        }
        int colorIdx = Math.abs(senderName.hashCode()) % AVATAR_COLORS.length;
        avatarCircle.setFill(Color.web(AVATAR_COLORS[colorIdx]));

        if (localSenderPath != null && Files.exists(Paths.get(localSenderPath))) {
            statusLabel.setText("Already on device");
            initMediaPlayer(new File(localSenderPath));
            return;
        }

        Path path = Paths.get(DOWNLOADS_DIR, meta.getOriginalName());
        if (Files.exists(path)) {
            statusLabel.setText("Downloaded");
            initMediaPlayer(path.toFile());
        }
    }

    private void initMediaPlayer(File audioFile) {
        if (mediaPlayer != null) {
            mediaPlayer.dispose();
        }
        try {
            Media media = new Media(audioFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);

            mediaPlayer.setOnReady(() -> {
                Duration total = media.getDuration();
                timeSlider.setMax(total.toSeconds());
                timeLabel.setText(formatTime(Duration.ZERO));
            });

            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!timeSlider.isValueChanging()) {
                    timeSlider.setValue(newTime.toSeconds());
                }
                timeLabel.setText(formatTime(newTime));
            });

            timeSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                if (!isChanging && mediaPlayer != null) {
                    mediaPlayer.seek(Duration.seconds(timeSlider.getValue()));
                }
            });

            timeSlider.setOnMouseClicked(e -> {
                if (mediaPlayer != null) {
                    mediaPlayer.seek(Duration.seconds(timeSlider.getValue()));
                }
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                mediaPlayer.stop();
                playButton.setText("▶ Play");
                timeSlider.setValue(0);
            });
        } catch (Exception e) {
            statusLabel.setText("Audio format not supported by player.");
        }
    }

    @FXML
    public void onPlayPause() {
        if (fileMetadata == null) return;

        File targetFile = null;
        if (localSenderPath != null && Files.exists(Paths.get(localSenderPath))) {
            targetFile = new File(localSenderPath);
        } else {
            Path path = Paths.get(DOWNLOADS_DIR, fileMetadata.getOriginalName());
            if (Files.exists(path)) {
                targetFile = path.toFile();
            }
        }

        if (targetFile == null) {
            // Need to download first
            downloadAndRun(() -> {
                Path downloadedPath = Paths.get(DOWNLOADS_DIR, fileMetadata.getOriginalName());
                initMediaPlayer(downloadedPath.toFile());
                if (mediaPlayer != null) {
                    mediaPlayer.play();
                    playButton.setText("⏸ Pause");
                }
            });
            return;
        }

        if (mediaPlayer == null) {
            initMediaPlayer(targetFile);
        }

        if (mediaPlayer != null) {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                playButton.setText("▶ Play");
            } else {
                mediaPlayer.play();
                playButton.setText("⏸ Pause");
            }
        }
    }

    @FXML
    public void onDownload() {
        if (fileMetadata == null) return;

        if (localSenderPath != null && Files.exists(Paths.get(localSenderPath))) {
            MediaViewer.openInSystem(new File(localSenderPath));
            return;
        }

        Path path = Paths.get(DOWNLOADS_DIR, fileMetadata.getOriginalName());
        if (Files.exists(path)) {
            MediaViewer.openInSystem(path.toFile());
            return;
        }
        downloadAndRun(null);
    }

    private void downloadAndRun(Runnable onComplete) {
        if (isDownloading) return;
        isDownloading = true;
        statusLabel.setText("Downloading audio...");

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
                    isDownloading = false;

                    if (onComplete != null) {
                        onComplete.run();
                    }
                } catch (Exception e) {
                    statusLabel.setText("Save failed: " + e.getMessage());
                    isDownloading = false;
                }
            });
        });

        try {
            ObjectNode payload = JsonUtil.createObjectNode();
            payload.put("fileId", fileMetadata.getId());
            LoginController.connection.sendMessage(new Message(MessageType.DOWNLOAD_FILE_REQ, payload));
        } catch (Exception e) {
            statusLabel.setText("Request failed.");
            isDownloading = false;
        }
    }

    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown()) return "00:00";
        int totalSeconds = (int) Math.floor(duration.toSeconds());
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
