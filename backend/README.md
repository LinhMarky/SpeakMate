# SpeakMate backend

Chạy backend/MySQL bằng Docker từ thư mục gốc, không cần Java/MySQL local: **[README chính](../README.md)**; kiểm API thật tại [README Docker](../README-Docker.md).

Cập nhật 30/10/2026. Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.8; Docker dùng MySQL 8.4. Giữ 7 bảng/24 operation P0, mở rộng level đến C2 bằng migration V4. Vòng UI chat → review Gemini thật → lưu/ôn từ → lịch sử đã PASS và đối chiếu MySQL; xem [file 16 và cách nâng database cũ](../Docs/Contract/16-hoan-thien-tu-ai-cap-do-va-lich-su.md). Demo chung, Google/SMTP/âm thanh vẫn cần nghiệm thu riêng; số liệu test cũ bên dưới là lịch sử.

## 1. Chạy trên máy bạn

1. Database mới: chạy [speakmate.sql](../speakmate.sql). Nếu đã có schema v2, sao lưu và chạy một lần [migration v3](../Docs/migrations/V3__account_auth.sql) khi backend dừng; không chạy script cài mới đè database cũ.
2. Điền MySQL/JWT trong `backend/application-local.properties`. File này bị Git bỏ qua; không thay cấu hình đã hoạt động bằng mẫu rỗng.
3. Máy clone mới: copy `application-local.properties.example` thành file local, điền MySQL và tạo JWT secret theo [hướng dẫn tài khoản](../Docs/SpeakMate-luong-tai-khoan.md).
4. Mở terminal tại backend:

```powershell
cd backend # Từ gốc repository; cần JDK 21.
.\gradlew.bat build
.\gradlew.bat bootRun
```

Linux/macOS dùng `sh ./gradlew build` và `sh ./gradlew bootRun`. Java 21/Gradle 8.8 phù hợp [yêu cầu Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html). Backend dùng cổng 8081, emulator Android dùng `http://10.0.2.2:8081/`.

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/speakmate?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
app.auth.jwt-secret=YOUR_RANDOM_BASE64_SECRET
```

Các giá trị trên là chỗ thay thế. Không ghi đè JWT secret sẵn có bằng chuỗi mẫu. Cổng mặc định 8081, có thể dùng biến PORT. Trong IntelliJ, working directory là thư mục backend, main class SpeakMateApplication. File local được đọc tương đối với working directory.

Biến chuẩn Spring như SPRING_DATASOURCE_PASSWORD ghi đè property tương ứng trong file local. Giá trị cụ thể trong local ưu tiên hơn DB_PASSWORD/JWT_SECRET nằm trong biểu thức mặc định của application.properties. Docker không copy file local nên dùng đúng các biến trong README chính. Không đưa mật khẩu vào Git/chat.

Backend không gọi Google/SMTP lúc khởi động. Thiếu cấu hình hai dịch vụ này chỉ làm API tương ứng trả 503. Không có màn hình ở /; kiểm tra API tại /api/v1/auth/.... GET /api/v1/auth/me thiếu token phải trả 401 JSON.

Gemini cũng không được gọi khi khởi động. Điền `app.ai.api-key` trong file local, model mặc định `gemini-3.5-flash-lite`. Thiếu key trả 503 AI_NOT_CONFIGURED ở thao tác cần AI. Có thể thêm [năm chủ đề](../Docs/seeds/V1__topics.sql) vào database đã chọn; script chỉ thêm tên chưa có và không tạo tài khoản. ADMIN chuẩn bị riêng theo [file 08](../Docs/Contract/08-doc-code-va-chay-backend.md).

## 2. Tính năng và cách đọc code

- Đăng ký, đăng nhập mật khẩu, Google ID token, liên kết Google tường minh.
- JWT access 15 phút; refresh ngẫu nhiên, DB chỉ giữ hash, hạn 7 ngày, xoay token mỗi lần sử dụng.
- Một phiên hiện hành/tài khoản; login mới/logout/reset thu hồi access cũ qua auth_version.
- Quên mật khẩu bằng email: mã 6 chữ số, 10 phút, 5 lần sai, chờ 60 giây để xin lại.
- Kiểm trạng thái tài khoản và role từ DB trên mỗi request được bảo vệ; /api/v1/admin/** yêu cầu ADMIN.
- DTO và Mapper không trả Entity/hash; JSON có field ngoài DTO bị từ chối.
- 5 API chủ đề cho learner/admin: xem, thêm, sửa, bật/tắt; topic tắt không tạo bài mới nhưng không làm mất bài cũ.
- Tạo bài có snapshot và UUID; chat tối đa 20 USER, lưu DB trước khi gọi AI; retry không tạo trùng, lease 90 giây phục hồi khi server dừng.
- Kết thúc đóng bài trước, chỉ sau đó sinh nhận xét/câu sửa/khuyến nghị/từ gợi ý. Review lỗi giữ transcript, có retry; không tự đổi level hồ sơ.
- Chọn lưu từ sang Từ của tôi, giữ nghĩa cá nhân khi trùng; ghi nhận LEARNING/MASTERED và thời gian ôn.

Đọc [Luồng tài khoản](../Docs/SpeakMate-luong-tai-khoan.md) để xem endpoint, request/response, sơ đồ, từng file và hướng dẫn Google/SMTP. Bắt đầu bằng Controller → AuthService → AuthTransactionService → UserRepository → UserMapper; sau đó đọc TokenService và SecurityConfiguration.

Nền JPA: AbstractAuditingEntity chứa bốn field audit; @PrePersist/@PreUpdate do JPA gọi. Người thực hiện là email đã xác thực hoặc system. User mặc định A1/USER/active; Topic lưu tình huống và vai AI. Enum lưu STRING/VARCHAR, thời gian Instant/UTC/DATETIME(6). Không trả User trực tiếp qua API.

ddl-auto=validate kiểm các Entity đã ánh xạ, không tự sửa schema và không kiểm toàn bộ CHECK/UNIQUE. SQL là nguồn thiết kế dữ liệu. Chi tiết ở [tài liệu CSDL](../Docs/SpeakMate-thiet-ke-co-so-du-lieu.md).

## 3. Kiểm thử

build chạy unit test và đóng gói JAR. Các test nhãn mysql chạy riêng với MySQL thật tạm tại **127.0.0.1:13317/speakmate_test**. Test từ chối URL khác. Import speakmate.sql vào DB thử với tên thay thành speakmate_test trong bản import tạm; không sửa schema chính và không dùng database ứng dụng.

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:13317/speakmate_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:TEST_DB_USERNAME='root'
$env:TEST_DB_PASSWORD='' # Chỉ dùng cho server tạm riêng đã initialize-insecure.
.\gradlew.bat build mysqlTest
```

