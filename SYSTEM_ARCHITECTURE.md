# LANCord — Phân Tích Kiến Trúc Hệ Thống (Dành cho Thuyết Trình)

> **Mục đích:** Tài liệu này phân tích toàn diện kiến trúc, luồng hoạt động và cơ chế kỹ thuật của LANCord ở mức độ Class/Method, phục vụ cho buổi thuyết trình và hỏi đáp chuyên sâu.

---

## 1. Cấu Trúc Module Tổng Quan

```
LANCord/
├── common/                          ← Dùng chung cả Client và Server
│   ├── protocol/
│   │   ├── MessageType.java         ← Enum 28 loại message (giao thức)
│   │   ├── Message.java             ← Đơn vị dữ liệu truyền TCP (type + JsonNode)
│   │   └── JsonUtil.java            ← Serializer/Deserializer Jackson
│   └── model/
│       ├── User.java
│       ├── ChatMessage.java
│       ├── FileMetadata.java
│       ├── Group.java
│       └── Channel.java
│
├── server/                          ← Chạy riêng trên máy chủ
│   ├── ServerMain.java              ← Entry point, ThreadPool(50), ServerSocket(:8888)
│   ├── core/
│   │   ├── ClientHandler.java       ← 1 thread / 1 Client, xử lý mọi lệnh TCP
│   │   └── ServerManager.java       ← Registry: Online users, Multicast IP, Streamers
│   └── db/
│       ├── DatabaseManager.java     ← Kết nối MySQL (singleton)
│       ├── UserRepo.java
│       ├── MessageRepo.java
│       ├── GroupRepo.java
│       └── FileRepo.java
│
└── client/                          ← Giao diện JavaFX phía người dùng
    ├── network/
    │   └── TCPConnection.java       ← Quản lý 1 Socket TCP với Server
    ├── udp/
    │   ├── UDPHeader.java           ← Custom 12-byte header (giao thức tự xây dựng)
    │   ├── UDPStreamSender.java     ← Phân mảnh & gửi qua MulticastSocket
    │   ├── UDPStreamReceiver.java   ← Nhận, phân loại, gom mảnh
    │   ├── FragmentBuffer.java      ← Bộ đệm gom mảnh theo (senderId, seqId)
    │   └── MediaDispatcher.java     ← @FunctionalInterface callback khi frame đủ mảnh
    ├── media/
    │   ├── AudioCaptureThread.java  ← Thu âm PCM 16kHz qua javax.sound.TargetDataLine
    │   ├── WebcamCaptureThread.java ← Chụp JPEG webcam qua sarxos/webcam-capture
    │   ├── ScreenCaptureThread.java ← Chụp màn hình JPEG qua java.awt.Robot
    │   └── AudioPlaybackRenderer.java ← Phát âm thanh PCM từ BlockingQueue
    └── ui/
        ├── LoginController.java     ← Màn hình đăng nhập / đăng ký
        ├── MainController.java      ← Hub điều phối TOÀN BỘ UI và logic Client
        └── components/
            ├── ChatFileMessageController.java
            ├── ChatImageMessageController.java
            ├── ChatAudioMessageController.java
            └── MediaViewer.java     ← Trình xem ảnh/video nội tuyến (không qua OS)
```

---

## 2. Giao Thức TCP — Định Dạng Message

Mọi giao tiếp qua TCP đều được đóng gói thành **một chuỗi JSON trên 1 dòng** và ghi vào `PrintWriter`. Phía nhận dùng `BufferedReader.readLine()` để tách từng gói.

**Cấu trúc gói tin (class `Message.java`):**
```json
{
  "type": "SEND_DM",
  "payload": {
    "receiverId": 42,
    "content": "Hello!"
  }
}
```

**Toàn bộ 28 loại MessageType (enum `MessageType.java`):**

