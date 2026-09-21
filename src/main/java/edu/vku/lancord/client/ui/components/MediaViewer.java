package edu.vku.lancord.client.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MediaViewer {

    private static final String DARK_BG = "-fx-background-color: #1E1F22;";
    private static final String CARD_BG = "-fx-background-color: #2B2D31; -fx-background-radius: 8px;";
    private static final String TEXT_FILL = "-fx-text-fill: #DBDEE1;";
    private static final String BTN_STYLE = "-fx-background-color: #5865F2; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4px; -fx-cursor: hand; -fx-padding: 6 14;";

    /**
     * Opens a dedicated image preview window.
     */
    public static void openImageViewer(String title, Image image, File localFile) {
        if (image == null) return;

        Stage stage = new Stage();
        stage.setTitle(title != null ? title : "Image Viewer");

        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        // Responsive sizing
        double initW = Math.min(Math.max(image.getWidth(), 400), 900);
        double initH = Math.min(Math.max(image.getHeight(), 300), 650);
        imageView.setFitWidth(initW);
        imageView.setFitHeight(initH);

        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setStyle("-fx-background-color: #111214; -fx-padding: 10;");

        ScrollPane scrollPane = new ScrollPane(imageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: #111214; -fx-border-color: transparent;");

        // Top bar with info and actions
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 16, 10, 16));
        topBar.setStyle(CARD_BG);

        Label titleLabel = new Label(title != null ? title : "Image");
        titleLabel.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label dimLabel = new Label(String.format("(%.0f × %.0f px)", image.getWidth(), image.getHeight()));
        dimLabel.setStyle("-fx-text-fill: #949BA4; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button saveAsBtn = new Button("Save As...");
        saveAsBtn.setStyle(BTN_STYLE);
        saveAsBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Image");
            chooser.setInitialFileName(title != null ? title : "image.png");
            File dest = chooser.showSaveDialog(stage);
            if (dest != null && localFile != null && localFile.exists()) {
                try {
                    Files.copy(localFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        Button openSystemBtn = new Button("Open in System App");
        openSystemBtn.setStyle("-fx-background-color: #4E5058; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 6 12; -fx-background-radius: 4px;");
        openSystemBtn.setOnAction(e -> {
            if (localFile != null && localFile.exists()) {
                openInSystem(localFile);
            }
        });

        topBar.getChildren().addAll(titleLabel, dimLabel, spacer, saveAsBtn, openSystemBtn);

        BorderPane root = new BorderPane();
        root.setStyle(DARK_BG);
        root.setTop(topBar);
        root.setCenter(scrollPane);

        Scene scene = new Scene(root, initW + 40, initH + 90);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Opens an in-app video player window with playback controls.
     */
    public static void openVideoPlayer(String title, File videoFile) {
        if (videoFile == null || !videoFile.exists()) return;

        Stage stage = new Stage();
        stage.setTitle(title != null ? title : "Video Player - " + videoFile.getName());

        try {
            Media media = new Media(videoFile.toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            MediaView mediaView = new MediaView(player);
            mediaView.setPreserveRatio(true);
            mediaView.setFitWidth(800);
            mediaView.setFitHeight(480);

            StackPane videoPane = new StackPane(mediaView);
            videoPane.setStyle("-fx-background-color: #000000;");
            videoPane.setMinHeight(300);

            // Controls Bar
            HBox controls = new HBox(12);
            controls.setAlignment(Pos.CENTER_LEFT);
            controls.setPadding(new Insets(10, 16, 10, 16));
            controls.setStyle(CARD_BG);

            Button playPauseBtn = new Button("▶");
            playPauseBtn.setStyle(BTN_STYLE);

            Slider timeSlider = new Slider();
            timeSlider.setMin(0);
            timeSlider.setMax(100);
            timeSlider.setValue(0);
            HBox.setHgrow(timeSlider, Priority.ALWAYS);

            Label timeLabel = new Label("00:00 / 00:00");
            timeLabel.setStyle(TEXT_FILL);

            Slider volumeSlider = new Slider(0, 1, 0.8);
            volumeSlider.setPrefWidth(80);
            player.volumeProperty().bind(volumeSlider.valueProperty());

            Label volIcon = new Label("🔊");
            volIcon.setStyle(TEXT_FILL);

            Button openSystemBtn = new Button("Open in System Player");
            openSystemBtn.setStyle("-fx-background-color: #4E5058; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 6 12; -fx-background-radius: 4px;");
            openSystemBtn.setOnAction(e -> {
                player.pause();
                openInSystem(videoFile);
            });

            // Player listeners
            player.setOnReady(() -> {
                Duration total = media.getDuration();
                timeSlider.setMax(total.toSeconds());
                timeLabel.setText(formatTime(Duration.ZERO) + " / " + formatTime(total));
                player.play();
                playPauseBtn.setText("⏸");
            });

            player.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!timeSlider.isValueChanging()) {
                    timeSlider.setValue(newTime.toSeconds());
                }
                timeLabel.setText(formatTime(newTime) + " / " + formatTime(media.getDuration()));
            });

            timeSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                if (!isChanging) {
                    player.seek(Duration.seconds(timeSlider.getValue()));
                }
            });

            timeSlider.setOnMouseClicked(e -> player.seek(Duration.seconds(timeSlider.getValue())));

            playPauseBtn.setOnAction(e -> {
                MediaPlayer.Status status = player.getStatus();
                if (status == MediaPlayer.Status.PLAYING) {
                    player.pause();
                    playPauseBtn.setText("▶");
                } else {
                    player.play();
                    playPauseBtn.setText("⏸");
                }
            });

            player.setOnEndOfMedia(() -> {
                player.stop();
                playPauseBtn.setText("▶");
                timeSlider.setValue(0);
            });

            controls.getChildren().addAll(playPauseBtn, timeSlider, timeLabel, volIcon, volumeSlider, openSystemBtn);

            BorderPane root = new BorderPane();
            root.setStyle(DARK_BG);
            root.setCenter(videoPane);
            root.setBottom(controls);

            Scene scene = new Scene(root, 840, 560);
            stage.setScene(scene);

            stage.setOnCloseRequest(e -> {
                player.stop();
                player.dispose();
            });

            stage.show();
        } catch (Exception e) {
            // If JavaFX Media fails (e.g. unsupported codec or platform issue), fallback to system player
            System.err.println("JavaFX MediaPlayer failed, falling back to system default: " + e.getMessage());
            openInSystem(videoFile);
        }
    }

    public static void openInSystem(File file) {
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    public static String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown()) return "00:00";
        int totalSeconds = (int) Math.floor(duration.toSeconds());
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