EntityMappingTest dùng rollback. Các test auth cần commit để kiểm tra bộ đếm, phiên và cạnh tranh; chúng để dữ liệu thử trong DB tạm để kiểm tra, vì vậy phải hủy toàn bộ server/datadir thử sau khi kết thúc. Không chạy trên DB thật. Google/SMTP được thay tại ranh giới giao tiếp bên ngoài; việc ký JWT, BCrypt, transaction, Security và MySQL vẫn chạy thật. GoogleTokenVerifierTest tự ký RSA với khóa thử để kiểm chứng logic xác minh, không gọi Google.

Báo cáo HTML sau khi chạy:

- build/reports/tests/test/index.html: unit test.
- build/reports/tests/mysqlTest/index.html: tích hợp MySQL/API.

Ngày 25/10/2026: `build mysqlTest` thành công, **68 unit test và 11 test tích hợp MySQL/API, 0 lỗi, 0 bỏ qua**. Đã kiểm tra đăng ký an toàn, phiên/token, khóa tài khoản/quyền admin, Google/link, reset, hai yêu cầu refresh đồng thời và hai lần đăng nhập Google đồng thời chỉ để lại một phiên hiện hành.

JAR đóng gói đã chạy trên cổng thử 18080 và gọi HTTP thật: register → login → me → logout; access cũ sau logout trả 401. Google/SMTP chưa cấu hình trả 503 theo thiết kế. Tiến trình ứng dụng thử đã dừng sau kiểm tra.

SQL v3 có **77 ca đạt, 6 ca giới hạn DB/Service**; migration v2→v3 giữ tài khoản/chủ đề trên MySQL 8.0.45 tạm. Chưa có thông tin kết nối Google/SMTP thật, chưa xác nhận cấu hình MySQL 3306 của bạn; các kết quả tạm không thay cho việc kiểm thử đó. Database thử được dừng và dọn riêng sau kiểm chứng.

## 4. Bước tiếp theo

Ngày 26/10/2026, đợt kiểm chứng mới nhất `build mysqlTest` đạt **77 unit test và 27 test MySQL/API**, không lỗi/bỏ qua. Đã bổ sung hai nhánh timeout transport và kiểm quyền finish/review/retry/history. 16 test tích hợp học kiểm quyền, snapshot, gửi/tạo trùng, cạnh tranh với End, lỗi provider/lease, ghi báo cáo atomic, token cũ về muộn, thu hồi phiên và lưu từ đồng thời. AiClient trong test được thay bằng stub; adapter Gemini được kiểm bằng HTTP mock, chưa gọi Gemini thật. [Báo cáo môi trường thực tế](../Docs/Contract/11-kiem-chung-moi-truong-thuc-te.md) ghi kết quả JAR/HTTP, lỗi cấu hình và các bước kiểm tiếp.

Trên Windows, có thể tự tạo/chạy/dọn MySQL thử bằng `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-MySql.ps1` trong backend. Script từ chối cổng 13317 đã có server, không dùng DB 3306 và dọn sau khi mysqld đã thoát. Báo cáo nằm ở build/reports/tests/test và mysqlTest.

Trạng thái FE sau pull và phần còn phải nghiệm thu nằm ở [audit UI](../Docs/Contract/14-android-ui-integration.md) và [bàn giao Docker mới nhất](../Docs/Contract/15-docker-va-ban-giao-nhom.md). Kết quả unit/MySQL với AI stub không xác minh giao diện Android, Google, SMTP hoặc âm thanh thật. Xem [bản đồ từng hàm](../Docs/Contract/08-doc-code-va-chay-backend.md) khi cần lần theo code.

[Mục lục tài liệu](../Docs/README.md) · [Hướng dẫn tài khoản](../Docs/SpeakMate-luong-tai-khoan.md)
