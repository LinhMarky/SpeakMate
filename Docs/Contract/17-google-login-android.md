# 17 — Nối đăng nhập Google trên Android

Cập nhật 30/10/2026. Không đổi backend, schema hoặc quy tắc liên kết tài khoản.

Admin Android đã có luồng riêng trong [file 18](18-admin-android.md): role ADMIN → quản trị chủ đề thật. Google login và quản trị là hai phần độc lập; tài khoản ADMIN hiện có vẫn có thể đăng nhập bằng email/mật khẩu.

## Nguyên nhân và phần đã nối

Nút Google trong `LoginFragment` trước đây chỉ hiện Toast “chưa được kết nối”. Backend đã có `POST /api/v1/auth/google`, `AuthApi.google()` cũng đã có; Android chưa đọc Web client ID hoặc mở bộ chọn tài khoản.

Luồng hiện tại: nút Google → `auth/GoogleSignIn.java` mở Credential Manager → lấy Google ID token → `AuthApi.google()` gửi token tới backend → backend xác minh chữ ký/audience/email → trả phiên SpeakMate → lưu bằng `SessionManager` và mở màn luyện tập.

Android không tự cấp phiên từ email Google. Không ghi ID token vào log hoặc file. Nút bị khóa khi đang xử lý; hủy chọn tài khoản, thiếu tài khoản/provider và phản hồi không hợp lệ đều có thông báo. Đóng view sẽ hủy yêu cầu chọn tài khoản và bỏ callback cũ.

SDK: AndroidX Credentials/Play Services Auth 1.6.0 và Google ID 1.2.1. Theo [hướng dẫn nút Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), dùng `GetSignInWithGoogleOption` cho nút được người dùng bấm; gọi `getCredentialAsync` để giữ code Java.

## Cấu hình

Hai giá trị sau phải là **cùng OAuth Web client ID**, không phải Android client ID, Gemini key hoặc client secret:

```properties
# backend/application-local.properties (bị Git ignore)
app.auth.google-client-id=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com

# frontend/local.properties (bị Git ignore)
speakmate.google.webClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Trên máy hiện tại, cả hai giá trị đã tồn tại và khớp nhau; giữ nguyên, không chép chuỗi mẫu đè lên. `app/build.gradle.kts` đọc cấu hình Android vào `BuildConfig.GOOGLE_WEB_CLIENT_ID`. Đổi client ID phải build/cài lại APK; đổi backend local cần khởi động lại backend.

Google Cloud cần Android OAuth client với package `com.example.speakmate` và SHA-1 đúng keystore ký APK, cùng project với Web client. Nếu ở Testing, thêm Gmail dùng thử vào test users. Xem hướng dẫn cấu hình trong [luồng tài khoản](../SpeakMate-luong-tai-khoan.md). Không cần đưa client secret vào Android.

Backend local vẫn **8081**; emulator dùng `http://10.0.2.2:8081/`. Docker hiện có `GOOGLE_CLIENT_ID` rỗng trong `.env`: nếu chuyển app sang Docker 8082 thì cần tự cấu hình biến này bằng đúng Web client ID và recreate riêng backend; không sửa key hay volume để chữa lỗi đăng nhập.

## Kiểm chứng và bước thao tác

- Build `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest`: PASS; 1 unit test PASS.
- `:app:lintDebug`: PASS; không có lỗi chặn build. Các cảnh báo hiện có xem `frontend/app/build/reports/lint-results-debug.html`.
- `GoogleSignInTest` chạy AndroidJUnitRunner trên emulator: **4 test PASS**, chỉ dùng credential/JWT giả cục bộ, không đăng nhập Google hoặc tạo dữ liệu server. Kiểm tra đọc đúng loại credential, từ chối loại khác/bundle thiếu và thông báo lỗi hủy/thiếu provider/tài khoản.
- Backend 8081 nhận request Google với token sai định dạng và trả 401; đây là kiểm tra từ chối token, không phải đăng nhập Google thật.
- APK mới đã cài bằng `adb install -r`, giữ dữ liệu app. Chưa nghiệm thu Google E2E bằng tài khoản thật; không gọi Gemini trong đợt sửa này.

Trong Android Studio: Run `app` → Cá nhân → Đăng nhập → Tiếp tục với Google → chọn tài khoản và chấp thuận nếu Google yêu cầu. Nếu máy chưa có tài khoản, thêm Google account trong Cài đặt emulator; máy ảo cần Google Play services.

Email chưa có trong SpeakMate sẽ được backend tạo tài khoản USER. Với email đã đăng ký bằng mật khẩu nhưng chưa liên kết Google, backend trả **409 GOOGLE_LINK_REQUIRED** để giữ tài khoản cũ. Luồng liên kết có API `POST /auth/google/link` cần phiên đăng nhập hợp lệ, nhưng màn Cá nhân Android hiện chưa có nút nối API này; đăng nhập bằng mật khẩu vẫn dùng được. Không tự liên kết, đổi role hoặc tạo bản sao để bỏ qua lỗi này.