| Nhóm | MessageType | Chiều đi |
|---|---|---|
| **Auth** | `LOGIN`, `LOGIN_RESP` | Client → Server → Client |
| **Auth** | `REGISTER`, `REGISTER_RESP` | Client → Server → Client |
| **Presence** | `ONLINE_USERS_UPDATE` | Server → All Online |
| **Group** | `CREATE_GROUP`, `CREATE_GROUP_RESP` | Client → Server → Client |
| **Group** | `GROUP_INVITE`, `GROUP_INVITE_RESP` | Client → Server → Target |
| **Group** | `CREATE_CHANNEL`, `CREATE_CHANNEL_RESP` | Client → Server → Members |
| **Chat** | `SEND_DM`, `SEND_GROUP_MSG` | Client → Server |
| **Chat** | `NEW_MESSAGE_NOTIFY` | Server → Recipients |
| **Chat** | `GET_CHAT_HISTORY`, `CHAT_HISTORY_RESP` | Client ↔ Server |
| **File** | `UPLOAD_FILE_INIT`, `UPLOAD_FILE_RESP` | Client ↔ Server |
| **File** | `FILE_CHUNK` | Client → Server (nhiều lần) |
| **File** | `FILE_UPLOAD_COMPLETE`, `FILE_UPLOAD_NOTIFY` | Server → Client |
| **File** | `DOWNLOAD_FILE_REQ`, `DOWNLOAD_FILE_RESP` | Client ↔ Server |
| **File** | `GET_FILES_IN_CONTEXT`, `FILES_IN_CONTEXT_RESP` | Client ↔ Server |
| **Stream** | `STREAM_START`, `STREAM_STARTED` | Client → Server → Members |
| **Stream** | `STREAM_STOP`, `STREAM_STOPPED` | Client → Server → Members |
| **Call (DM)** | `CALL_REQUEST`, `CALL_INCOMING` | Client → Server → Target |
| **Call (DM)** | `CALL_ACCEPT`, `CALL_REJECT`, `CALL_REJECTED` | Client → Server → Caller |
| **Error** | `ERROR` | Server → Client |

---

## 3. TÍNH NĂNG 1 — Khởi Động Server

### Class: `ServerMain.java` → `main(String[] args)`

```
ServerMain.main()
    │
    ├─ DatabaseManager.getConnection()        ← Khởi tạo MySQL singleton
    │       URL: jdbc:mysql://localhost:3306/lancord
    │       Driver: com.mysql.cj.jdbc.Driver
    │
    ├─ Files.createDirectories("server_storage/")  ← Tạo thư mục lưu file upload
    │
    └─ new ServerSocket(PORT=8888)
            └─ Vòng lặp vô tận: serverSocket.accept()
                    └─ new ClientHandler(clientSocket)
                            └─ pool.execute(handler)   ← FixedThreadPool(50 threads)
```

> **Mô hình concurrency:** Server dùng `Executors.newFixedThreadPool(50)`. Khi Client kết nối, một `ClientHandler` mới được tạo và nộp vào pool. Mỗi Client có **1 thread riêng** để blocking `readLine()` mà không block các Client khác.

---

## 4. TÍNH NĂNG 2 — Đăng Nhập / Đăng Ký (TCP)

```
┌─────────────────────────────────┐         TCP :8888          ┌──────────────────────────────┐
│   CLIENT (LoginController)      │ ─────────────────────────> │   SERVER (ClientHandler)     │
│                                 │                             │                              │
│  onLoginClick()                 │                             │   run()                      │
│   ├─ new TCPConnection()        │                             │    └─ while(readLine())      │
│   ├─ connection.connect(h, 8888)│  {type: "LOGIN",            │        └─ deserialize()      │
│   └─ sendMessage(LOGIN) ───────>│   payload: {username,       │        └─ handleMessage()    │
│                                 │            password}}       │            └─ handleLogin()  │
│                                 │                             │                │             │
│                                 │                             │    UserRepo.findByUsername() │
│                                 │                             │    BCrypt.checkpw(pw, hash)  │
│                                 │                             │    ServerManager             │
│                                 │                             │     .registerUser(u, this)   │
│                                 │                             │     └─ onlineUsers.put(id)   │
│                                 │  {type: "LOGIN_RESP",       │     └─ broadcastOnlineUsers()│
│  handleMessage()            <───│   payload: User object}    │    sendMessage(LOGIN_RESP)    │
│   └─ Chuyển sang MainController │                             │                              │
└─────────────────────────────────┘                             └──────────────────────────────┘
```

**`TCPConnection.connect()`:** Mở `new Socket(host, port)`, bọc IO vào `BufferedReader` / `PrintWriter`, spawn thread `listen()` để đọc phản hồi bất đồng bộ.

**`TCPConnection.sendMessage(msg)`:**
```java
String json = JsonUtil.serialize(message);  // Jackson → JSON string 1 dòng
out.println(json);                           // PrintWriter tự flush (autoFlush=true)
```

