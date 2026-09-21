package edu.vku.lancord.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import edu.vku.lancord.client.ClientMain;
import edu.vku.lancord.client.network.TCPConnection;
import edu.vku.lancord.common.model.User;
import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;
import edu.vku.lancord.common.protocol.MessageType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private TextField serverIpField;
    @FXML private Button loginButton;

    public static TCPConnection connection;
    public static User currentUser;
    public static Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();

    @FXML
    public void initialize() {
        serverIpField.setText("127.0.0.1");
    }

    @FXML
    public void onLoginClick() {
        String username = usernameField.getText().trim();
        String ip = serverIpField.getText().trim();

        if (username.isEmpty() || ip.isEmpty()) return;

        loginButton.setDisable(true);

        new Thread(() -> {
            try {
                connection = new TCPConnection();
                connection.connect(ip, 8888);
                connection.setOnMessageReceived(this::handleMessage);

                // Send login message
                JsonNode payload = JsonUtil.valueToTree(new User(0, username));
                connection.sendMessage(new Message(MessageType.LOGIN, payload));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Connection Error", "Could not connect to server " + ip);
                    loginButton.setDisable(false);
                });
            }
        }).start();
    }

    private void handleMessage(Message msg) {
        Platform.runLater(() -> {
            try {
                if (msg.getType() == MessageType.LOGIN_RESP) {
                    currentUser = JsonUtil.treeToValue(msg.getPayload(), User.class);
                    ClientMain.switchScene("main.fxml", "LANCord - " + currentUser.getUsername());
                } else if (msg.getType() == MessageType.ERROR) {
                    showAlert("Login Error", msg.getPayload().asText());
                    loginButton.setDisable(false);
                    connection.close();
                } else {
                    pendingMessages.add(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
