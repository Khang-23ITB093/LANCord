# LANCord Project Progress & Backlog

Tài liệu này theo dõi tiến độ thực tế của dự án LANCord, phân định rõ những tính năng đã chạy được thực tế và những tính năng mới chỉ có khung (skeleton/stubs) hoặc chưa được làm.

## ✅ Đã Hoàn Thiện (Thực tế đã chạy được)
- **Hệ thống tin nhắn cơ bản**: Gửi tin nhắn văn bản (Text).
- **Truyền tải tệp tin (File Transfer)**: 
  - Gửi và nhận file (Ảnh, Audio, Tệp tin chung).
  - Tích hợp bộ nhớ đệm cục bộ (`upload_cache.properties`) giúp người gửi không phải tải lại file vừa gửi.
- **Kết nối cơ bản**: Nhập username là có thể kết nối vào hệ thống chat (chưa có bảo mật).

## 🚧 Chưa Làm / Đang Phát Triển (Cần tập trung trong tương lai)

### 1. Quản lý Tài khoản & Bảo mật (Authentication)
- Chuyển từ đăng nhập "nhập mỗi tên" sang hệ thống Đăng nhập / Đăng ký hoàn chỉnh (Cần Mật khẩu).
- Quản lý phiên đăng nhập (Session/Token).
- Hồ sơ người dùng (Avatar, Tiểu sử).

### 2. Quản lý Nhóm và Kênh (Group & Channel Management)
- *Hiện tại mới chỉ có khung giao thức mạng, chưa có logic thực tế và UI hoàn chỉnh.*
- Tạo nhóm, mời bạn bè vào nhóm.
- Phân chia các kênh (Channel) nhỏ bên trong nhóm (giống Discord).
- Phân quyền (Admin, Member).

### 3. Gọi thoại / Video Streaming (Voice & Video Calls)
- *Hiện tại UDP Multicast mới chỉ là khung (Skeleton), chưa bắt được Mic/Camera.*
- Tích hợp ghi âm (Microphone) và phát âm thanh (Speaker) theo thời gian thực.
- Giao diện người dùng cho cuộc gọi (Nút nghe, gọi, kết thúc).

### 4. Nâng cấp Trải nghiệm Người dùng (UX/UI)
- **Thanh tiến trình (Progress Bar)**: Hiển thị phần trăm khi Upload/Download các tệp tin nặng.
- **Typing Indicator**: Hiển thị trạng thái "đang soạn tin nhắn...".
- **Trạng thái Hoạt động**: Hiển thị chấm trạng thái (Online/Offline) theo thời gian thực trên danh sách bạn bè thay vì chỉ load một lần.

---
*Cập nhật lần cuối: Tháng 9/2026*
