package edu.vku.lancord.client;

import atlantafx.base.theme.PrimerDark;
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
        
        switchScene("login.fxml", "LANCord - Login");
        
        stage.show();
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