**`ClientHandler.handleLogin(payload)`:**
1. `UserRepo.findByUsername(username)` — truy vấn MySQL.
2. `BCrypt.checkpw(rawPw, hash)` — so khớp mật khẩu được băm với jBCrypt.
3. `ServerManager.registerUser(u, this)` — thêm `ClientHandler` vào `ConcurrentHashMap`. Nếu đã online → trả `ERROR`.
4. `ServerManager.broadcastOnlineUsers()` — cập nhật danh sách người online cho **tất cả** Client ngay lập tức.
5. `sendMessage(LOGIN_RESP)` — gửi User object về Client vừa login.

---

## 5. TÍNH NĂNG 3 — Nhắn Tin Chat (TCP)

### Luồng DM (Nhắn tin 1-1):

```
  CLIENT A (MainController)           SERVER (ClientHandler A)        CLIENT B (MainController)
       │                                       │                               │
  onSendMessage()                              │                               │
  └─ SEND_DM ──────────────────────────────> │                               │
     {receiverId: B.id, content: "Hi!"}       │ handleChatMessage()           │
                                              │ ├─ MessageRepo.saveMessage()  │
                                              │ │   sender=A, type=DM,        │
                                              │ │   receiver=B, content       │
                                              │ ├─ chatMsg.setSenderName(A)   │
                                              │ ├─ ServerManager              │
                                              │ │   .sendToUser(B.id, notify) │
                                              │ │   [onlineUsers.get(B.id)    │
                                              │ │    .sendMessage(notify)]    │
                                              │ │                             │
                                              │ └─ sendMessage(notify) ──────>│ [echo lại A]
  <── NEW_MESSAGE_NOTIFY ──────────────────── │ ──NEW_MESSAGE_NOTIFY ────────>│
  {chatMsg + senderName}                      │ {chatMsg + senderName}        │
                                              │                        buildTextMessage()
                                              │                        → Hiển thị lên UI
```

**`ServerManager.sendToUser(userId, msg)`** — O(1) lookup:
```java
ClientHandler handler = onlineUsers.get(userId); // ConcurrentHashMap
if (handler != null) handler.sendMessage(message);
```

### Luồng Group Message:

- **General Channel (ID=1):** `ServerManager.broadcast(msg)` → gửi đến **tất cả** online users.
- **Các Group Channel khác:** `GroupRepo.getGroupMembers(channelId)` lấy danh sách thành viên → `ServerManager.sendToUsers(members, msg)`.

---

## 6. TÍNH NĂNG 4 — Truyền Tải File (TCP Chunking)

### Tại sao cần Chunking?

File lớn không thể bỏ hết vào RAM rồi gửi 1 lần (OutOfMemoryError). LANCord đọc từng block ~64KB, mã hóa Base64, gói vào `FILE_CHUNK` và gửi tuần tự.

### Sơ đồ đầy đủ quy trình Upload → Download:

```
  CLIENT (Sender)          SERVER (ClientHandler)       CLIENT (Receiver)
  MainController
       │
  onUploadFile()
  ├─ UPLOAD_FILE_INIT ──────────────────────────────> │
  │  {name, size, mimeType,                           │ handleUploadInit()
  │   contextId, contextType}                         │ ├─ FileRepo.createFileMetadata()
  │                                                   │ └─ sendMessage(UPLOAD_FILE_RESP)
  │ <── UPLOAD_FILE_RESP ──────────────────────────── │    {fileId: 123}
  │
  sendChunks(fileId, file)
  [Vòng lặp: đọc từng 64KB block]
  ├─ FILE_CHUNK ──────────────────────────────────> │
  │  {fileId: 123, chunk: "Base64..."}               │ handleFileChunk()
  ├─ FILE_CHUNK ──────────────────────────────────> │ ├─ FileRepo.getFileMetadata(fileId)
  ├─ FILE_CHUNK ──────────────────────────────────> │ ├─ Files.write(path, chunk, APPEND)
  │   ... (lặp cho đến hết file) ...                │ └─ if(Files.size(path) >= meta.size):
  │                                                   │      ├─ FILE_UPLOAD_COMPLETE → Sender
  │                                                   │      └─ FILE_UPLOAD_NOTIFY  → Others
  │ <── FILE_UPLOAD_COMPLETE ─────────────────────── │
  │                                                   │ ──── FILE_UPLOAD_NOTIFY ──────────────> │
  │                                                   │                               Hiển thị bubble
  │                                                   │                               "⬇ Tải về"
  │
  [Người nhận ấn "Tải về"]
  │                                                   │ <── DOWNLOAD_FILE_REQ ──────────────── │
  │                                                   │     {fileId: 123}
  │                                                   │ handleDownloadReq()
  │                                                   │ ├─ đọc toàn bộ file từ disk
  │                                                   │ └─ DOWNLOAD_FILE_RESP ────────────────>│
  │                                                   │    {data: Base64(bytes)}        Lưu vào
  │                                                   │                               client_downloads/
```

