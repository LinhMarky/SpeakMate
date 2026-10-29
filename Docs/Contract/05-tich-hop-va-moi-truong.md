# 05 — Tích hợp, môi trường và cách hai người làm việc

> Cài đặt hiện tại dùng [README chính](../../README.md). Quy trình T0 dưới đây là kế hoạch; một DB nhóm chưa tự xuất hiện khi mỗi máy chạy Docker. Cách chọn một backend demo chung và các thông tin còn thiếu ở [bàn giao Docker](15-docker-va-ban-giao-nhom.md).

## 1. Chuẩn bị tại T0

| Hạng mục | Người phụ trách | Điều kiện xác nhận |
| --- | --- | --- |
| MySQL/schema v3 | BE | Backend Started; Hibernate validate; đăng ký/login/me chạy trên DB nhóm |
| JWT secret | BE | Random secret đúng yêu cầu, nằm local/server env, không vào Git |
| Google OAuth | BE tạo cấu hình project; FE lấy SHA-1/điền client ID | Android chọn tài khoản thật, BE xác minh audience, nhận token SpeakMate |
| SMTP | BE | Xin mã → hộp thư thật → reset → mật khẩu cũ không dùng được |
| AI provider/model/key | BE | Một lời đáp thật và một review có cấu trúc trong giờ đầu tiên |
| Điện thoại demo/STT/TTS | FE | Nhận một câu tiếng Anh và đọc một câu tiếng Anh, nghe thử chất lượng |
| URL LAN/cổng | Cả hai | Điện thoại gọi được backend; cùng một database tích hợp |

Chưa có key/config ở T0 là một dependency cần xử lý, không phải lý do sửa UI cho hiển thị thành công giả. Nếu chưa có SMTP/Google, ghi chức năng chưa nghiệm thu và vẫn phát triển phần độc lập; trước demo phải công bố đúng phần nào đã chạy thật.

Cập nhật 26/10/2026: đã triển khai Gemini REST, mặc định `gemini-3.5-flash-lite`; điền `app.ai.api-key` trong file local theo [hướng dẫn 08](08-doc-code-va-chay-backend.md). 24 operation P0 backend đã qua kiểm thử MySQL với AI stub; FE có thể bắt đầu nối server, nhưng vẫn phải xác minh AI thật và chất lượng giọng nói để đạt M0–M2.

## 2. Chạy backend và Android

Schema mới dùng [speakmate.sql](../../speakmate.sql); database v2 dùng [migration v3](../migrations/V3__account_auth.sql) một lần khi server dừng và đã sao lưu. Không chạy script cài mới chồng lên database có dữ liệu. Giữ `ddl-auto=validate`.

```powershell
cd backend
.\gradlew.bat build
.\gradlew.bat bootRun
```

Backend dùng cổng **8081** theo cấu hình dự án đã chốt. `backend/application-local.properties` bị Git bỏ qua. Điền MySQL/JWT, Google Web client ID, SMTP theo [luồng tài khoản](../SpeakMate-luong-tai-khoan.md). Khóa AI chỉ cấu hình ở backend; tên cấu hình provider cụ thể được BE ghi vào hướng dẫn khi chọn dịch vụ, không tạo giá trị bí mật mẫu trong repo.

`frontend/local.properties`:

```properties
speakmate.google.webClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
speakmate.api.baseUrl=http://10.0.2.2:8081/
```

Emulator dùng 10.0.2.2; điện thoại thật thay bằng `http://IP_LAN_CUA_MAY_BE:8081/`, cùng mạng và chỉ mở kết nối cần dùng. Sau sửa local.properties phải Sync/Build lại vì giá trị được đưa vào BuildConfig. Release dùng URL HTTPS; debug mới cho HTTP. Chi tiết Google Web client/Android client/SHA-1 và SMTP ở tài liệu auth; không đưa client secret vào FE.

