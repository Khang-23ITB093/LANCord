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

## Tính năng đã làm (Phase 1)
1. Đăng nhập bằng username.
2. Hiển thị danh sách online realtime.
3. Chat DM 1-1 (cơ bản).
4. Gửi file, hình ảnh, video, âm thanh
5. Setup database, kiến trúc message JSON linh hoạt.

> **Lưu ý**: Một số chức năng như Group, Channel UI, Upload/Download file vật lý cần được hoàn thiện thêm logic giao diện (code server đã hỗ trợ khung sườn).
