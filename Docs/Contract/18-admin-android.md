# 18 — Quản trị chủ đề ngay trong Android

Cập nhật 30/10/2026. Dùng API quản trị hiện có; không đổi backend, database hoặc quyền tài khoản.

## Cách dùng khi báo cáo

1. Backend local chạy cổng **8081**, app dùng `http://10.0.2.2:8081/` trên emulator.
2. Mở app → Cá nhân → Đăng nhập. Dùng email tài khoản đã có role ADMIN và mật khẩu SpeakMate đã đăng ký; không có mật khẩu admin mặc định.
3. Đăng nhập thành công sẽ mở **Quản trị**. Phiên ADMIN còn lưu khi mở lại app cũng vào đây; không hiển thị thanh tab học viên.
4. Mở **Quản lý chủ đề luyện nói**: danh sách thật, tìm trong các trang đã tải, chạm dòng cuối để tải thêm; chạm số lượng để làm mới.
5. **Thêm chủ đề**: nhập tên, mô tả tùy chọn, trình độ A1–C2 và tình huống/hướng dẫn cho AI. Lưu thành công mới quay lại danh sách. **Sửa** dùng đúng ID server; **Ẩn/Hiện** chỉ cập nhật khi server xác nhận.
6. **Tài khoản quản trị** hiển thị tài khoản đang đăng nhập. **Đăng xuất** gọi backend và xóa phiên trên máy.

Chủ đề ẩn không dùng để tạo bài mới; lịch sử bài cũ vẫn giữ. Backend không có trường câu mở đầu nên form không bắt nhập trường đó. Khi lưu bị gián đoạn, quay lại tải danh sách để xác định kết quả trước khi tạo lại; app không tự gửi lại POST.

Quản lý người dùng, bộ thẻ hệ thống, đổi mật khẩu và các thống kê chưa có API tương ứng được vô hiệu hóa/ẩn hoặc hiển thị chưa hỗ trợ. Không dùng dữ liệu mẫu làm kết quả quản trị thật. Không cần trang web riêng để quản lý chủ đề trong app sau bản cập nhật này; web admin vẫn dùng được.

## Code và API

| Phần | File / trách nhiệm |
| --- | --- |
| Phân luồng | `MainActivity`, `AuthSession.isAdmin()` đọc role trả từ backend. USER tiếp tục vào luyện nói. |
| Chặn màn sai quyền | `ui/AdminFragment` yêu cầu phiên ADMIN; 401/403 về đăng nhập. Backend vẫn kiểm quyền trên từng request. |
| Dashboard / tài khoản | `AdminDashboardFragment`, `AdminAccountFragment`: tổng chủ đề server, thông tin phiên và logout. |
| Danh sách | `AdminTopicsFragment`: GET `/admin/topics?page=...&size=20`, phân trang, chi tiết, PATCH `/admin/topics/{id}/active`. |
| Form | `AdminTopicFormFragment`: POST `/admin/topics` hoặc PUT `/admin/topics/{id}` với `name`, `description`, `level`, `scenario`. Giữ bản nháp, kiểm giới hạn và khóa thao tác khi lưu. |
| HTTP | `api/AdminApi` gom endpoint/kiểm ID response, `ApiClient` + `BackendHttpApi` bổ sung PUT. Tiếp tục dùng SessionManager và Retrofit đang có. |

Tiền tố API là `/api/v1`. Không nhận role từ form đăng ký hoặc tự nâng quyền ở Android.

## Kiểm chứng

- `:app:assembleDebug :app:testDebugUnitTest`: PASS; build APK kiểm thử `assembleDebugAndroidTest` cũng PASS.
- Bản cuối trỏ `http://10.0.2.2:8081/`: build APK/app-test, unit test và `lintDebug` PASS. Đã cài lại bằng `adb install -r` và mở MainActivity; HTTP từ emulator tới backend trả 401 cho `/auth/me` khi không gửi token, xác nhận đường mạng hoạt động.
- AndroidJUnitRunner `AdminIntegrationTest`: **3 test PASS, 35,973 giây**, chạy trên emulator với HTTP fixture riêng `127.0.0.1:18080`. Đăng nhập ADMIN, mở lại activity, tổng chủ đề, POST tạo với chặn bấm trùng, PUT đúng ID/scenario, PATCH đúng active, logout; 400 giữ bản nháp/cho thử lại; USER bị chặn và 403 về đăng nhập.
- Kết quả fixture lưu local trong `backend/build/admin-check/android-admin-fixture.log` bị Git ignore. File phiên mã hóa của app được giữ và khôi phục sau test.
- Không gọi Gemini, không tạo user/topic hoặc đổi quyền trong database thật. Các test này chứng minh Android gửi/nhận đúng contract qua HTTP fixture, chưa phải E2E với tài khoản admin thật. Người dùng đăng nhập bằng mật khẩu của mình để nghiệm thu trên dữ liệu demo.
