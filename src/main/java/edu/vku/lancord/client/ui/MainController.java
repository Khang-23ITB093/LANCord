package edu.vku.lancord.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.vku.lancord.client.network.UDPStreamReceiver;
import edu.vku.lancord.client.network.UDPStreamSender;
import edu.vku.lancord.common.model.*;
import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;
import edu.vku.lancord.common.protocol.MessageType;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

public class MainController {

    @FXML private ListView<String> onlineUsersList;
    @FXML private ListView<String> groupsList;
    @FXML private ListView<String> channelsList;
    @FXML private ListView<String> chatArea;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private Button uploadButton;
    @FXML private Button goLiveButton;
    @FXML private Label currentChatLabel;

    private ObservableList<String> usersObservable = FXCollections.observableArrayList();
    private ObservableList<String> groupsObservable = FXCollections.observableArrayList();
    private ObservableList<String> channelsObservable = FXCollections.observableArrayList();
    private ObservableList<String> chatObservable = FXCollections.observableArrayList();

    private List<User> currentOnlineUsers;
    private int currentContextId = -1;
    private String currentContextType = ""; // "DM" or "GROUP"

    private UDPStreamSender currentStreamSender;
    private UDPStreamReceiver currentStreamReceiver;

    @FXML
    public void initialize() {
        onlineUsersList.setItems(usersObservable);
        groupsList.setItems(groupsObservable);
        channelsList.setItems(channelsObservable);
        chatArea.setItems(chatObservable);

        LoginController.connection.setOnMessageReceived(this::handleMessage);

        onlineUsersList.setOnMouseClicked(event -> {
            int index = onlineUsersList.getSelectionModel().getSelectedIndex();
            if (index >= 0 && index < currentOnlineUsers.size()) {
                User u = currentOnlineUsers.get(index);
                if (u.getId() != LoginController.currentUser.getId()) {
                    openDM(u);
                }
            }
        });
    }

    private void handleMessage(Message msg) {
        Platform.runLater(() -> {
            try {
                switch (msg.getType()) {
                    case ONLINE_USERS_UPDATE:
                        updateOnlineUsers(msg.getPayload());
                        break;
                    case NEW_MESSAGE_NOTIFY:
                        receiveChatMessage(msg.getPayload());
                        break;
                    case STREAM_STARTED:
                        handleStreamStarted(msg.getPayload());
                        break;
                    // Add other handlers for group creation, etc.
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateOnlineUsers(JsonNode payload) throws Exception {
        currentOnlineUsers = JsonUtil.treeToValue(payload, new TypeReference<List<User>>() {});
        usersObservable.clear();
        for (User u : currentOnlineUsers) {
            String display = u.getUsername() + (u.getId() == LoginController.currentUser.getId() ? " (You)" : "");
            usersObservable.add(display);
        }
    }

    private void openDM(User target) {
        currentContextType = "DM";
        currentContextId = target.getId();
        currentChatLabel.setText("Chatting with: " + target.getUsername());
        chatObservable.clear(); // Clear previous chat
        goLiveButton.setDisable(true); // Can only stream in channels
    }

    @FXML
    public void onSendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || currentContextId == -1) return;

        try {
            ObjectNode payload = JsonUtil.valueToTree(new Object()).deepCopy();
            payload.put("receiverId", currentContextId);
            payload.put("content", text);
            
            MessageType type = currentContextType.equals("DM") ? MessageType.SEND_DM : MessageType.SEND_GROUP_MSG;
            LoginController.connection.sendMessage(new Message(type, payload));
            
            messageField.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void receiveChatMessage(JsonNode payload) throws Exception {
        ChatMessage msg = JsonUtil.treeToValue(payload, ChatMessage.class);
        
        // Filter if it belongs to current context
        boolean shouldDisplay = false;
        if (msg.getType().equals("DM")) {
            if (msg.getSenderId() == currentContextId || 
               (msg.getSenderId() == LoginController.currentUser.getId() && msg.getReceiverId() == currentContextId)) {
                shouldDisplay = true;
            }
        } else {
            if (msg.getReceiverId() == currentContextId) {
                shouldDisplay = true;
            }
        }
        
        if (shouldDisplay) {
            chatObservable.add(msg.getSenderName() + ": " + msg.getContent());
        }
    }

    @FXML
    public void onGoLive() {
        if (!currentContextType.equals("GROUP")) {
            Alert a = new Alert(Alert.AlertType.WARNING, "You can only Go Live in a Group Channel.");
            a.show();
            return;
        }

        try {
            ObjectNode payload = JsonUtil.valueToTree(new Object()).deepCopy();
            payload.put("channelId", currentContextId); // Assuming contextId is channelId for simplicity
            payload.put("groupId", currentContextId);
            LoginController.connection.sendMessage(new Message(MessageType.STREAM_START, payload));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleStreamStarted(JsonNode payload) {
        String ip = payload.get("multicastIp").asText();
        int port = payload.get("multicastPort").asInt();
        String streamerName = payload.get("streamerName").asText();

        if (streamerName.equals(LoginController.currentUser.getUsername())) {
            // I am the streamer
            if (currentStreamSender != null) currentStreamSender.stop();
            currentStreamSender = new UDPStreamSender(ip, port);
            currentStreamSender.start();
            chatObservable.add("System: You started streaming!");
        } else {
            // Ask if want to join stream
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Stream Started");
            alert.setHeaderText(streamerName + " has started a stream.");
            alert.setContentText("Do you want to watch?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                openStreamView(ip, port, streamerName);
            }
        }
    }

    private void openStreamView(String ip, int port, String streamerName) {
        Stage stage = new Stage();
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(800);
        imageView.setFitHeight(600);

        VBox root = new VBox(imageView);
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setTitle("Watching: " + streamerName);

        if (currentStreamReceiver != null) currentStreamReceiver.stop();
        currentStreamReceiver = new UDPStreamReceiver(ip, port);
        currentStreamReceiver.setOnFrameReceived(img -> Platform.runLater(() -> imageView.setImage(img)));
        currentStreamReceiver.start();

        stage.setOnCloseRequest(e -> currentStreamReceiver.stop());
        stage.show();
    }
}
