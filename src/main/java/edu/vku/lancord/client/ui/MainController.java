package edu.vku.lancord.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.vku.lancord.client.network.UDPStreamReceiver;
import edu.vku.lancord.client.network.UDPStreamSender;
import edu.vku.lancord.client.ui.components.*;
import edu.vku.lancord.common.model.*;
import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;
import edu.vku.lancord.common.protocol.MessageType;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

public class MainController {

    // ── FXML bindings ──────────────────────────────────────────────────────
    @FXML private ListView<String> onlineUsersList;
    @FXML private ListView<String> groupsList;
    @FXML private ListView<String> channelsList;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatBox;
    @FXML private TextField messageField;
    @FXML private Button uploadButton;
    @FXML private Button goLiveButton;
    @FXML private Label currentChatLabel;

    // ── Observable lists ──────────────────────────────────────────────────
    private final ObservableList<String> usersObservable  = FXCollections.observableArrayList();
    private final ObservableList<String> groupsObservable = FXCollections.observableArrayList();
    private final ObservableList<String> channelsObservable = FXCollections.observableArrayList();

    // ── State ─────────────────────────────────────────────────────────────
    private List<User> currentOnlineUsers;
    private int    currentContextId   = -1;
    private String currentContextType = "";  // "DM" or "GROUP"

    // ── Streaming ────────────────────────────────────────────────────────
    private UDPStreamSender   currentStreamSender;
    private UDPStreamReceiver currentStreamReceiver;

    // ── Component FXML paths ─────────────────────────────────────────────
    private static final String COMP_BASE = "/edu/vku/lancord/client/ui/components/";

    // ── Image / video / audio extensions ─────────────────────────────────
    private static final Set<String> IMAGE_EXT = Set.of("png","jpg","jpeg","gif","bmp","webp");
    private static final Set<String> VIDEO_EXT = Set.of("mp4","avi","mkv","mov","wmv","flv","webm");
    private static final Set<String> AUDIO_EXT = Set.of("mp3","wav","m4a","aac","ogg","flac");

    // ── Upload chunk size: 512 KB ─────────────────────────────────────────
    private static final int CHUNK_SIZE = 512 * 1024;

    // ── Pending upload tracking (fileId -> file bytes) ────────────────────
    private final Map<Integer, byte[]> pendingUploads = new HashMap<>();
    private final Map<Integer, String> pendingUploadPaths = new HashMap<>();
    private final Map<Integer, String> localUploadPaths = new HashMap<>();

    @FXML
    public void initialize() {
        loadLocalUploadPaths();
        onlineUsersList.setItems(usersObservable);
        groupsList.setItems(groupsObservable);
        channelsList.setItems(channelsObservable);

        setupListCellFactory(onlineUsersList);
        setupListCellFactory(groupsList);
        setupListCellFactory(channelsList);

        LoginController.connection.setOnMessageReceived(this::handleMessage);

        // Drain messages that arrived before this controller was ready
        Message pending;
        while ((pending = LoginController.pendingMessages.poll()) != null) {
            handleMessage(pending);
        }

        onlineUsersList.setOnMouseClicked(event -> {
            int index = onlineUsersList.getSelectionModel().getSelectedIndex();
            if (index >= 0 && currentOnlineUsers != null && index < currentOnlineUsers.size()) {
                User u = currentOnlineUsers.get(index);
                if (u.getId() != LoginController.currentUser.getId()) {
                    openDM(u);
                }
            }
        });

        // Auto-scroll chat to bottom when new nodes are added
        chatBox.heightProperty().addListener((obs, oldH, newH) ->
            chatScrollPane.setVvalue(1.0));
    }