**Chi tiết `FileDownloadRegistry`:**
Là `Map<Integer fileId, Consumer<byte[]> callback>`. Khi `MainController` gửi `DOWNLOAD_FILE_REQ`, nó đăng ký một callback ngay trước khi gửi. Khi server trả về `DOWNLOAD_FILE_RESP`, callback được gọi với dữ liệu file — callback này lưu file ra đĩa và tự động mở Media Viewer.

---

## 7. TÍNH NĂNG 5 — Stream Nhóm (TCP Signaling + UDP Multicast Media)

> Đây là tính năng kỹ thuật phức tạp nhất. Hệ thống dùng **2 giao thức song song**: TCP chỉ để bắt tay thiết lập phiên, UDP Multicast để truyền Media thực sự.

### Giai đoạn A: Signaling qua TCP

```
  CLIENT A (Streamer)                  SERVER                            CLIENT B (Viewer)
       │                                  │                                    │
  onGoLive()                              │                                    │
  ├─ new UDPStreamSender(...)             │                                    │
  └─ STREAM_START ───────────────────> │                                    │
     {channelId, groupId}                │ handleStreamStart():               │
                                         │ ├─ ServerManager                   │
                                         │ │  .getMulticastIpForChannel(id)   │
                                         │ │   Nếu chưa có: "230.0.0.1" (mới) │
                                         │ │   Nếu có rồi:  dùng lại          │
                                         │ │                                   │
                                         │ ├─ ServerManager                   │
                                         │ │  .assignSenderId(channelId, userId)
                                         │ │   → senderId = 1 (byte, 1-indexed)│
                                         │ │                                   │
                                         │ ├─ ServerManager                   │
                                         │ │  .addActiveStreamer(id, sId, name)│
                                         │ │                                   │
                                         │ └─ STREAM_STARTED ────────────────>│
  <── STREAM_STARTED ────────────────── │    {multicastIp: "230.0.0.1",      │
  {multicastIp: "230.0.0.1",            │     port: 9999,                    │
   port: 9999,                           │     senderId: 2,                   │
   senderId: 1,                          │     streamerNames: {1: "Alice"}}   │
   streamerNames: {1: "Alice"}}          │                             handleStreamStarted()
                                         │                             ├─ new UDPStreamReceiver(
  handleStreamStarted()                  │                             │    "230.0.0.1", 9999,
  ├─ sender.setSenderId(1)               │                             │    mediaDispatcher)
  ├─ new AudioCaptureThread(sender)      │                             └─ receiver.start()
  ├─ new WebcamCaptureThread(sender)     │
  └─ new ScreenCaptureThread(sender)     │
     [Tất cả start()]                    │
```

### Giai đoạn B: Truyền Media qua UDP Multicast

