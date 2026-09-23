# LANCord - Phase 1

LANCord là một ứng dụng nhắn tin và chia sẻ màn hình theo mô hình Discord, xây dựng bằng JavaFX kết hợp kiến trúc Client-Server và Peer-to-Peer sử dụng TCP, UDP Multicast cho đồ án môn học Network Programming.

## Cấu trúc dự án
- `edu.vku.lancord.server`: Server đa luồng xử lý TCP connections và Database (MySQL).
- `edu.vku.lancord.client`: Client UI bằng JavaFX, kết nối TCP tới server và hỗ trợ UDP Multicast để xem/phát Screen Streaming.
- `edu.vku.lancord.common`: Chứa Model và Protocol chia sẻ giữa Client và Server.

## Yêu cầu môi trường
- Java 17+
- Maven
- MySQL 8.0+

## Hướng dẫn cài đặt
1. **Khởi tạo Database**:
   - Chạy MySQL server.
   - Chạy script `schema.sql` ở thư mục gốc để tạo bảng. Mặc định `DatabaseManager.java` kết nối tới `localhost:3306`, db `lancord`. Nếu khác, vui lòng sửa lại trong `DatabaseManager.java`.

2. **Build dự án**:
   ```bash
   mvn clean package
   ```

3. **Chạy Server**:
   Chạy class `edu.vku.lancord.server.ServerMain`.
   Server sẽ lắng nghe ở port `8888` và tạo thư mục `server_storage/` để lưu file upload.

4. **Chạy Client**:
   Chạy class `edu.vku.lancord.client.ClientMain` hoặc qua Maven:
   ```bash
   mvn javafx:run
   ```
   Để test đa máy, hãy chạy ClientMain nhiều lần.

## Kiến trúc Mạng
- **TCP Socket**: Dùng cho Client-Server control plane. Tất cả tín hiệu (Login, Chat, Tạo Group, Upload File metadata) đều truyền qua TCP dưới dạng Newline-delimited JSON.
- **UDP Multicast**: Dùng riêng cho tính năng Screen Sharing ("Go Live"). Khi một user stream, Server chỉ cấp phát IP Multicast. Client dùng `java.awt.Robot` để chụp ảnh màn hình, nén JPEG, chia nhỏ thành các gói UDP và bắn trực tiếp vào Multicast Group. Các client khác join group này để nhận ảnh mà không đi qua server.

## Tiến độ Dự án & Backlog

### ✅ Tính năng đã làm (Thực tế đã chạy được)
- **Hệ thống tin nhắn cơ bản**: Gửi tin nhắn văn bản (Text) realtime trong Nhóm và DM.
- **Quản lý Tài khoản & Bảo mật**: Đăng nhập / Đăng ký có xác thực cơ bản, Hash mật khẩu.
- **Truyền tải tệp tin (File Transfer)**: 
  - Gửi và nhận file (Ảnh, Audio, Tệp tin chung, Video).
  - Tích hợp bộ nhớ đệm cục bộ (`upload_cache.properties`).
- **Kiến trúc mạng P2P Media**: Khung truyền phát Audio, Webcam, Screen Sharing bằng gói tin UDP Multicast, ghép nối FragmentBuffer.
- **Thiết kế UI/UX Discord-like**:
  - Giao diện Discord-like (Dark theme, 3-layer architecture).
  - Tích hợp tính năng Calling Bar, Group Live Bar.

### 🚧 Lỗi Đang Khắc Phục (Sẽ xử lý trong phiên tới)
1. **Lỗi Webcam/Camera kẹt trạng thái**: Bật rồi tắt nhanh khiến phần cứng camera vẫn tiếp tục hoạt động ngầm.
2. **Lỗi UI người xem khi tắt Live**: Màn hình của người xem không ẩn ngay lập tức khi Streamer tắt Live mà kẹt lại frame cuối cùng.

### 🚧 Backlog / Đang Phát Triển
1. **Quản lý Nhóm và Kênh**: Tạo nhóm, mời người, phân quyền, phân kênh theo dạng Tree (Discord-like).
2. **Nâng cấp UX/UI**:
   - Trạng thái Hoạt động (Online/Offline Realtime cho User/Group).
   - Typing Indicator (Hiển thị trạng thái "đang soạn tin nhắn...").
   - Trạng thái chưa đọc (Unread badge).

> Chi tiết phân công và tiến độ cụ thể vui lòng tham khảo file [PROGRESS.md](./PROGRESS.md).