    private void loadLocalUploadPaths() {
        try {
            File file = new File("client_downloads/upload_cache.properties");
            if (file.exists()) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(file)) {
                    props.load(fis);
                    for (String key : props.stringPropertyNames()) {
                        localUploadPaths.put(Integer.parseInt(key), props.getProperty(key));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load local upload paths: " + e.getMessage());
        }
    }

    private void saveLocalUploadPaths() {
        try {
            Path dir = Paths.get("client_downloads");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Properties props = new Properties();
            for (Map.Entry<Integer, String> entry : localUploadPaths.entrySet()) {
                props.setProperty(String.valueOf(entry.getKey()), entry.getValue());
            }
            try (FileOutputStream fos = new FileOutputStream("client_downloads/upload_cache.properties")) {
                props.store(fos, "Local paths for uploaded files");
            }
        } catch (Exception e) {
            System.err.println("Failed to save local upload paths: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  LIST CELL FACTORIES
    // ═══════════════════════════════════════════════════════════════

    private void setupListCellFactory(ListView<String> listView) {
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                HBox box = new HBox(10);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                Circle avatar = new Circle(12, javafx.scene.paint.Color.web("#5865F2"));
                Label text = new Label(item);
                text.setTextFill(javafx.scene.paint.Color.web("#DBDEE1"));
                box.getChildren().addAll(avatar, text);
                setGraphic(box);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  MESSAGE ROUTING
    // ═══════════════════════════════════════════════════════════════

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
                    case CHAT_HISTORY_RESP:
                        handleChatHistory(msg.getPayload());
                        break;
                    case FILES_IN_CONTEXT_RESP:
                        handleFilesInContext(msg.getPayload());
                        break;
                    case UPLOAD_FILE_RESP:
                        handleUploadResp(msg.getPayload());
                        break;
                    case FILE_UPLOAD_COMPLETE:
                    case FILE_UPLOAD_NOTIFY:
                        handleFileUploadComplete(msg.getPayload(), msg.getType() == MessageType.FILE_UPLOAD_COMPLETE);
                        break;
                    case DOWNLOAD_FILE_RESP:
                        handleDownloadResp(msg.getPayload());
                        break;
                    case STREAM_STARTED:
                        handleStreamStarted(msg.getPayload());
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  ONLINE USERS
    // ═══════════════════════════════════════════════════════════════

    private void updateOnlineUsers(JsonNode payload) throws Exception {
        currentOnlineUsers = JsonUtil.treeToValue(payload, new TypeReference<List<User>>() {});
        usersObservable.clear();
        if (currentOnlineUsers != null) {
            for (User u : currentOnlineUsers) {
                if (u == null) continue;
                String display = u.getUsername() +
                    (u.getId() == LoginController.currentUser.getId() ? " (You)" : "");
                usersObservable.add(display);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  OPEN DM
    // ═══════════════════════════════════════════════════════════════

    private void openDM(User target) {
        currentContextType = "DM";
        currentContextId   = target.getId();
        currentChatLabel.setText("Chatting with: " + target.getUsername());
        chatBox.getChildren().clear();
        goLiveButton.setDisable(true);

        // Request text history
        try {
            ObjectNode p = JsonUtil.createObjectNode();
            p.put("type", "DM");
            p.put("contextId", target.getId());
            LoginController.connection.sendMessage(new Message(MessageType.GET_CHAT_HISTORY, p));
        } catch (Exception e) { e.printStackTrace(); }

        // Request file history
        try {
            ObjectNode p = JsonUtil.createObjectNode();
            p.put("contextType", "DM");
            p.put("contextId", target.getId());
            LoginController.connection.sendMessage(new Message(MessageType.GET_FILES_IN_CONTEXT, p));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHAT HISTORY (text)
    // ═══════════════════════════════════════════════════════════════

    private void handleChatHistory(JsonNode payload) throws Exception {
        String type      = payload.get("type").asText();
        int    contextId = payload.get("contextId").asInt();
        if (!currentContextType.equalsIgnoreCase(type) || currentContextId != contextId) return;

        List<ChatMessage> history = JsonUtil.treeToValue(
            payload.get("messages"), new TypeReference<List<ChatMessage>>() {});
        chatBox.getChildren().clear();
        if (history != null) {
            for (ChatMessage m : history) {
                String sender = m.getSenderName() != null ? m.getSenderName() : "Unknown";
                Date   ts     = m.getCreatedAt() != null ? new Date(m.getCreatedAt().getTime()) : null;
                chatBox.getChildren().add(buildTextMessage(sender, m.getContent(), ts));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  FILES IN CONTEXT (file history after opening DM/Group)
    // ═══════════════════════════════════════════════════════════════

    private void handleFilesInContext(JsonNode payload) throws Exception {
        String contextType = payload.get("contextType").asText();
        int    contextId   = payload.get("contextId").asInt();
        if (!currentContextType.equalsIgnoreCase(contextType) || currentContextId != contextId) return;

        List<FileMetadata> files = JsonUtil.treeToValue(
            payload.get("files"), new TypeReference<List<FileMetadata>>() {});
        if (files == null) return;

        for (FileMetadata meta : files) {
            String sender = meta.getUploaderName() != null ? meta.getUploaderName() : "Unknown";
            Date   ts     = meta.getCreatedAt() != null ? new Date(meta.getCreatedAt().getTime()) : null;
            chatBox.getChildren().add(buildFileMessage(sender, meta, ts));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  INCOMING CHAT MESSAGE (real-time)
    // ═══════════════════════════════════════════════════════════════

    private void receiveChatMessage(JsonNode payload) throws Exception {
        ChatMessage msg = JsonUtil.treeToValue(payload, ChatMessage.class);
        boolean shouldDisplay = false;
        if (msg.getType().equals("DM")) {
            if (msg.getSenderId() == currentContextId ||
               (msg.getSenderId() == LoginController.currentUser.getId() &&
                msg.getReceiverId() == currentContextId)) {
                shouldDisplay = true;
            }
        } else {
            if (msg.getReceiverId() == currentContextId) shouldDisplay = true;
        }
        if (!shouldDisplay) return;

        String sender = msg.getSenderName() != null ? msg.getSenderName() : "Unknown";
        Date   ts     = msg.getCreatedAt() != null ? new Date(msg.getCreatedAt().getTime()) : new Date();
        chatBox.getChildren().add(buildTextMessage(sender, msg.getContent(), ts));
    }

    // ═══════════════════════════════════════════════════════════════
    //  FILE UPLOAD FLOW
    // ═══════════════════════════════════════════════════════════════

    @FXML
    public void onUploadFile() {
        if (currentContextId == -1) {
            showAlert("No conversation selected", "Please open a DM or channel first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to send");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showOpenDialog(uploadButton.getScene().getWindow());
        if (file == null) return;

        if (file.length() > 50L * 1024 * 1024) {
            showAlert("File too large", "Maximum file size is 50 MB.");
            return;
        }

        new Thread(() -> {
            try {
                byte[] data = Files.readAllBytes(file.toPath());

                // Tell server we're about to upload
                ObjectNode initPayload = JsonUtil.createObjectNode();
                initPayload.put("filename", file.getName());
                initPayload.put("size", file.length());
                initPayload.put("contextType", currentContextType);
                initPayload.put("contextId", currentContextId);

                // Store data keyed temporarily by filename; server gives us a real fileId
                // We'll identify it in handleUploadResp via a waiting queue
                pendingUploads.put(file.getName().hashCode(), data);
                pendingUploadPaths.put(file.getName().hashCode(), file.getAbsolutePath());

                LoginController.connection.sendMessage(
                    new Message(MessageType.UPLOAD_FILE_INIT, initPayload));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert("Upload failed", e.getMessage()));
            }
        }).start();
    }

    /** Called when server responds with the allocated fileId for our upload. */
    private void handleUploadResp(JsonNode payload) throws Exception {
        FileMetadata meta = JsonUtil.treeToValue(payload, FileMetadata.class);
        if (meta == null) return;

        // Find our pending data by original name hash
        int nameKey = meta.getOriginalName().hashCode();
        byte[] data = pendingUploads.remove(nameKey);
        if (data == null) return;
        
        String localPath = pendingUploadPaths.remove(nameKey);
        if (localPath != null) {
            localUploadPaths.put(meta.getId(), localPath);
            saveLocalUploadPaths();
        }

        final FileMetadata metaFinal = meta;
        new Thread(() -> sendChunks(metaFinal.getId(), data)).start();
    }

    private void sendChunks(int fileId, byte[] data) {
        try {
            int offset = 0;
            while (offset < data.length) {
                int end   = Math.min(offset + CHUNK_SIZE, data.length);
                byte[] chunk = Arrays.copyOfRange(data, offset, end);

                ObjectNode chunkPayload = JsonUtil.createObjectNode();
                chunkPayload.put("fileId", fileId);
                chunkPayload.put("chunk", chunk); // Jackson auto-encodes as Base64
                LoginController.connection.sendMessage(
                    new Message(MessageType.FILE_CHUNK, chunkPayload));
                offset = end;

                Thread.sleep(5); // small backpressure
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Called when our own upload completes (FILE_UPLOAD_COMPLETE) or we receive notification (FILE_UPLOAD_NOTIFY). */
    private void handleFileUploadComplete(JsonNode payload, boolean isMyUpload) throws Exception {
        FileMetadata meta = JsonUtil.treeToValue(payload, FileMetadata.class);
        if (meta == null) return;

        // Only show if it belongs to our current conversation
        boolean relevant = false;
        if ("DM".equalsIgnoreCase(meta.getContextType())) {
            int myId = LoginController.currentUser.getId();
            relevant = (meta.getUploaderId() == currentContextId && meta.getContextId() == myId)
                    || (meta.getUploaderId() == myId && meta.getContextId() == currentContextId);
        } else {
            relevant = currentContextType.equalsIgnoreCase(meta.getContextType())
                    && currentContextId == meta.getContextId();
        }
        if (!relevant) return;

        String sender = meta.getUploaderName() != null ? meta.getUploaderName()
                      : LoginController.currentUser.getUsername();
        Date ts = meta.getCreatedAt() != null ? new Date(meta.getCreatedAt().getTime()) : new Date();

        chatBox.getChildren().add(buildFileMessage(sender, meta, ts));
    }

    // ═══════════════════════════════════════════════════════════════
    //  DOWNLOAD RESPONSE
    // ═══════════════════════════════════════════════════════════════

    private void handleDownloadResp(JsonNode payload) throws Exception {
        int fileId = payload.get("id").asInt();
        byte[] data = payload.get("data").binaryValue();

        // Dispatch to image callbacks first
        Consumer<byte[]> imgCallback = ImageDownloadRegistry.consume(fileId);
        if (imgCallback != null) {
            imgCallback.accept(data);
            return;
        }

        // Then file/video callbacks
        Consumer<byte[]> fileCallback = FileDownloadRegistry.consume(fileId);
        if (fileCallback != null) {
            fileCallback.accept(data);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  COMPONENT BUILDERS
    // ═══════════════════════════════════════════════════════════════

    private Node buildTextMessage(String sender, String content, Date timestamp) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(COMP_BASE + "ChatTextMessage.fxml"));
            Node node = loader.load();
            ChatTextMessageController ctrl = loader.getController();
            ctrl.setData(sender, content, timestamp);
            return node;
        } catch (Exception e) {
            e.printStackTrace();
            Label fallback = new Label(sender + ": " + content);
            fallback.setStyle("-fx-text-fill: #DBDEE1; -fx-padding: 4 16;");
            return fallback;
        }
    }

    private Node buildFileMessage(String sender, FileMetadata meta, Date timestamp) {
        String ext = getExtension(meta.getOriginalName()).toLowerCase();
        String localPath = localUploadPaths.get(meta.getId());

        if (IMAGE_EXT.contains(ext)) {
            return buildImageMessage(sender, meta, timestamp, localPath);
        } else if (VIDEO_EXT.contains(ext)) {
            return buildVideoMessage(sender, meta, timestamp, localPath);
        } else if (AUDIO_EXT.contains(ext)) {
            return buildAudioMessage(sender, meta, timestamp, localPath);
        } else {
            return buildGenericFileMessage(sender, meta, timestamp, localPath);
        }
    }

    private Node buildImageMessage(String sender, FileMetadata meta, Date timestamp, String localPath) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(COMP_BASE + "ChatImageMessage.fxml"));
            Node node = loader.load();
            ChatImageMessageController ctrl = loader.getController();
            ctrl.setDataAndDownload(sender, meta, timestamp, localPath);
            return node;
        } catch (Exception e) {
            e.printStackTrace();
            return buildGenericFileMessage(sender, meta, timestamp, localPath);
        }
    }

    private Node buildGenericFileMessage(String sender, FileMetadata meta, Date timestamp, String localPath) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(COMP_BASE + "ChatFileMessage.fxml"));
            Node node = loader.load();
            ChatFileMessageController ctrl = loader.getController();
            ctrl.setData(sender, meta, timestamp, false, localPath);
            return node;
        } catch (Exception e) {
            e.printStackTrace();
            Label fallback = new Label(sender + " sent: " + meta.getOriginalName());
            fallback.setStyle("-fx-text-fill: #DBDEE1; -fx-padding: 4 16;");
            return fallback;
        }
    }

    private Node buildVideoMessage(String sender, FileMetadata meta, Date timestamp, String localPath) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(COMP_BASE + "ChatVideoMessage.fxml"));
            Node node = loader.load();
            ChatFileMessageController ctrl = loader.getController();
            ctrl.setData(sender, meta, timestamp, true, localPath);
            return node;
        } catch (Exception e) {
            e.printStackTrace();
            return buildGenericFileMessage(sender, meta, timestamp, localPath);
        }
    }

    private Node buildAudioMessage(String sender, FileMetadata meta, Date timestamp, String localPath) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(COMP_BASE + "ChatAudioMessage.fxml"));
            Node node = loader.load();
            ChatAudioMessageController ctrl = loader.getController();
            ctrl.setData(sender, meta, timestamp, localPath);
            return node;
        } catch (Exception e) {
            e.printStackTrace();
            return buildGenericFileMessage(sender, meta, timestamp, localPath);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SEND TEXT MESSAGE
    // ═══════════════════════════════════════════════════════════════

    @FXML
    public void onSendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || currentContextId == -1) return;

        try {
            ObjectNode payload = JsonUtil.createObjectNode();
            payload.put("receiverId", currentContextId);
            payload.put("content", text);
            MessageType type = currentContextType.equals("DM") ? MessageType.SEND_DM : MessageType.SEND_GROUP_MSG;
            LoginController.connection.sendMessage(new Message(type, payload));
            messageField.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  STREAMING
    // ═══════════════════════════════════════════════════════════════

    @FXML
    public void onGoLive() {
        if (!currentContextType.equals("GROUP")) {
            showAlert("Cannot go live", "You can only Go Live in a Group Channel.");
            return;
        }
        try {
            ObjectNode payload = JsonUtil.createObjectNode();
            payload.put("channelId", currentContextId);
            payload.put("groupId", currentContextId);
            LoginController.connection.sendMessage(new Message(MessageType.STREAM_START, payload));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleStreamStarted(JsonNode payload) {
        String ip          = payload.get("multicastIp").asText();
        int    port        = payload.get("multicastPort").asInt();
        String streamerName = payload.get("streamerName").asText();

        if (streamerName.equals(LoginController.currentUser.getUsername())) {
            if (currentStreamSender != null) currentStreamSender.stop();
            currentStreamSender = new UDPStreamSender(ip, port);
            currentStreamSender.start();
            chatBox.getChildren().add(buildTextMessage("System", "You started streaming!", new Date()));
        } else {
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
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(imageView);
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setTitle("Watching: " + streamerName);

        if (currentStreamReceiver != null) currentStreamReceiver.stop();
        currentStreamReceiver = new UDPStreamReceiver(ip, port);
        currentStreamReceiver.setOnFrameReceived(
            img -> Platform.runLater(() -> imageView.setImage(img)));
        currentStreamReceiver.start();
        stage.setOnCloseRequest(e -> currentStreamReceiver.stop());
        stage.show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.show();
    }
}
