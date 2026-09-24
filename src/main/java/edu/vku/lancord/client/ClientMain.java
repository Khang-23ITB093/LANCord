package edu.vku.lancord.client;

import atlantafx.base.theme.PrimerDark;
import edu.vku.lancord.client.ui.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ClientMain extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        primaryStage = stage;

        // Ensure client downloads directory exists
        java.nio.file.Path downloadDir = java.nio.file.Paths.get("client_downloads");
        if (!java.nio.file.Files.exists(downloadDir)) {
            java.nio.file.Files.createDirectories(downloadDir);
        }

        // Cleanly close all threads when the window is closed
        stage.setOnCloseRequest(event -> {
            cleanup();
        });

        switchScene("login.fxml", "LANCord - Login");
        stage.show();
    }

    @Override
    public void stop() {
        cleanup();
    }

    private static void cleanup() {
        try {
            // Close TCP connection so receive loop exits
            if (LoginController.connection != null) {
                LoginController.connection.close();
            }
        } catch (Exception e) {
            // Ignore — we're shutting down anyway
        }
        // Force exit to kill any remaining non-daemon threads (e.g. webcam drivers)
        System.exit(0);
    }

    public static void switchScene(String fxml, String title) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(ClientMain.class.getResource("/edu/vku/lancord/client/ui/" + fxml));
            Parent root = fxmlLoader.load();
            Scene scene = new Scene(root);
            primaryStage.setTitle(title);
            primaryStage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