```
┌────────────────────────────────────────────────────────────────────┐
│                    LUỒNG GỬI (Client Streamer)                      │
│                                                                      │
│  Luồng thu thập phần cứng (3 thread chạy song song):               │
│                                                                      │
│  AudioCaptureThread.run():                                          │
│  ├─ TargetDataLine.read(buf, 0, BUFFER_SIZE)                        │
│  │   [PCM_SIGNED, 16kHz, 16bit, Mono — 20ms/chunk = 640 bytes]     │
│  └─ sender.sendAudio(bytes)                                         │
│                                                                      │
│  WebcamCaptureThread.run():                                         │
│  ├─ sarxos.Webcam.getDefault().getImage()  [15 FPS, 640×480]       │
│  ├─ toJpeg(frame, quality=0.6f)            [JPEG nén 60%]          │
│  └─ sender.sendWebcam(jpeg, isKeyframe)    [mỗi 30 frame = keyframe]│
│                                                                      │
│  ScreenCaptureThread.run():                                         │
│  ├─ Robot.createScreenCapture(screenRect)  [10 FPS]                │
│  ├─ scaleImage(screenshot, 1280, 720)      [Scale xuống HD]        │
│  ├─ toJpeg(scaled, quality=0.5f)           [JPEG nén 50%]         │
│  └─ sender.sendScreen(jpeg, isKeyframe)    [mỗi 20 frame = keyframe]│
│                                                                      │
│  ─────────────────────────────────────────────────────────────────  │
│  UDPStreamSender.sendFragmented(mediaType, data, isKeyframe):       │
│                                                                      │
│  1. Tính số mảnh: totalFrags = ceil(data.length / 1388)            │
│  2. Lấy seqId: seqIdCounter.getAndIncrement() & 0xFFFF             │
│                                                                      │
│  FOR mỗi fragment i từ 0..totalFrags-1:                            │
│    ├─ Tính flags:                                                   │
│    │   flags = 0x01 (FLAG_IS_FRAGMENT)                              │
│    │   if i == last:   flags |= 0x02 (FLAG_IS_LAST_FRAGMENT)       │
│    │   if isKeyframe:  flags |= 0x04 (FLAG_IS_KEYFRAME)            │
│    │                                                                │
│    ├─ UDPHeader.encode() → 12 bytes (BIG_ENDIAN):                  │
│    │   [mediaType:1B][flags:1B][seqId:2B][fragIndex:2B]            │
│    │   [totalFrags:2B][payloadLen:2B][senderId:1B][reserved:1B]    │
│    │                                                                │
│    ├─ Ghép packet = header(12B) + payload(≤1388B)                  │
│    └─ MulticastSocket.send(packet → "230.0.0.1":9999)              │
└────────────────────────────────────────────────────────────────────┘

                🌐 LAN NETWORK (UDP Multicast 230.0.0.1:9999)
            [Switch/Router tự nhân bản gói → tất cả máy đã joinGroup]

┌────────────────────────────────────────────────────────────────────┐
│                    LUỒNG NHẬN (Client Viewer)                       │
│                                                                      │
│  UDPStreamReceiver.receiveLoop() [Daemon Thread]:                   │
│  └─ socket.receive(DatagramPacket)   ← block đến khi có gói tin    │
│      └─ processPacket(pkt)                                          │
│           │                                                         │
│           ├─ UDPHeader.decode(data)  → ParsedHeader                │
│           │   {mediaType, flags, seqId, fragIndex, totalFrags,     │
│           │    payloadLen, senderId}                                 │
│           │                                                         │
│           ├─ Nếu packet < 12 bytes → bỏ qua (malformed)           │
│           │                                                         │
│           ├─ key = senderId + ":" + seqId   ← unique per frame    │
│           │                                                         │
│           ├─ IF totalFrags == 1:  ← Gói nhỏ (audio thường ≤1388B)│
│           │   └─ dispatcher.onMedia(senderId, mediaType, payload)  │
│           │                                                         │
│           └─ ELSE:  ← Gói lớn, cần gom mảnh                       │
│               ├─ buf = fragmentMap.computeIfAbsent(key,            │
│               │         k → new FragmentBuffer(h))                 │
│               │   FragmentBuffer: {fragments[][], receivedCount,   │
│               │                    totalFrags, createdAt}          │
│               ├─ synchronized(buf):                                │
│               │   buf.fragments[fragIndex] = payload               │
│               │   buf.receivedCount++                              │
│               │                                                    │
│               └─ IF receivedCount == totalFrags:  ← Frame đủ mảnh!│
│                   ├─ Nối mảnh: System.arraycopy(each frag → full[])│
│                   ├─ fragmentMap.remove(key)  ← giải phóng bộ nhớ │
│                   └─ dispatcher.onMedia(senderId, mediaType, full) │
│                                                                      │
│  ScheduledExecutor.cleanStaleBuffers() — mỗi 500ms:                │
│  └─ fragmentMap.removeIf(e → now - e.createdAt > 2000ms)           │
│      [Dọn dẹp các frame bị mất gói, không bao giờ hoàn tất]        │
└────────────────────────────────────────────────────────────────────┘
```

### Giai đoạn C: Phát Media — `MediaDispatcher` & Playback

```
MediaDispatcher.onMedia(senderId, mediaType, fullData)
[Đây là @FunctionalInterface — implementation là lambda trong MainController]
        │
        ├─ MEDIA_AUDIO (0x00):
        │       AudioPlaybackRenderer audioRenderer = renderers.get(senderId)
        │       audioRenderer.enqueue(fullData)
        │           └─ queue.offer(data)   ← BlockingQueue(capacity=50), non-blocking
        │                                    Nếu đầy → DROP (tránh lag tích lũy)
        │           AudioPlaybackRenderer.playbackLoop() [Dedicated Thread]:
        │           └─ data = queue.take()  ← block đến khi có data
        │               └─ SourceDataLine.write(data, 0, data.length)
        │                   → Phát ra loa hệ thống
        │
        ├─ MEDIA_WEBCAM (0x01):
        │       Platform.runLater(() → {          ← Phải gọi trên JavaFX thread
        │           Image img = new Image(new ByteArrayInputStream(fullData))
        │           imageViewMap.get(senderId).setImage(img)
        │       })
        │
        └─ MEDIA_SCREEN (0x02):
                Platform.runLater(() → {
                    Image img = new Image(new ByteArrayInputStream(fullData))
                    screenShareView.setImage(img)
                })
```

