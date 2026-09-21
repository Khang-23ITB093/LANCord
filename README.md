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
   - Chạy script `schema.sql` ở thư mục gốc để tạo bảng. Mặc định `DatabaseManager.java` kết nối tới `localhost:3306`, user `root`, pass `root`, db `lancord`. Nếu khác, vui lòng sửa lại trong `DatabaseManager.java`.

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
- **Hệ thống tin nhắn cơ bản**: Gửi tin nhắn văn bản (Text).
- **Truyền tải tệp tin (File Transfer)**: 
  - Gửi và nhận file (Ảnh, Audio, Tệp tin chung).
  - Tích hợp bộ nhớ đệm cục bộ (`upload_cache.properties`) giúp người gửi không phải tải lại file vừa gửi.
- **Kết nối cơ bản**: Nhập username là có thể kết nối vào hệ thống chat (chưa có bảo mật).
- **Kiến trúc mạng UDP Multicast**: Khung truyền phát Screen Streaming bằng gói tin UDP.

### 🚧 Chưa Làm / Đang Phát Triển (Backlog & To-Do)
1. **Quản lý Tài khoản & Bảo mật**: Cần hệ thống Đăng nhập / Đăng ký có mật khẩu, Session/Token, Profile User.
2. **Quản lý Nhóm và Kênh (Discord-like)**: Giao thức mạng đã có, nhưng chưa có UI và logic hoạt động thực tế cho việc tạo nhóm, mời người, phân quyền, phân kênh.
3. **Gọi thoại / Video Streaming**: Mới có bộ khung UDP Multicast, cần bắt luồng từ Camera/Microphone thực tế.
4. **Nâng cấp UX/UI**:
   - Thanh tiến trình (Progress Bar) khi Upload/Download file lớn.
   - Typing Indicator (Hiển thị trạng thái "đang soạn tin nhắn...").
   - Trạng thái Hoạt động (Online/Offline Realtime cho User/Group).

> Chi tiết phân công và tiến độ cụ thể vui lòng tham khảo file [PROGRESS.md](./PROGRESS.md).
