package edu.vku.lancord.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.vku.lancord.client.udp.UDPStreamReceiver;
import edu.vku.lancord.client.udp.UDPStreamSender;
import edu.vku.lancord.client.udp.UDPHeader;
import edu.vku.lancord.client.media.*;
import edu.vku.lancord.client.ui.components.*;
import edu.vku.lancord.common.model.*;
import edu.vku.lancord.common.protocol.JsonUtil;
import edu.vku.lancord.common.protocol.Message;
import edu.vku.lancord.common.protocol.MessageType;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

public class MainController {

    // ── FXML: Sidebar lists ──────────────────────────────────────────────────
    @FXML private ListView<String> onlineUsersList;
    @FXML private ListView<String> groupsList;

    // ── FXML: User Profile Bar ───────────────────────────────────────────────
    @FXML private Label userProfileNameLabel;
    @FXML private Label userProfileStatusLabel;
    @FXML private Label userAvatarLabel;
    @FXML private javafx.scene.shape.Circle userAvatarCircle;

    // ── FXML: Layer panels ───────────────────────────────────────────────────
    @FXML private VBox welcomePanel;
    @FXML private VBox dmPanel;
    @FXML private VBox groupPanel;
    @FXML private VBox incomingCallOverlay;
    @FXML private Label incomingCallNameText;
    private int pendingCallerId = -1;

    // ── FXML: DM panel components ────────────────────────────────────────────
    @FXML private Label dmNameLabel;
    @FXML private Button btnVoiceCall;
    @FXML private Button btnVideoCall;
    @FXML private HBox dmCallBar;
    @FXML private Label dmCallStatusLabel;
    @FXML private ToggleButton btnMic;
    @FXML private ToggleButton btnCamera;
    @FXML private HBox dmVideoBar;
    @FXML private ImageView remoteVideoView;
    @FXML private ImageView localVideoPreview;
    @FXML private ScrollPane dmChatScroll;
    @FXML private VBox dmChatBox;
    @FXML private Button uploadButton;
    @FXML private TextField messageField;

    // ── FXML: Group panel components ─────────────────────────────────────────
    @FXML private Label groupNameLabel;
    @FXML private Button goLiveButton;
    @FXML private HBox groupLiveBar;
    @FXML private ToggleButton btnMicGroup;
    @FXML private ToggleButton btnCameraGroup;
    @FXML private ToggleButton btnScreen;
    @FXML private VBox groupVideoArea;
    @FXML private ImageView localVideoPreviewGroup;
    @FXML private FlowPane participantsPane;
    
    private final java.util.Map<Byte, ImageView> activeVideoFeeds = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Byte, String> currentStreamerNames = new java.util.concurrent.ConcurrentHashMap<>();
    @FXML private ScrollPane groupChatScroll;
    @FXML private VBox groupChatBox;
    @FXML private Button uploadButtonGroup;
    @FXML private TextField messageFieldGroup;

    // ── Observable lists ──────────────────────────────────────────────────────
    private final ObservableList<String> usersObservable  = FXCollections.observableArrayList();
    private final ObservableList<String> groupsObservable = FXCollections.observableArrayList();

    // ── State ──────────────────────────────────────────────────────────────────
    private List<User> currentOnlineUsers;
    private int    currentContextId   = -1;
    private String currentContextType = "";  // "DM" or "GROUP"
    private boolean inCall = false;
    
    // ── Active Call Tracking ───────────────────────────────────────────────────
    private String activeCallType = null;
    private int    activeCallId   = -1;

    // ── Streaming ────────────────────────────────────────────────────────────
    private UDPStreamSender   currentStreamSender;
    private UDPStreamReceiver currentStreamReceiver;
    private AudioCaptureThread    audioCapture;
    private AudioPlaybackRenderer audioPlayback;
    private WebcamCaptureThread   webcamCapture;
    private ScreenCaptureThread   screenCapture;

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final String COMP_BASE  = "/edu/vku/lancord/client/ui/components/";
    private static final int    CHUNK_SIZE = 512 * 1024;
    private static final Set<String> IMAGE_EXT = Set.of("png","jpg","jpeg","gif","bmp","webp");
    private static final Set<String> VIDEO_EXT = Set.of("mp4","avi","mkv","mov","wmv","flv","webm");
    private static final Set<String> AUDIO_EXT = Set.of("mp3","wav","m4a","aac","ogg","flac");

    // ── Pending upload tracking ────────────────────────────────────────────────
    private final Map<Integer, byte[]>  pendingUploads    = new HashMap<>();
    private final Map<Integer, String>  pendingUploadPaths = new HashMap<>();
    private final Map<Integer, String>  localUploadPaths  = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════
    //  INIT
    // ═══════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        loadLocalUploadPaths();

        // Wire sidebar lists
        onlineUsersList.setItems(usersObservable);
        groupsList.setItems(groupsObservable);
        setupListCellFactory(onlineUsersList, true);
        setupListCellFactory(groupsList, false);

        // Placeholder groups (will be replaced by real group management)
        groupsObservable.add("General Channel");