### Giai đoạn D: Kết thúc Stream

```
  CLIENT A (Streamer)                SERVER                     CLIENT B (Viewer)
       │                               │                              │
  stopAllMedia()                       │                              │
  ├─ audioCapture.stopCapture()        │                              │
  ├─ webcamCapture.stopCapture()       │                              │
  │   └─ running=false; webcam.close() │                              │
  ├─ screenCapture.stopCapture()       │                              │
  ├─ udpSender.close()                 │                              │
  └─ STREAM_STOP ─────────────────> │                              │
     {channelId, groupId, senderId}    │ handleStreamStop():          │
                                       │ ├─ ServerManager             │
                                       │ │  .removeActiveStreamer()   │
                                       │ └─ STREAM_STOPPED ──────────>│
                                       │                         udpReceiver.stop()
                                       │                         ├─ socket.leaveGroup()
                                       │                         ├─ socket.close()
                                       │                         └─ staleCleanup.shutdown()
```

---

## 8. TÍNH NĂNG 6 — Cuộc Gọi Video 1-1 (DM Call)

> Cuộc gọi DM dùng cùng hạ tầng UDP Multicast như Group Stream, nhưng **Signaling khác**: không có khái niệm channelId — Server phân bổ một Multicast IP tạm thời riêng cho cuộc gọi này.

```
  CLIENT A (Người gọi)          SERVER (ClientHandler)         CLIENT B (Người nhận)
       │                               │                               │
  Bấm nút "Gọi"                        │                               │
  └─ CALL_REQUEST ───────────────────>│                               │
     {receiverId: B.id}                │ handleCallRequest()           │
                                       │ └─ CALL_INCOMING ────────────>│
                                       │    {callerId: A.id,           │
                                       │     callerName: "Alice"}      │
                                       │                        Dialog: "Alice đang gọi..."
                                       │                               │
                                       │                         [B ấn Nghe]
                                       │                        CALL_ACCEPT ─────────────>│
                                       │                        {callerId: A.id}           │
                                       │  handleCallAccept()                               │
                                       │  ├─ getMulticastIpForChannel(callChannelId)       │
                                       │  ├─ assignSenderId → senderId_A                   │
                                       │  ├─ assignSenderId → senderId_B                   │
                                       │  ├─ STREAM_STARTED ──────────────────────────────>│
  <── STREAM_STARTED ─────────────────│     {multicastIp, port=9999, senderId_B}           │
  {multicastIp, port=9999, senderId_A} │                               │
       │                               │                               │
  [Bắt đầu Audio+Video Capture]        │                         [Bắt đầu nhận UDP]
  [senderId=A → UDP Multicast]         │                         [senderId=B → UDP Multicast]
```

**Khi B từ chối:**
- B gửi `CALL_REJECT {callerId: A.id}`.
- Server chuyển thành `CALL_REJECTED` gửi về A.
- A hiển thị thông báo "Cuộc gọi bị từ chối".

---

## 9. Quản Lý Trạng Thái Online — `ServerManager.java`

`ServerManager` là một utility class **hoàn toàn static** và **thread-safe** nhờ dùng `ConcurrentHashMap` và `synchronized`.

```java
// 4 ConcurrentHashMap chính:

// 1. Registry người online: userId → ClientHandler (socket của người đó)
Map<Integer, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

// 2. Multicast IP cố định của mỗi channel (persists trong suốt session server):
//    channelId → "230.0.0.x"  (tạo mới theo nextMulticastOctet, range 230.0.0.1-254)
Map<Integer, String> channelMulticastMap = new ConcurrentHashMap<>();

// 3. SenderID (1 byte) của mỗi user trong mỗi channel:
//    channelId → { userId → senderId }
//    Dùng để receiver biết gói UDP này từ ai (sender nhúng vào header)
Map<Integer, Map<Integer, Byte>> channelSenderIds = new ConcurrentHashMap<>();

// 4. Ai đang stream trong channel nào:
//    channelId → { senderId → username }
//    Dùng để hiển thị "Ai đang live" và auto-notify người mới vào
Map<Integer, Map<Byte, String>> activeStreamersByChannel = new ConcurrentHashMap<>();
```