```powershell
cd frontend
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Test thiết bị cần emulator/điện thoại đã mở khóa, không có hộp lỗi hệ thống che app. Bộ test MySQL của backend chỉ chạy trên database thử riêng theo README backend; không dùng database tích hợp để test ghi/xóa phá dữ liệu.

## 3. Dữ liệu nhóm và tài khoản

- Một DB tích hợp do BE quản lý; FE không sửa schema hoặc tự tạo ID để khớp ví dụ.
- Ít nhất hai USER A/B để kiểm sai quyền và một ADMIN riêng. Mỗi người tự đặt mật khẩu; không ghi vào Markdown, Git hoặc ảnh demo.
- Tài khoản ADMIN đầu tiên: đăng ký tài khoản bình thường, BE xác minh đúng dòng bằng email trong môi trường phát triển rồi gán role ADMIN qua quy trình khởi tạo kiểm soát. Không tạo public endpoint nhận role=ADMIN.
- Admin tạo ít nhất năm tình huống trong file 01. Topic ID do server trả; FE lưu ID đó, không dùng số trong examples.json làm cấu hình thật.
- Dữ liệu demo không chứa cuộc hội thoại riêng tư thật. Không import examples.json vào MySQL: nó gồm nhiều thời điểm khác nhau, token/ID chỉ minh họa.

## 4. Làm song song bằng contract

FE chọn một fixture theo name/operationId/httpStatus trong examples.json. Tạo mock repository ở chế độ phát triển có nhãn rõ; không để màn hình báo mock là kết quả AI thật. Token EXAMPLE_ONLY không dùng gọi server. Khi chuyển server thật, giữ DTO/UI giống contract và thay nguồn dữ liệu.

BE triển khai 15 operation P0 mới, đọc được request mẫu trước; không đợi FE xong giao diện. Mỗi operation bàn giao phải có: URL/operationId, body/response đúng schema, một ca thành công, một ca lỗi quan trọng, build/kiểm tra đã chạy và trạng thái phụ thuộc ngoài (AI/SMTP/Google).

Ví dụ bàn giao: “sendConversationMessage đã chạy trên server nhóm; retry cùng UUID không tạo USER thứ hai; provider timeout giữ USER; FE có thể thay mock của CHAT-01”. Không bàn giao bằng câu “đã viết xong Entity” khi API chưa gọi được.

## 5. Giao việc và Git

| Phần | Chủ sở hữu chính | Vùng dễ xung đột |
| --- | --- | --- |
| backend/src/main/java, SQL, config server | BE | SecurityConfiguration, DTO chung, migration |
| frontend/app và cấu hình Android | FE | MainActivity, auth client, navigation |
| backend/src/main/resources/static/admin | FE | BE cần mở đúng đường dẫn static; không đồng thời sửa file HTML của FE |
| Docs/Contract/openapi.json + examples.json | BE giữ contract; cả hai review | Không tự đổi JSON một phía |

Mỗi người làm branch ngắn theo use case (`be/conversation-turn`, `fe/chat-api`...), commit nhỏ, đồng bộ sau mỗi mốc. Cùng máy/worktree thì thống nhất file trước khi chỉnh. Không reset/ghi đè thay đổi của người kia để “lấy bản mới”. Secrets/local.properties/build outputs không commit.

Ghi công việc theo mẫu: **Task ID — người làm — endpoint/màn hình — đầu vào — tiêu chí xong — phụ thuộc — trạng thái — bằng chứng**. Trạng thái: TODO / IN_PROGRESS / READY_TO_INTEGRATE / VERIFIED / BLOCKED. BLOCKED cần ghi thứ đang thiếu và người xử lý, không dùng để che một API chưa viết.

## 6. Quy tắc đổi contract

1. Đề xuất rõ operationId, field/status muốn đổi, lý do và ảnh hưởng FE/BE.
2. Người còn lại xác nhận trong kênh làm việc nhóm; trong lúc chờ, làm phần không phụ thuộc thay đổi.
3. Sửa OpenAPI, examples, tài liệu màn hình và code hai phía trong cùng đợt tích hợp.
4. Ghi change log cuối file này. Không sửa DB chỉ để khớp một màn hình demo cũ có decks.

Thêm field response tùy chọn thường ít ảnh hưởng hơn đổi tên/xóa/type; FE vẫn cần được biết. Request của backend hiện chặn field lạ nên “FE gửi thêm cũng được” là giả định sai. Sau T0+48 giờ, không đổi tên field/URL hoặc thêm công nghệ; chỉ sửa lỗi cần để P0 đạt.

## 7. Chốt mỗi ngày

- Đầu buổi 10 phút: chọn việc trong file 06, nêu dependency, cập nhật trạng thái task.
- Giữa buổi 15 phút: gọi API thật cùng nhau; xem HTTP/DB và màn hình, không chỉ ảnh chụp.
- Cuối buổi 20 phút: chạy mốc của ngày; ghi API đã deploy, lỗi còn lại, test thật/mock và việc đầu tiên ngày sau.
- Bất kỳ lỗi tích hợp: ghi operationId, thời điểm, HTTP/error, conversationId/requestId nếu có; không chụp access/refresh token, password hoặc toàn transcript riêng tư vào log nhóm.

## 8. Change log contract

| Phiên bản | Ngày | Nội dung |
| --- | --- | --- |
| 1.0.0 | 25/10/2026 | Chốt chung FE/BE cho 2 người/3 ngày; 9 auth đã có, 15 P0 mới, 5 P1; nói theo lượt, review cuối bài, Từ của tôi, admin chủ đề |
