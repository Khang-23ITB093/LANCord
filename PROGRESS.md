# LANCord Project Progress & Backlog

Tài liệu này theo dõi tiến độ thực tế của dự án LANCord.

---

## ✅ Đã Hoàn Thiện

- **Authentication**: Đăng nhập / Đăng ký với validation (min ký tự, check mật khẩu).
- **Tin nhắn real-time**: Gửi/nhận text DM + Group, history load khi mở context.
- **File Transfer**: Upload/Download ảnh, video, audio, file chung; cache local.
- **UDP P2P Streaming engine**: `UDPHeader`, `FragmentBuffer`, `UDPStreamSender`, `UDPStreamReceiver`, `MediaDispatcher`.
- **Media capture**: `AudioCaptureThread`, `AudioPlaybackRenderer`, `WebcamCaptureThread`, `ScreenCaptureThread`.
- **UI Layered Architecture**: 3 panel chồng nhau (Welcome / DM / Group) — chỉ hiện panel đúng context.
- **CSS Design System**: Full token màu Discord, button variants, toggle states, scrollbar, input area.
- **ChatTextMessage**: Avatar chữ cái đầu + màu hash + fade-in animation.

---

## 🎯 ĐANG LÀM — UI/UX Discord-like Redesign

### Phân tích ảnh tham khảo (4 ảnh đã xem)

| File | Màn hình | Nội dung chính |
|---|---|---|
| `01_friends_list.png` | DM Friends list | Sidebar DM list, user row với avatar + status dot, User Profile Bar bottom |
| `02_dm_chat.png` | DM conversation | Chat messages với avatar, header bar với 📞📹 icons, User Profile right panel |
| `03_group_channel_voice.png` | Group/Server channel | Sidebar channel tree (text + voice), voice connected bar ở dưới sidebar, chat messages |
| `04_screen_share_picker.png` | Screen Share picker | Modal chọn app/tab/toàn màn hình để chia sẻ |

### Những gì LANCord CẦN LÀM (rút gọn, bỏ những gì không cần)

#### ✅ GIỮ LẠI
| Vùng UI | Chi tiết |
|---|---|
| Sidebar DM list | Avatar + tên + **online status dot** (xanh/xám) |
| Sidebar Group list | Danh sách nhóm (đơn giản, không cần channel tree như Discord) |
| User Profile Bar (bottom sidebar) | Avatar + tên + status + nút settings ⚙ |
| DM Header | `@` icon + tên + nút 📞 Voice + 🎥 Video |
| Group Header | `#` icon + tên + nút 🔴 Go Live |
| Chat message row | Avatar circle + sender name + timestamp + content |
| Call bar (DM) | Mic + Cam toggles + End button (slide in khi gọi) |
| Live bar (Group) | Mic + Cam + Screen toggles + End button |
| Input bar | `+` upload + text field |
| Welcome panel | Khi chưa chọn context |

#### ❌ LOẠI BỎ (không cần cho LANCord)
| Discord feature | Lý do bỏ |
|---|---|
| Nitro / Store | Không phù hợp |
| Nhiệm vụ / Quest | Không phù hợp |
| Cây kênh (channel tree) phân cấp | Quá phức tạp cho LANCord hiện tại |
| User Profile Panel (cột phải) | Để sau |
| Emoji picker nâng cao / Sticker | Để sau |
| Message reactions | Để sau |
| Pinned messages / Threads | Để sau |
| Search bar header | Để sau |

### Tiến độ Phase thực hiện

- [x] **Phase 1** — Design audit + color tokens
- [x] **Phase 2** — `discord-theme.css` đầy đủ
- [x] **Phase 3** — `main.fxml` layout chuẩn 72px rail + 240px sidebar + layered center
- [x] **Phase 4a** — `ChatTextMessage.fxml` + Controller với avatar + fade-in
- [x] **Phase 4b** — `MainController.java` User Profile Bar wiring
- [x] **Phase 5** — Online status dot trên DM list (xanh = online, xám = offline)
- [x] **Phase 6** — File card redesign (icon file, size label đúng chuẩn)
- [x] **Phase 7** — Call bar smooth slide-in animation
- [x] **Phase 8** — Screen share picker dialog (modal chọn app/window)
- [x] **Phase 9** — Compile + test end-to-end

### Context cho model tiếp theo

**Các file quan trọng:**
- `src/main/resources/.../main.fxml` — Layout chính (đã xong)
- `src/main/resources/.../discord-theme.css` — CSS system (đã xong)
- `src/main/java/.../MainController.java` — Controller chính
- `src/main/java/.../components/ChatTextMessageController.java` — Message row
- `.agents/ui-references/` — 4 ảnh Discord tham khảo

**Công việc còn lại ưu tiên cao:**
1. `online status dot` — trong `MainController.updateOnlineUsers()`, khi render mỗi user row cần thêm Circle xanh/xám. Hiện tại `setupListCellFactory` dùng Circle màu `#5865F2` cho tất cả, cần sửa để kiểm tra trạng thái online.
2. `File card redesign` — `ChatFileMessage.fxml` + `ChatFileMessageController` cần thêm file type icon và size label theo chuẩn Discord.
3. `Call bar animation` — Khi `showDMCallBar(true)` / `showGroupLiveBar(true)` cần thêm `TranslateTransition` slide-down 200ms.

---

## 🚧 Backlog (Chưa bắt đầu)

### Quản lý Nhóm và Kênh
- Tạo nhóm, mời thành viên.
- Phân quyền Admin/Member.
- Channel tree bên trong nhóm.

### UX nâng cao
- Typing indicator ("đang nhập...").
- Progress bar upload/download.
- Unread message badge trên sidebar.

### Lỗi còn tồn đọng (Cần fix ở phiên sau)
- Không có lỗi nghiêm trọng nào được ghi nhận.

---
*Cập nhật: 2026-09-24 11:55 — Đã fix lỗi kẹt camera do Thread không nhả resource và lỗi hiển thị giữ frame hình người xem khi luồng Live bị tắt đột ngột.*