**Cơ chế Auto-Join Stream:**
Khi Client mở một Group Channel → `GET_CHAT_HISTORY`. Server xử lý:
```
handleGetChatHistory()
├─ Gửi CHAT_HISTORY_RESP (lịch sử tin nhắn)
└─ if (ServerManager.isStreamActive(channelId)):
    ├─ Lấy multicastIp cho channel
    ├─ assignSenderId(channelId, userId)  ← cấp senderId cho viewer mới
    ├─ getActiveStreamers(channelId)      ← danh sách ai đang stream
    └─ Gửi STREAM_STARTED {userId: -1}   ← flag -1 = "join as viewer only"
```
Client nhận `userId: -1` → chỉ khởi tạo `UDPStreamReceiver`, **không** khởi tạo Capture Threads.

---

## 10. Kiến Trúc Tổng Thể & Q&A Chuyên Sâu

### Sơ đồ tổng thể:

```
┌──────────────────────────────────────────────────────────────────────┐
│                     LANCord — Hybrid Network Architecture             │
│                                                                        │
│  ┌──────────────┐    TCP :8888 (JSON/Line)  ┌─────────────────────┐  │
│  │   Client A   │ <───────────────────────> │      SERVER         │  │
│  │  (JavaFX)    │    ← Chat, Auth, Files →  │                     │  │
│  │              │    ← Signaling/Control →  │  ClientHandler x50  │  │
│  │  TCPConnection│                           │  ServerManager      │  │
│  │  MainController│   TCP :8888             │  DatabaseManager    │  │
│  └──────┬───────┘ <───────────────────────> │  (MySQL)            │  │
│         │                                   └─────────────────────┘  │
│         │                                                              │
│  ┌──────▼───────┐                                                     │
│  │   Client B   │                                                     │
│  │  (JavaFX)    │                                                     │
│  │              │ ════════ UDP Multicast :9999 ═══════════════════>  │
│  │  UDP: Sender │ <══════════════════════════════════════════════════ │
│  │  UDP: Receiver│        (Server KHÔNG tham gia luồng media)        │
│  └──────────────┘   Audio(PCM) / Webcam(JPEG) / Screen(JPEG)         │
└──────────────────────────────────────────────────────────────────────┘
```

### Bảng so sánh giao thức:

| Tính năng | Giao thức | Port | Class chính |
|---|---|---|---|
| Đăng nhập / Đăng ký | TCP | 8888 | `LoginController` ↔ `ClientHandler.handleLogin()` |
| Chat DM / Group | TCP | 8888 | `MainController` ↔ `ClientHandler.handleChatMessage()` |
| Upload / Download File | TCP | 8888 | `MainController.sendChunks()` ↔ `ClientHandler.handleFileChunk()` |
| Group Stream Signaling | TCP | 8888 | `MainController.onGoLive()` ↔ `ClientHandler.handleStreamStart()` |
| DM Call Signaling | TCP | 8888 | `MainController` ↔ `ClientHandler.handleCallRequest/Accept()` |
| **Audio / Webcam / Screen** | **UDP** | **9999** | `UDPStreamSender` ↔ `UDPStreamReceiver` |
| Online Users Presence | TCP | 8888 | `ServerManager.broadcastOnlineUsers()` |

---

## 11. Q&A Chuyên Sâu (Câu Hỏi Giáo Viên)

### Q: Tại sao TCP cho Chat nhưng UDP cho Video?

| Tiêu chí | TCP | UDP |
|---|---|---|
| Đảm bảo delivery | Có (ACK + Retransmit) | Không |
| Thứ tự packet | Đảm bảo | Không đảm bảo |
| Độ trễ | Cao hơn (round-trip ACK) | Cực thấp |
| Phù hợp cho | Chat, File, Auth | Audio/Video realtime |

Trong Video Call: **mất 1-2 frame = chấp nhận được** (người xem chỉ thấy giật nhẹ), nhưng **trễ 500ms = không chấp nhận được** (hội thoại bị gián đoạn). UDP ưu tiên tốc độ, TCP ưu tiên tính toàn vẹn.

### Q: Multicast khác Unicast / Broadcast thế nào? Tại sao dùng Multicast?