        // Set user profile bar
        if (LoginController.currentUser != null) {
            String username = LoginController.currentUser.getUsername();
            if (userProfileNameLabel != null) userProfileNameLabel.setText(username);
            if (userAvatarLabel != null) {
                userAvatarLabel.setText(username.substring(0, 1).toUpperCase());
            }
            // Color avatar by username hashcode
            if (userAvatarCircle != null) {
                String[] colors = {"#5865F2", "#23A559", "#E91E63", "#FF9800", "#9C27B0", "#00BCD4"};
                int colorIdx = Math.abs(username.hashCode() % colors.length);
                userAvatarCircle.setFill(javafx.scene.paint.Color.web(colors[colorIdx]));
            }
        }

        // Register message handler
        LoginController.connection.setOnMessageReceived(this::handleMessage);

        // Drain messages that arrived before this controller was ready
        Message pending;
        while ((pending = LoginController.pendingMessages.poll()) != null) {
            handleMessage(pending);
        }

        // Sidebar click handlers
        onlineUsersList.setOnMouseClicked(e -> {
            int idx = onlineUsersList.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && currentOnlineUsers != null && idx < currentOnlineUsers.size()) {
                User u = currentOnlineUsers.get(idx);
                if (u.getId() != LoginController.currentUser.getId()) {
                    openDM(u);
                }
            }
        });

        groupsList.setOnMouseClicked(e -> {
            int idx = groupsList.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                openGroup(idx + 1, groupsObservable.get(idx));
            }
        });

        // Auto-scroll chat to bottom
        dmChatBox.heightProperty().addListener((obs, o, n)    -> dmChatScroll.setVvalue(1.0));
        groupChatBox.heightProperty().addListener((obs, o, n) -> groupChatScroll.setVvalue(1.0));

        showPanel(welcomePanel);
    }

    // ═══════════════════════════════════════════════════════════════
    //  PANEL SWITCHING – only one panel visible at a time
    // ═══════════════════════════════════════════════════════════════

    private void showPanel(VBox target) {
        for (VBox p : new VBox[]{welcomePanel, dmPanel, groupPanel}) {
            boolean show = p == target;
            p.setVisible(show);
            p.setManaged(show);
        }
        if (target != welcomePanel) {
            FadeTransition ft = new FadeTransition(Duration.millis(180), target);
            ft.setFromValue(0); ft.setToValue(1); ft.play();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONTEXT OPENERS
    // ═══════════════════════════════════════════════════════════════

    private void openDM(User target) {
        currentContextType = "DM";
        currentContextId   = target.getId();
        syncVideoUIVisibility();
        dmNameLabel.setText(target.getUsername());
        dmChatBox.getChildren().clear();
        showPanel(dmPanel);

        // Fetch history
        try {
            ObjectNode p = JsonUtil.createObjectNode();
            p.put("type", "DM");
            p.put("contextId", target.getId());
            LoginController.connection.sendMessage(new Message(MessageType.GET_CHAT_HISTORY, p));
        } catch (Exception e) { e.printStackTrace(); }

        try {
            ObjectNode p = JsonUtil.createObjectNode();
            p.put("contextType", "DM");
            p.put("contextId", target.getId());
            LoginController.connection.sendMessage(new Message(MessageType.GET_FILES_IN_CONTEXT, p));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void openGroup(int groupId, String groupName) {
        currentContextType = "GROUP";
        currentContextId   = groupId;
        syncVideoUIVisibility();
        groupNameLabel.setText(groupName);
        groupChatBox.getChildren().clear();
        showPanel(groupPanel);

        try {
            ObjectNode p = JsonUtil.createObjectNode();
            p.put("type", "GROUP");
            p.put("contextId", groupId);
            LoginController.connection.sendMessage(new Message(MessageType.GET_CHAT_HISTORY, p));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void syncVideoUIVisibility() {
        boolean isLocalCapturing = (webcamCapture != null) || (screenCapture != null);
        boolean isRemoteDMVisible = (remoteVideoView.getImage() != null);
        boolean isRemoteGroupVisible = !activeVideoFeeds.isEmpty();

        if (activeCallType == null) {
            setNodeVisible(dmVideoBar, false);
            setNodeVisible(groupVideoArea, false);
        } else if (activeCallType.equals("DM") && activeCallId == currentContextId && currentContextType.equals("DM")) {
            // Show video bar if local camera is actively capturing OR we are receiving a remote video frame
            setNodeVisible(dmVideoBar, isLocalCapturing || isRemoteDMVisible);
        } else if (activeCallType.equals("GROUP") && activeCallId == currentContextId && currentContextType.equals("GROUP")) {
            // Video area shown if cam or screen is active OR we are watching others
            setNodeVisible(groupVideoArea, isLocalCapturing || isRemoteGroupVisible);
        } else {
            setNodeVisible(dmVideoBar, false);
            setNodeVisible(groupVideoArea, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  LIST CELL FACTORY
    // ═══════════════════════════════════════════════════════════════

    private void setupListCellFactory(ListView<String> listView, boolean showStatusDot) {
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }

                // Root row container
                HBox row = new HBox(10);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.setPadding(new javafx.geometry.Insets(4, 8, 4, 8));

                // Avatar + status dot overlay (StackPane)
                StackPane avatarStack = new StackPane();
                avatarStack.setMinSize(36, 36);
                avatarStack.setMaxSize(36, 36);

                // Avatar circle (colored by name hash)
                String cleanName = item.replace(" (You)", "");
                String[] colors = {"#5865F2","#23A559","#E91E63","#FF9800","#9C27B0","#00BCD4","#FAA61A"};
                int ci = Math.abs(cleanName.hashCode()) % colors.length;
                Circle avatarCircle = new Circle(18, javafx.scene.paint.Color.web(colors[ci]));

                // Initial letter
                Label initial = new Label(cleanName.substring(0, 1).toUpperCase());
                initial.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14;");

                avatarStack.getChildren().addAll(avatarCircle, initial);

                if (showStatusDot) {
                    // Online status dot
                    boolean isOnline = isUserOnline(cleanName);
                    Circle statusDot = new Circle(6);
                    statusDot.setFill(isOnline
                        ? javafx.scene.paint.Color.web("#23A559")
                        : javafx.scene.paint.Color.web("#80848E"));
                    statusDot.setStroke(javafx.scene.paint.Color.web("#2B2D31"));
                    statusDot.setStrokeWidth(2);
                    StackPane.setAlignment(statusDot, javafx.geometry.Pos.BOTTOM_RIGHT);
                    avatarStack.getChildren().add(statusDot);
                }

                // Name label
                Label nameLabel = new Label(item);
                nameLabel.setTextFill(javafx.scene.paint.Color.web("#DBDEE1"));
                nameLabel.setStyle("-fx-font-size: 14px;");

                row.getChildren().addAll(avatarStack, nameLabel);
                setGraphic(row);
            }
        });
    }

    private boolean isUserOnline(String username) {
        if (currentOnlineUsers == null) return false;
        return currentOnlineUsers.stream()
            .anyMatch(u -> u != null && u.getUsername().equals(username));
    }

    // ═══════════════════════════════════════════════════════════════
    //  MESSAGE ROUTING (incoming from server)
    // ═══════════════════════════════════════════════════════════════

    private void handleMessage(Message msg) {
        Platform.runLater(() -> {
            try {
                switch (msg.getType()) {
                    case ONLINE_USERS_UPDATE  -> updateOnlineUsers(msg.getPayload());
                    case NEW_MESSAGE_NOTIFY   -> receiveChatMessage(msg.getPayload());
                    case CHAT_HISTORY_RESP    -> handleChatHistory(msg.getPayload());
                    case FILES_IN_CONTEXT_RESP -> handleFilesInContext(msg.getPayload());
                    case UPLOAD_FILE_RESP     -> handleUploadResp(msg.getPayload());
                    case FILE_UPLOAD_COMPLETE, FILE_UPLOAD_NOTIFY ->
                        handleFileUploadComplete(msg.getPayload(),
                            msg.getType() == MessageType.FILE_UPLOAD_COMPLETE);
                    case DOWNLOAD_FILE_RESP   -> handleDownloadResp(msg.getPayload());
                    case STREAM_STARTED       -> handleStreamStarted(msg.getPayload());
                    case STREAM_STOPPED       -> handleStreamStopped(msg.getPayload());
                    case CALL_INCOMING        -> handleCallIncoming(msg.getPayload());
                    case CALL_REJECTED        -> handleCallRejected(msg.getPayload());
                }
            } catch (Exception e) { e.printStackTrace(); }
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
    //  CHAT HISTORY
    // ═══════════════════════════════════════════════════════════════

    private void handleChatHistory(JsonNode payload) throws Exception {
        String type      = payload.get("type").asText();
        int    contextId = payload.get("contextId").asInt();
        if (!currentContextType.equalsIgnoreCase(type) || currentContextId != contextId) return;

        List<ChatMessage> history = JsonUtil.treeToValue(
            payload.get("messages"), new TypeReference<List<ChatMessage>>() {});
        VBox chatBox = activeChatBox();
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
    //  FILES IN CONTEXT
    // ═══════════════════════════════════════════════════════════════

    private void handleFilesInContext(JsonNode payload) throws Exception {
        String contextType = payload.get("contextType").asText();
        int    contextId   = payload.get("contextId").asInt();
        if (!currentContextType.equalsIgnoreCase(contextType) || currentContextId != contextId) return;

        List<FileMetadata> files = JsonUtil.treeToValue(
            payload.get("files"), new TypeReference<List<FileMetadata>>() {});
        if (files == null) return;
        VBox chatBox = activeChatBox();
        for (FileMetadata meta : files) {
            String sender = meta.getUploaderName() != null ? meta.getUploaderName() : "Unknown";
            Date   ts     = meta.getCreatedAt() != null ? new Date(meta.getCreatedAt().getTime()) : null;
            chatBox.getChildren().add(buildFileMessage(sender, meta, ts));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  INCOMING REAL-TIME CHAT MESSAGE
    // ═══════════════════════════════════════════════════════════════

    private void receiveChatMessage(JsonNode payload) throws Exception {
        ChatMessage msg = JsonUtil.treeToValue(payload, ChatMessage.class);
        boolean shouldDisplay = false;
        int myId = LoginController.currentUser.getId();

        if ("DM".equalsIgnoreCase(msg.getType())) {
            shouldDisplay = currentContextType.equals("DM") &&
                ((msg.getSenderId() == currentContextId) ||
                 (msg.getSenderId() == myId && msg.getReceiverId() == currentContextId));
        } else {
            shouldDisplay = currentContextType.equals("GROUP") &&
                msg.getReceiverId() == currentContextId;
        }
        if (!shouldDisplay) return;

        String sender = msg.getSenderName() != null ? msg.getSenderName() : "Unknown";
        Date   ts     = msg.getCreatedAt() != null ? new Date(msg.getCreatedAt().getTime()) : new Date();
        activeChatBox().getChildren().add(buildTextMessage(sender, msg.getContent(), ts));

        Platform.runLater(() -> {
            if (currentContextType.equals("DM")) dmChatScroll.setVvalue(1.0);
            else groupChatScroll.setVvalue(1.0);
        });
    }

    /** Returns the currently active chat VBox based on context */
    private VBox activeChatBox() {
        return currentContextType.equals("GROUP") ? groupChatBox : dmChatBox;
    }

    /** Returns the currently active message TextField */
    private TextField activeMessageField() {
        return currentContextType.equals("GROUP") ? messageFieldGroup : messageField;
    }

    // ═══════════════════════════════════════════════════════════════
    //  SEND TEXT MESSAGE
    // ═══════════════════════════════════════════════════════════════

    @FXML
    public void onSendMessage() {
        TextField tf = activeMessageField();
        String text = tf.getText().trim();
        if (text.isEmpty() || currentContextId == -1) return;

        try {
            ObjectNode payload = JsonUtil.createObjectNode();
            payload.put("receiverId", currentContextId);
            payload.put("content", text);
            MessageType type = currentContextType.equals("DM")
                ? MessageType.SEND_DM : MessageType.SEND_GROUP_MSG;
            LoginController.connection.sendMessage(new Message(type, payload));
            tf.clear();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  FILE UPLOAD
    // ═══════════════════════════════════════════════════════════════

    @FXML
    public void onUploadFile() {
        if (currentContextId == -1) {
            showAlert("No conversation selected", "Please open a DM or channel first.");
            return;
        }
        // Use the button from whichever panel is active as owner
        Node ownerNode = currentContextType.equals("GROUP") ? uploadButtonGroup : uploadButton;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file to send");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showOpenDialog(ownerNode.getScene().getWindow());
        if (file == null) return;

        if (file.length() > 50L * 1024 * 1024) {
            showAlert("File too large", "Maximum file size is 50 MB.");
            return;
        }

        new Thread(() -> {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                ObjectNode initPayload = JsonUtil.createObjectNode();
                initPayload.put("filename", file.getName());
                initPayload.put("size", file.length());
                initPayload.put("contextType", currentContextType);
                initPayload.put("contextId", currentContextId);
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

    private void handleUploadResp(JsonNode payload) throws Exception {
        FileMetadata meta = JsonUtil.treeToValue(payload, FileMetadata.class);
        if (meta == null) return;
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
                chunkPayload.put("chunk", chunk);
                LoginController.connection.sendMessage(new Message(MessageType.FILE_CHUNK, chunkPayload));
                offset = end;
                Thread.sleep(5);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleFileUploadComplete(JsonNode payload, boolean isMyUpload) throws Exception {
        FileMetadata meta = JsonUtil.treeToValue(payload, FileMetadata.class);
        if (meta == null) return;
        boolean relevant = false;
        if ("DM".equalsIgnoreCase(meta.getContextType())) {
            int myId = LoginController.currentUser.getId();
            relevant = currentContextType.equals("DM") &&
                ((meta.getUploaderId() == currentContextId && meta.getContextId() == myId) ||
                 (meta.getUploaderId() == myId && meta.getContextId() == currentContextId));
        } else {
            relevant = currentContextType.equalsIgnoreCase(meta.getContextType())
                    && currentContextId == meta.getContextId();
        }
        if (!relevant) return;
        String sender = meta.getUploaderName() != null ? meta.getUploaderName()
                      : LoginController.currentUser.getUsername();
        Date ts = meta.getCreatedAt() != null ? new Date(meta.getCreatedAt().getTime()) : new Date();
        activeChatBox().getChildren().add(buildFileMessage(sender, meta, ts));
    }

    // ═══════════════════════════════════════════════════════════════
    //  DOWNLOAD
    // ═══════════════════════════════════════════════════════════════

    private void handleDownloadResp(JsonNode payload) throws Exception {
        int fileId = payload.get("id").asInt();
        byte[] data = payload.get("data").binaryValue();
        Consumer<byte[]> imgCallback = ImageDownloadRegistry.consume(fileId);
        if (imgCallback != null) { imgCallback.accept(data); return; }
        Consumer<byte[]> fileCallback = FileDownloadRegistry.consume(fileId);
        if (fileCallback != null) { fileCallback.accept(data); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  STREAMING – DM Voice / Video Call
    // ═══════════════════════════════════════════════════════════════

    @FXML
    public void onStartVoiceCall() {
        // DM voice call: request stream session from server (audio only)
        requestDMStream(false);
    }

    @FXML
    public void onStartVideoCall() {
        // DM video call: request stream session from server (audio + video)
        requestDMStream(true);
    }

    private void requestDMStream(boolean withVideo) {
        if (currentContextType.equals("DM") && currentContextId != -1) {
            try {
                ObjectNode payload = JsonUtil.createObjectNode();
                payload.put("receiverId", currentContextId);
                payload.put("withVideo", withVideo);
                LoginController.connection.sendMessage(new Message(MessageType.CALL_REQUEST, payload));
                
                activeChatBox().getChildren().add(
                    buildTextMessage("System", "Ringing...", new Date()));
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void handleCallIncoming(JsonNode payload) {
        int callerId = payload.get("callerId").asInt();
        String callerName = payload.get("callerName").asText();
        
        pendingCallerId = callerId;
        incomingCallNameText.setText("Cuộc gọi đến từ " + callerName);
        
        incomingCallOverlay.setVisible(true);
        incomingCallOverlay.setManaged(true);
        
        // Add fade in
        FadeTransition ft = new FadeTransition(Duration.millis(200), incomingCallOverlay);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    @FXML
    public void onAcceptCall() {
        if (pendingCallerId == -1) return;
        try {
            ObjectNode resp = JsonUtil.createObjectNode();
            resp.put("callerId", pendingCallerId);
            
            if (!currentContextType.equals("DM") || currentContextId != pendingCallerId) {
                currentContextType = "DM";
                currentContextId = pendingCallerId;
                showPanel(dmPanel);
            }
            LoginController.connection.sendMessage(new Message(MessageType.CALL_ACCEPT, resp));
            
            hideCallOverlay();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    public void onRejectCall() {
        if (pendingCallerId == -1) return;
        try {
            ObjectNode resp = JsonUtil.createObjectNode();
            resp.put("callerId", pendingCallerId);
            LoginController.connection.sendMessage(new Message(MessageType.CALL_REJECT, resp));
            
            hideCallOverlay();
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    private void hideCallOverlay() {
        FadeTransition ft = new FadeTransition(Duration.millis(150), incomingCallOverlay);
        ft.setFromValue(1); ft.setToValue(0);
        ft.setOnFinished(e -> {
            incomingCallOverlay.setVisible(false);
            incomingCallOverlay.setManaged(false);
            pendingCallerId = -1;
        });
        ft.play();
    }

    private void handleCallRejected(JsonNode payload) {
        String receiverName = payload.get("receiverName").asText();
        activeChatBox().getChildren().add(
            buildTextMessage("System", "☎ " + receiverName + " đang bận hoặc từ chối cuộc gọi.", new Date()));
    }
    // ═══════════════════════════════════════════════════════════════
    //  STREAMING – Group Go Live
    // ═══════════════════════════════════════════════════════════════

    @FXML
    public void onGoLive() {
        if (!currentContextType.equals("GROUP")) return;
        try {
            ObjectNode payload = JsonUtil.createObjectNode();
            payload.put("channelId", currentContextId);
            payload.put("groupId", currentContextId);
            LoginController.connection.sendMessage(new Message(MessageType.STREAM_START, payload));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Server confirms stream session ──────────────────────────────────────
    private void handleStreamStarted(JsonNode payload) {
        String ip     = payload.get("multicastIp").asText();
        int    port   = payload.get("multicastPort").asInt();
        byte senderId = (byte) payload.get("senderId").asInt();
        int streamUserId = payload.has("userId") ? payload.get("userId").asInt() : -1;
        
        int groupId = payload.has("groupId") ? payload.get("groupId").asInt() : 0;
        int channelId = payload.has("channelId") ? payload.get("channelId").asInt() : 0;
        String intendedCallType = (groupId == 0) ? "DM" : "GROUP";
        int intendedCallId = (groupId == 0) ? channelId : groupId;

        if (inCall) {
            if (currentStreamSender != null && currentStreamSender.getIp().equals(ip)) {
                // Already in this group's call, update names and do not interrupt!
                if (payload.has("streamerNames")) {
                    currentStreamerNames.clear();
                    payload.get("streamerNames").fields().forEachRemaining(entry -> {
                        currentStreamerNames.put(Byte.parseByte(entry.getKey()), entry.getValue().asText());
                    });
                }
                if (streamUserId == LoginController.currentUser.getId()) {
                    currentStreamSender.setSenderId(senderId);
                    Platform.runLater(() -> {
                        activeChatBox().getChildren().add(buildTextMessage("System", "🔴 You went Live!", new Date()));
                    });
                }
                syncVideoUIVisibility();
            }
            return;
        }

        if (payload.has("streamerNames")) {
            currentStreamerNames.clear();
            payload.get("streamerNames").fields().forEachRemaining(entry -> {
                currentStreamerNames.put(Byte.parseByte(entry.getKey()), entry.getValue().asText());
            });
        }

        stopAllMedia();
        try {
            audioPlayback = new AudioPlaybackRenderer();
            byte initialSenderId = (streamUserId == LoginController.currentUser.getId()) ? senderId : 0;
            currentStreamSender   = new UDPStreamSender(ip, port, initialSenderId);
            currentStreamReceiver = new UDPStreamReceiver(ip, port, this::onMediaReceived);
            currentStreamReceiver.start();
            inCall = true;
            activeCallType = intendedCallType;
            activeCallId   = intendedCallId;

            // Show the appropriate call UI layer if the user is currently viewing that context
            if (currentContextType.equals(intendedCallType) && currentContextId == intendedCallId) {
                if (intendedCallType.equals("DM")) {
                    showDMCallBar(true);
                    Platform.runLater(() -> {
                        if (dmCallStatusLabel != null) {
                            dmCallStatusLabel.setText("In call with " + dmNameLabel.getText());
                        }
                        activeChatBox().getChildren().add(
                            buildTextMessage("System", "📞 Call connected with " + dmNameLabel.getText() + "!", new Date()));
                    });
                } else {
                    showGroupLiveBar(true);
                    if (initialSenderId != 0) {
                        activeChatBox().getChildren().add(
                            buildTextMessage("System", "🔴 You went Live!", new Date()));
                    } else {
                        String streamer = currentStreamerNames.getOrDefault(senderId, "Someone");
                        activeChatBox().getChildren().add(
                            buildTextMessage("System", "🔴 " + streamer + " started a live stream!", new Date()));
                    }
                }
            } else {
                // Not in the same context, notify them
                String contextName = intendedCallType.equals("DM") ? "a user" : "a group";
                activeChatBox().getChildren().add(
                        buildTextMessage("System", "🔴 A live stream started in " + contextName + ".", new Date()));
            }
            syncVideoUIVisibility();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Streaming Error", "Failed to start streaming: " + e.getMessage());
        }
    }

    private void handleStreamStopped(JsonNode payload) {
        byte senderId = (byte) payload.get("senderId").asInt();
        
        // Remove from map and grid
        currentStreamerNames.remove(senderId);
        ImageView iv = activeVideoFeeds.remove(senderId);
        
        if (iv != null) {
            // Find its parent StackPane and remove it
            Platform.runLater(() -> {
                participantsPane.getChildren().removeIf(node -> 
                    node instanceof javafx.scene.layout.StackPane && 
                    ((javafx.scene.layout.StackPane) node).getChildren().contains(iv)
                );
            });
        }

        Platform.runLater(() -> {
            if ("DM".equals(activeCallType)) {
                remoteVideoView.setImage(null);
                onEndCall();
            } else if ("GROUP".equals(activeCallType)) {
                if (activeVideoFeeds.isEmpty() && currentStreamSender == null) {
                    onEndCall();
                }
            }
            syncVideoUIVisibility();
        });
    }

    // ── Media received from UDP multicast ──────────────────────────────────
    private void onMediaReceived(byte senderId, byte mediaType, byte[] fullData) {
        switch (mediaType) {
            case UDPHeader.MEDIA_AUDIO -> {
                if (audioPlayback != null) audioPlayback.enqueue(fullData);
            }
            case UDPHeader.MEDIA_WEBCAM, UDPHeader.MEDIA_SCREEN -> {
                try {
                    java.awt.image.BufferedImage bImg =
                        javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(fullData));
                    if (bImg != null) {
                        javafx.scene.image.WritableImage fxImg =
                            javafx.embed.swing.SwingFXUtils.toFXImage(bImg, null);
                        Platform.runLater(() -> {
                            if ("DM".equals(activeCallType)) {
                                remoteVideoView.setImage(fxImg);
                            } else if ("GROUP".equals(activeCallType)) {
                                // Group Mode Grid Logic
                                ImageView participantView = activeVideoFeeds.computeIfAbsent(senderId, id -> {
                                    ImageView iv = new ImageView();
                                    iv.setFitWidth(320);
                                    iv.setFitHeight(180);
                                    iv.setPreserveRatio(true);
                                    
                                    String name = currentStreamerNames.getOrDefault(id, "User " + id);
                                    Label nameLbl = new Label(name);
                                    nameLbl.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-padding: 2 6; -fx-font-size: 12px;");
                                    
                                    javafx.scene.layout.StackPane sp = new javafx.scene.layout.StackPane(iv, nameLbl);
                                    javafx.scene.layout.StackPane.setAlignment(nameLbl, javafx.geometry.Pos.BOTTOM_LEFT);
                                    sp.setStyle("-fx-border-color: #5865F2; -fx-border-width: 2; -fx-border-radius: 4;");
                                    
                                    participantsPane.getChildren().add(sp);
                                    return iv;
                                });
                                participantView.setImage(fxImg);
                            }
                            syncVideoUIVisibility();
                        });
                    }
                } catch (java.io.IOException e) { e.printStackTrace(); }
            }
        }
    }

    // ── Show/hide DM call bar ───────────────────────────────────────────────
    private void showDMCallBar(boolean show) {
        if (show) {
            dmCallBar.setVisible(true);
            dmCallBar.setManaged(true);
            dmCallBar.setTranslateY(-40);
            TranslateTransition tt = new TranslateTransition(Duration.millis(200), dmCallBar);
            tt.setToY(0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), dmCallBar);
            ft.setFromValue(0); ft.setToValue(1);
            new ParallelTransition(tt, ft).play();
        } else {
            setNodeVisible(dmCallBar, false);
            setNodeVisible(dmVideoBar, false);
        }
    }

    // ── Show/hide Group live bar ────────────────────────────────────────────
    private void showGroupLiveBar(boolean show) {
        if (show) {
            groupLiveBar.setVisible(true);
            groupLiveBar.setManaged(true);
            groupLiveBar.setTranslateY(-40);
            TranslateTransition tt = new TranslateTransition(Duration.millis(200), groupLiveBar);
            tt.setToY(0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), groupLiveBar);
            ft.setFromValue(0); ft.setToValue(1);
            new ParallelTransition(tt, ft).play();
        } else {
            setNodeVisible(groupLiveBar, false);
            setNodeVisible(groupVideoArea, false);
        }
    }

    // ── End call (shared by DM and Group) ──────────────────────────────────
    @FXML
    public void onEndCall() {
        if (inCall && activeCallType != null) {
            byte mySenderId = currentStreamSender != null ? currentStreamSender.getSenderId() : -1;
            // Only notify the server to stop stream if we actually went live (senderId != 0)
            if (mySenderId != 0) {
                try {
                    ObjectNode payload = JsonUtil.createObjectNode();
                    payload.put("contextType", activeCallType);
                    payload.put("channelId", activeCallId);
                    payload.put("groupId", activeCallId);
                    payload.put("senderId", mySenderId); 
                    LoginController.connection.sendMessage(new Message(MessageType.STREAM_STOP, payload));
                } catch (Exception e) { e.printStackTrace(); }
            }
        }

        stopAllMedia();
        endCallUI();
        activeChatBox().getChildren().add(
            buildTextMessage("System", "📵 Call ended.", new Date()));
    }

    private void endCallUI() {
        inCall = false;
        activeCallType = null;
        activeCallId = -1;
        showDMCallBar(false);
        showGroupLiveBar(false);
        if (btnMic != null)       { btnMic.setSelected(false); }
        if (btnCamera != null)    { btnCamera.setSelected(false); }
        if (btnMicGroup != null)  { btnMicGroup.setSelected(false); }
        if (btnCameraGroup != null) { btnCameraGroup.setSelected(false); }
        if (btnScreen != null)    { btnScreen.setSelected(false); }
        
        activeVideoFeeds.clear();
        if (participantsPane != null) { participantsPane.getChildren().clear(); }
        if (remoteVideoView != null) { remoteVideoView.setImage(null); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MEDIA TOGGLE HANDLERS
    // ═══════════════════════════════════════════════════════════════

    @FXML
    public void onBtnMicAction() {
        // Works for both DM (btnMic) and Group (btnMicGroup) — same action source
        boolean selected = (currentContextType.equals("DM") ? btnMic : btnMicGroup).isSelected();
        if (selected) {
            if (currentStreamSender != null) {
                audioCapture = new AudioCaptureThread(currentStreamSender);
                audioCapture.start();
            }
        } else {
            if (audioCapture != null) { audioCapture.stopCapture(); audioCapture = null; }
        }
    }

    @FXML
    public void onBtnCameraAction() {
        boolean selected = (currentContextType.equals("DM") ? btnCamera : btnCameraGroup).isSelected();
        if (selected) {
            // Turn off Screen if it's running
            if (btnScreen.isSelected()) {
                btnScreen.setSelected(false);
                if (screenCapture != null) { screenCapture.stopCapture(); screenCapture = null; }
            }
            if (currentStreamSender != null) {
                webcamCapture = new WebcamCaptureThread(currentStreamSender);
                webcamCapture.start();
            }
        } else {
            if (webcamCapture != null) { webcamCapture.stopCapture(); webcamCapture = null; }
        }
        syncVideoUIVisibility();
    }

    @FXML
    public void onBtnScreenAction() {
        if (btnScreen.isSelected()) {
            // Show picker dialog
            Alert picker = new Alert(Alert.AlertType.CONFIRMATION);
            picker.setTitle("Chọn nguồn chia sẻ");
            picker.setHeaderText("Chia sẻ màn hình");
            ButtonType btnFullScreen = new ButtonType("🖥 Toàn màn hình");
            ButtonType btnCancel = ButtonType.CANCEL;
            picker.getButtonTypes().setAll(btnFullScreen, btnCancel);

            picker.showAndWait().ifPresent(choice -> {
                if (choice == btnFullScreen && currentStreamSender != null) {
                    // Turn off Camera if it's running
                    boolean camSelected = (currentContextType.equals("DM") ? btnCamera : btnCameraGroup).isSelected();
                    if (camSelected) {
                        if (currentContextType.equals("DM")) btnCamera.setSelected(false);
                        else btnCameraGroup.setSelected(false);
                        if (webcamCapture != null) { webcamCapture.stopCapture(); webcamCapture = null; }
                    }

                    try {
                        screenCapture = new ScreenCaptureThread(currentStreamSender);
                        screenCapture.start();
                    } catch (java.awt.AWTException e) {
                        e.printStackTrace();
                        btnScreen.setSelected(false);
                    }
                } else {
                    btnScreen.setSelected(false);
                }
                syncVideoUIVisibility();
            });
        } else {
            if (screenCapture != null) { screenCapture.stopCapture(); screenCapture = null; }
            syncVideoUIVisibility();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  STOP ALL MEDIA
    // ═══════════════════════════════════════════════════════════════

    private void stopAllMedia() {
        if (audioCapture  != null) { audioCapture.stopCapture();  audioCapture  = null; }
        if (webcamCapture != null) { webcamCapture.stopCapture(); webcamCapture = null; }
        if (screenCapture != null) { screenCapture.stopCapture(); screenCapture = null; }
        if (audioPlayback != null) { audioPlayback.close();       audioPlayback = null; }
        if (currentStreamSender   != null) { currentStreamSender.close();   currentStreamSender   = null; }
        if (currentStreamReceiver != null) { currentStreamReceiver.stop();  currentStreamReceiver = null; }
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
        String ext       = getExtension(meta.getOriginalName()).toLowerCase();
        String localPath = localUploadPaths.get(meta.getId());
        if (IMAGE_EXT.contains(ext)) return buildImageMessage(sender, meta, timestamp, localPath);
        if (VIDEO_EXT.contains(ext)) return buildVideoMessage(sender, meta, timestamp, localPath);
        if (AUDIO_EXT.contains(ext)) return buildAudioMessage(sender, meta, timestamp, localPath);
        return buildGenericFileMessage(sender, meta, timestamp, localPath);
    }

    private Node buildImageMessage(String sender, FileMetadata meta, Date ts, String localPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(COMP_BASE + "ChatImageMessage.fxml"));
            Node node = loader.load();
            ((ChatImageMessageController) loader.getController()).setDataAndDownload(sender, meta, ts, localPath);
            return node;
        } catch (Exception e) { return buildGenericFileMessage(sender, meta, ts, localPath); }
    }

    private Node buildGenericFileMessage(String sender, FileMetadata meta, Date ts, String localPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(COMP_BASE + "ChatFileMessage.fxml"));
            Node node = loader.load();
            ((ChatFileMessageController) loader.getController()).setData(sender, meta, ts, false, localPath);
            return node;
        } catch (Exception e) {
            Label fallback = new Label(sender + " sent: " + meta.getOriginalName());
            fallback.setStyle("-fx-text-fill: #DBDEE1; -fx-padding: 4 16;");
            return fallback;
        }
    }

    private Node buildVideoMessage(String sender, FileMetadata meta, Date ts, String localPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(COMP_BASE + "ChatVideoMessage.fxml"));
            Node node = loader.load();
            ((ChatFileMessageController) loader.getController()).setData(sender, meta, ts, true, localPath);
            return node;
        } catch (Exception e) { return buildGenericFileMessage(sender, meta, ts, localPath); }
    }

    private Node buildAudioMessage(String sender, FileMetadata meta, Date ts, String localPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(COMP_BASE + "ChatAudioMessage.fxml"));
            Node node = loader.load();
            ((ChatAudioMessageController) loader.getController()).setData(sender, meta, ts, localPath);
            return node;
        } catch (Exception e) { return buildGenericFileMessage(sender, meta, ts, localPath); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════

    private static void setNodeVisible(Node node, boolean show) {
        if (node == null) return;
        node.setVisible(show);
        node.setManaged(show);
    }

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

    @FXML
    public void onOpenSettings() {
        showAlert("Settings", "Settings panel coming soon.");
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
        } catch (Exception e) { System.err.println("Failed to load local upload paths: " + e.getMessage()); }
    }

    private void saveLocalUploadPaths() {
        try {
            Path dir = Paths.get("client_downloads");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Properties props = new Properties();
            for (Map.Entry<Integer, String> entry : localUploadPaths.entrySet()) {
                props.setProperty(String.valueOf(entry.getKey()), entry.getValue());
            }
            try (FileOutputStream fos = new FileOutputStream("client_downloads/upload_cache.properties")) {
                props.store(fos, "Local paths for uploaded files");
            }
        } catch (Exception e) { System.err.println("Failed to save local upload paths: " + e.getMessage()); }
    }
}
