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
    @FXML private javafx.scene.control.PasswordField passwordField;
    @FXML private javafx.scene.control.PasswordField confirmPasswordField;
    @FXML private TextField serverIpField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;

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
        String password = passwordField.getText().trim();
        String ip = serverIpField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || ip.isEmpty()) {
            showAlert("Validation Error", "Fields cannot be empty.");
            return;
        }

        loginButton.setDisable(true);
        registerButton.setDisable(true);

        new Thread(() -> {
            try {
                if (connection == null || !connection.isConnected()) {
                    connection = new TCPConnection();
                    connection.connect(ip, 8888);
                    connection.setOnMessageReceived(this::handleMessage);
                }

                com.fasterxml.jackson.databind.node.ObjectNode payload = JsonUtil.createObjectNode();
                payload.put("username", username);
                payload.put("password", password);
                connection.sendMessage(new Message(MessageType.LOGIN, payload));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Connection Error", "Could not connect to server " + ip);
                    loginButton.setDisable(false);
                    registerButton.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    public void onRegisterClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String ip = serverIpField.getText().trim();

        String confirmPassword = confirmPasswordField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || ip.isEmpty()) {
            showAlert("Validation Error", "Fields cannot be empty.");
            return;
        }

//        if (username.length() < 3 || username.contains(" ")) {
//            showAlert("Validation Error", "Username must be at least 3 characters and contain no spaces.");
//            return;
//        }
//
//        if (password.length() < 6) {
//            showAlert("Validation Error", "Password must be at least 6 characters.");
//            return;
//        }

        if (!password.equals(confirmPassword)) {
            showAlert("Validation Error", "Passwords do not match.");
            return;
        }

        loginButton.setDisable(true);
        registerButton.setDisable(true);

        new Thread(() -> {
            try {
                if (connection == null || !connection.isConnected()) {
                    connection = new TCPConnection();
                    connection.connect(ip, 8888);
                    connection.setOnMessageReceived(this::handleMessage);
                }

                com.fasterxml.jackson.databind.node.ObjectNode payload = JsonUtil.createObjectNode();
                payload.put("username", username);
                payload.put("password", password);
                connection.sendMessage(new Message(MessageType.REGISTER, payload));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Connection Error", "Could not connect to server " + ip);
                    loginButton.setDisable(false);
                    registerButton.setDisable(false);
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
                } else if (msg.getType() == MessageType.REGISTER_RESP) {
                    showAlert("Success", "Registered successfully! You can now log in.");
                    loginButton.setDisable(false);
                    registerButton.setDisable(false);
                } else if (msg.getType() == MessageType.ERROR) {
                    showAlert("Error", msg.getPayload().asText());
                    loginButton.setDisable(false);
                    registerButton.setDisable(false);
                    if (connection != null) connection.close();
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
