# Tic Tac Toe Online

Giao diện tài khoản gồm đăng ký, đăng nhập bằng tên đăng nhập hoặc email, đăng xuất và đặt lại mật khẩu.

Sau khi đăng nhập, bạn có thể tạo phòng riêng hoặc tham gia bằng mã 8 ký tự. Phòng chờ hiển thị tối đa hai người chơi và tự cập nhật khi có người vào hoặc rời phòng.

## Chạy trên Windows

Cần JDK 25, Maven, Node.js và PostgreSQL. Backend dùng database `tictactoe_db` trên cổng `5432` với tài khoản `postgres`. Chạy `CREATE DATABASE tictactoe_db;` nếu database chưa có.

Trong PowerShell tại thư mục dự án, khởi động backend.

```powershell
cd backend
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Mật khẩu PostgreSQL' -AsSecureString)).Password
mvn.cmd spring-boot:run
```

Mở PowerShell thứ hai tại thư mục dự án và khởi động frontend.

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Mở http://localhost:5173/. Vite chuyển tiếp `/api` đến backend ở cổng `8080`.

## Thử phòng chờ với hai tài khoản

1. Đăng nhập bằng tài khoản thứ nhất, chọn **Tạo phòng mới** và sao chép mã.
2. Mở trình duyệt khác hoặc cửa sổ ẩn danh, đăng nhập bằng tài khoản thứ hai rồi nhập mã và chọn **Vào phòng**.
3. Sau khi cả hai tài khoản xuất hiện trong phòng chờ, thử tải lại trang để trở về phòng hiện tại. Khi người chơi thứ hai rời phòng, người khác có thể vào thế chỗ. Khi chủ phòng rời hoặc đăng xuất, phòng đóng và người chơi còn lại trở về trang chủ.

## Bật email đặt lại mật khẩu

Trước khi chạy backend, đặt các biến sau trong cùng cửa sổ PowerShell. Ví dụ dùng một hộp thư thử SMTP chạy trên cổng `1025`.

```powershell
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '1025'
$env:RESET_MAIL_FROM = 'no-reply@example.test'
$env:APP_FRONTEND_URL = 'http://localhost:5173/'
```

Với SMTP yêu cầu xác thực qua STARTTLS, dùng host, cổng và địa chỉ gửi của dịch vụ. Đặt thêm `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SMTP_AUTH=true` và `SMTP_STARTTLS=true`. Khởi động lại backend sau khi thay đổi các biến.

Sau khi yêu cầu đặt lại mật khẩu trên giao diện, mở liên kết trong email. Liên kết hết hạn sau 30 phút và dùng được một lần. Đổi mật khẩu sẽ kết thúc các phiên đăng nhập cũ.

## Cấu hình khi triển khai

Phục vụ frontend và chuyển tiếp `/api` trên cùng tên miền HTTPS. Đặt `APP_FRONTEND_URL` theo URL thực tế, `AUTH_COOKIE_SECURE=true` và `JWT_SECRET` bằng khóa ngẫu nhiên dài ít nhất 32 byte, mã hóa Base64. Khi chạy local mà chưa đặt `JWT_SECRET`, backend tạo khóa tạm và phiên đăng nhập hết hiệu lực mỗi khi backend khởi động lại.