- **Unicast:** A gửi cho B, C, D → phải gửi 3 gói riêng biệt → Sender bị bottleneck khi nhiều viewer.
- **Broadcast:** Gửi cho tất cả máy trong mạng, kể cả máy không quan tâm → lãng phí băng thông.
- **Multicast:** A gửi 1 gói vào địa chỉ nhóm `230.0.0.1`. Switch/Router tự nhân bản đến những máy đã `joinGroup()` → **hiệu quả O(1) về phía Sender**, scalable khi tăng số viewer.

> Phạm vi Multicast: `224.0.0.0 – 239.255.255.255`. LANCord dùng dải `230.0.0.x` (local scope trong hầu hết LAN).

### Q: TTL=32 trong `UDPStreamSender` có tác dụng gì?

```java
socket.setTimeToLive(32); // giới hạn số hop router
```
TTL kiểm soát gói tin có thể đi qua tối đa bao nhiêu router trước khi bị hủy. TTL=32 đủ cho LAN nhưng ngăn gói tin lan ra Internet rộng hơn. Với WAN cần tăng lên 64 hoặc 128.

### Q: Xử lý Packet Loss (mất gói tin UDP) như thế nào?

Cơ chế **Stale Buffer Cleanup** trong `UDPStreamReceiver`:
```java
// Mỗi 500ms, scheduled executor dọn dẹp:
private void cleanStaleBuffers() {
    long now = System.currentTimeMillis();
    fragmentMap.entrySet().removeIf(e -> now - e.getValue().createdAt > 2000);
}
```
Nếu 1 frame bị mất ≥1 mảnh → không bao giờ `receivedCount == totalFrags` → sau 2 giây bị dọn sạch. Frame tiếp theo (seqId mới) vẫn được xử lý bình thường. Đây là kỹ thuật **"frame skip"** cơ bản — không retransmit (vì UDP đã gửi đi rồi), đơn giản là bỏ frame đó.

### Q: `seqId` dùng để làm gì? Tại sao không dùng `fragIndex` để phân biệt?

`seqId` là **số thứ tự của frame (ảnh/audio chunk)**, còn `fragIndex` là **thứ tự mảnh trong 1 frame**. Ví dụ: Frame 5 của Alice được chia thành 3 mảnh → 3 gói có `seqId=5, fragIndex=0/1/2`. Nếu thiếu `seqId`, receiver không thể biết "mảnh fragIndex=0 này thuộc về frame nào — frame cũ đang chờ hay frame mới?"

### Q: Tại sao `AudioPlaybackRenderer` dùng `BlockingQueue`?

```java
BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(50); // ~1 giây buffer

// Thread UDP (producer): non-blocking
queue.offer(data);  // Nếu đầy → DROP (tránh lag tích lũy)

// Thread Playback (consumer): blocking
byte[] data = queue.take();  // block đến khi có data
line.write(data, 0, data.length);
```

Thiết kế này giải quyết vấn đề **jitter** (biến động độ trễ mạng): nếu 10 gói đến cùng lúc thì queue hấp thụ và phát lần lượt. Capacity=50 × 20ms = **1 giây buffer**. Nếu quá tải → drop để tránh âm thanh bị lag ngày càng tệ hơn.

### Q: Tại sao phải dùng `Platform.runLater()` khi cập nhật ImageView?

JavaFX có quy tắc: **chỉ JavaFX Application Thread mới được thay đổi UI**. `UDPStreamReceiver` chạy trên một daemon thread riêng (non-UI thread). Nếu gọi `imageView.setImage()` trực tiếp từ thread đó sẽ gây `IllegalStateException`. `Platform.runLater()` đưa task vào hàng đợi của JavaFX thread để thực thi an toàn.

### Q: Server có thể bị bottleneck ở đâu không?

Điểm yếu tiềm tàng:
1. **ThreadPool(50):** Nếu hơn 50 Client kết nối đồng thời, connection thứ 51 phải chờ. Giải pháp: Dùng `Executors.newCachedThreadPool()` hoặc NIO Selector.
2. **File Download:** Toàn bộ file được đọc vào memory trước khi gửi (`handleDownloadReq`). File rất lớn có thể gây OOM. Giải pháp: Streaming file theo chunk.
3. **Broadcast:** Khi nhiều user online và gửi tin nhắn vào General Channel, `ServerManager.broadcast()` ghi lần lượt vào từng socket. Giải pháp: Async/parallel send.
4. **UDP Media:** Server **không tham gia** — Multicast P2P-like nên không bị bottleneck ở đây. ✅
