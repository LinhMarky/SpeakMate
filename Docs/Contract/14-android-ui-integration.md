# 14 — Nối giao diện Android hiện tại vào backend

> Admin Android đã nối phân luồng theo role và quản lý chủ đề qua API hiện có; hướng dẫn hiện tại ở [file 18](18-admin-android.md).

> Cập nhật 30/10/2026: vòng học thật đã PASS theo [file 16](16-hoan-thien-tu-ai-cap-do-va-lich-su.md). Nút Google nay đã nối Credential Manager → API backend; build và 4 test cục bộ PASS, đăng nhập Google thật còn chờ người dùng chọn tài khoản. Cấu hình và giới hạn email đã tồn tại ở [file 17](17-google-login-android.md). Các trạng thái bị chặn bên dưới là nhật ký của đợt cũ.

> Cập nhật Docker 30/10/2026: một tin nhắn qua backend Docker đã nhận Gemini HTTP 200, lưu và đọc lại MySQL đúng; lỗi thiếu key Docker đã xử lý. Đây không phải nghiệm thu lại UI Android hoặc toàn bộ vòng học. Chạy theo [README chính](../../README.md); trạng thái mới nhất ở [bàn giao nhóm](15-docker-va-ban-giao-nhom.md). Các mục bên dưới giữ kết quả của đợt nối UI trước đó.

Cập nhật: 29/10/2026. Tiếp nối [audit 13](13-final-integration-audit.md), giữ nguyên giao diện XML của nhóm và backend/schema hiện có.

**Trạng thái: chưa đạt end-to-end với Gemini thật.** Android đã nối đăng ký, đăng nhập, tải chủ đề, tạo bài, đọc và gửi tin. Luồng thật bị chặn tại phản hồi AI: backend trả `502 AI_UNAVAILABLE`; một yêu cầu chẩn đoán tối thiểu đến đúng model/key local xác nhận Google trả `503 UNAVAILABLE`. Không thay provider, model hoặc key để che lỗi.

Theo thứ tự bắt buộc trong yêu cầu, chưa tiến hành nối nhận xét, từ đã lưu, ôn thẻ và lịch sử server khi critical path thật chưa đạt. Không dùng kết quả audit API cũ hoặc fixture để tuyên bố UI thật đã qua.

## 1. Phần đã nối

| Màn hình/lớp | API và hành vi |
| --- | --- |
| RegisterFragment | POST `/auth/register`, gửi `name,email,password`; ẩn ô username không có trong DTO. Hiện lỗi server và trạng thái đang gửi. |
| LoginFragment | POST `/auth/login`, đọc `access_token,refresh_token,user`; đăng nhập xong sang Speaking. |
| SessionManager/SessionStore | Khôi phục helper cũ từ bản sao đã cứu; mã hóa phiên bằng Android Keystore, lưu trong thư mục không backup; tự refresh khi API trả 401 và thử lại một lần. |
| HomeFragment | Lời chào lấy tên từ phiên thật; thống kê/kế hoạch học còn dùng dữ liệu local. |
| SpeakingFragment | GET `/topics` theo phân trang; chọn ID thật và POST `/conversations` bằng `topicId,level,createRequestId`. Giữ UUID khi thử lại. |
| Mức luyện | A1/A2/B1/B2 theo enum/contract backend. **Backend không có endpoint riêng để tải danh sách trình độ**; không tạo API mới. |
| ChatFragment | GET `/conversations/{id}` và POST `/conversations/{id}/messages`; chỉ render transcript server, không tạo lời chào/phản hồi mẫu cho bài thật. |
| Mic/TTS | Giữ cơ chế STT/TTS hiện tại; câu nhận dạng vẫn vào ô nhập để người học kiểm tra rồi gửi cùng API. Chưa kiểm chứng âm thanh thật. |

Tất cả đường dẫn trên có prefix `/api/v1`. Backend vẫn dùng `8081`; URL debug emulator là `http://10.0.2.2:8081/`. Retrofit tự thêm `/api/v1/`; không lặp prefix trong đường dẫn API.

## 2. Giữ đúng lượt khi có lỗi

- Lưu nội dung và UUID trước POST; retry dùng lại đúng nội dung/UUID đó.
- Khóa gửi và sửa ô nhập trong lúc xử lý. Nhấn hai lần không tạo hai lượt.
- Sau lỗi hoặc timeout, GET trạng thái trước khi quyết định POST lại. Nếu server đã có câu AI thì render dữ liệu đó và xóa lượt chờ.
- Khi server đang xử lý, hỏi lại trạng thái; không tự gọi Gemini thêm bằng UUID mới.
- Hủy callback của view đã bị hủy; khi quay lại đọc transcript server. Việc rời màn hình không có nghĩa server đã hủy xử lý.
- Dữ liệu lượt chờ tách theo user, lưu trong thư mục không backup. Không ghi password/token vào báo cáo hoặc log kiểm thử.
- Màn nhận xét cũ là dữ liệu local. Nút tổng kết của bài có ID backend chưa mở vào màn đó, tránh hiển thị nhận xét/từ mẫu cho hội thoại thật.

## 3. Kiểm tra thật và bằng chứng

Thư mục bằng chứng local, bị Git bỏ qua: `backend/build/ui-integration/`.

| Kiểm tra | Kết quả thực tế |
| --- | --- |
| Backend 8081 | Dùng tiến trình backend hiện có; GET `/api/v1/auth/me` không token trả 401 đúng contract. Không build lại/ghi đè runtime JAR. |
| Đăng ký → đăng nhập trên UI | Đã thực hiện bằng tài khoản thử mới, không dùng dữ liệu tài khoản cá nhân. Tạo user 14. |
| Chọn chủ đề/mức → tạo bài trên UI | Đã thực hiện: topic 2, mức B2, conversation 9; bài ban đầu không có tin nhắn. |
| Gửi tin trên UI → MySQL | Có đúng một tin USER, id 49, nội dung và UUID giữ nguyên qua các lần kiểm tra. |
| Khôi phục phiên/bài thật sau recreate | PASS trên emulator bằng GET thật, không gọi AI thêm: phiên đúng user, câu/UUID cũ không thay đổi. |
| Backend → Gemini thật | Chưa đạt: diagnostic qua API hiện có ghi `diagnosticHttpStatus=502`, `diagnosticErrorCode=AI_UNAVAILABLE`. |
| Lỗi Google gốc | Chẩn đoán tối thiểu bằng cấu hình local, `maxOutputTokens=8`: HTTP 503, status UNAVAILABLE. Không in key/header/body lỗi thô. |
| Phản hồi AI trên UI | Chưa có tin AI trong bài thử; chưa được đánh dấu PASS. |
| Finish → feedback → lưu từ → ôn → lịch sử | Chưa nối trong đợt này do điều kiện critical path chưa đạt. API backend đã có, xem audit 13; không đồng nghĩa UI hiện tại dùng được. |

`ui-live-critical.json` chỉ lưu ID, transcript thử và mã lỗi, không lưu thông tin đăng nhập. SQL đọc bằng JDBC dùng `setReadOnly(true)`, lọc đúng `conversationId/userId`; không reset database hoặc sửa dữ liệu cũ.

Các lần chưa đạt được giữ trong log: test thật đầu tiên không nhận được câu AI; một lần chạy lại instrumentation bị ANR khi Activity mất focus; bản test chẩn đoán ban đầu dùng `Files.readString` không hỗ trợ trên emulator, đã sửa sang `readAllBytes`. Không tính các lần đó là PASS. Chi tiết kiểm tra cuối ghi ở mục tiếp theo.

Sau yêu cầu “tiếp tục”, đã thử lại một lần qua UI trên đúng conversation 9/UUID cũ. Test vẫn chưa thấy câu AI (`critical-retry-after-wait.log`, 76,399 giây); không đánh dấu critical path đạt. Không đổi model/key hay lặp gọi Gemini vô hạn. Theo [hướng dẫn lỗi của Google](https://ai.google.dev/gemini-api/docs/api-errors), 503 là dịch vụ tạm quá tải/không sẵn sàng; đây không phải bằng chứng key đang thiếu.

## 4. Build và test

Lệnh build mặc định trong `frontend`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:GRADLE_USER_HOME = 'C:\Users\PC\.gradle'
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug `
    --no-daemon --console=plain `
    '-Dorg.gradle.java.installations.paths=C:\Users\PC\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2,C:\Program Files\Java\jdk-21'
```

- Build đầu sau tích hợp: SUCCESSFUL, 49 giây; unit test mặc định chỉ có `ExampleUnitTest`. Lint lúc đó: 0 errors, 605 warnings.
- HTTP fixture trên emulator: **6/6 PASS**, 37,147 giây. Kiểm tra lỗi AI, JSON hỏng, AI timeout, mất phản hồi sau ghi, socket read timeout, rời màn hình trong lúc đang gửi. Các test đều kiểm tra retry/không nhân đôi ở các bước liên quan. Đây **không phải** MySQL/Gemini thật.
- URL `http://127.0.0.1:18080/` chỉ dành cho fixture chạy bên trong emulator, truyền tạm qua `-Pspeakmate.api.baseUrl=...`. Không đổi port backend; bản APK dùng demo phải build lại bằng lệnh mặc định ở trên.
- **Build cuối mặc định 8081:** `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` SUCCESSFUL, 49 giây; 1 unit PASS, lint 0 errors/605 warnings. Xem `final-build.log` và report trong `frontend/app/build/reports/`.
- **Fixture chạy lại sau sửa cuối:** 6/6 PASS, 32,863 giây, `fixture-final-native.log`.
- **Native bản cuối dùng backend thật:** 2/2 PASS, 11,34 giây: package-context test và khôi phục phiên/câu USER đang chờ sau recreate. `final-native-read-only.log`. Hai test này không tạo được phản hồi Gemini và không thay kết luận E2E chưa đạt.
- JDBC cuối: `REAL_MYSQL_OWNER=PASS`, `MESSAGE_COUNT=1`, `EXACT_TRANSCRIPT=PASS`; bài vẫn OPEN/NOT_STARTED. Xem `real-mysql-persistence.txt`.
- `git diff --check` PASS. Local BE/Android và recovery bị Git bỏ qua. Quét đối chiếu 4 giá trị bí mật local dài ít nhất 8 byte trong 255 file source/thay đổi và các entry APK: không thấy khớp. Đây là phép quét cụ thể, không phải chứng nhận bảo mật toàn diện.
- Đối chiếu 109 file `backend/src` với bản sao phục hồi: nội dung không đổi khi chuẩn hóa BOM/CRLF/LF. Không sửa backend hoặc schema trong đợt nối UI.

Không chạy lại backend unit/MySQL fixture vì không sửa backend; kết quả 77 unit và 27 MySQL test trong audit 13 thuộc lần kiểm trước, không phải kết quả mới của đợt nối UI này.

## 5. File thay đổi trong đợt nối UI

| File/nhóm file | Lý do |
| --- | --- |
| `frontend/app/build.gradle.kts` | Đọc base URL từ cấu hình hiện có; tạo BuildConfig; khôi phục dependency Retrofit 3.0.0. Không nâng SDK/AGP/Gradle. |
| `api/ApiClient.java`, `BackendHttpApi.java`, `LearningApi.java`, `PendingRequests.java` | Khôi phục lớp HTTP và UUID đã có trong bản sao trước khi mất code. |
| `auth/AuthApi.java`, `AuthSession.java`, `SessionManager.java`, `SessionStore.java` | Khôi phục xử lý tài khoản/phiên đã có; không viết client song song. |
| `ui/NetworkFragment.java` | Tận dụng helper gọi mạng nền, bỏ callback của view cũ; thêm constructor dùng layout XML và xử lý hết phiên qua MainActivity. |
| `MainActivity.java` | Điều hướng sau đăng nhập/khi hết phiên. |
| `LoginFragment.java`, `RegisterFragment.java` | Bind form hiện tại với AuthApi; loading/validation/lỗi thật. |
| `SpeakingFragment.java` | Tải chủ đề, tạo bài bằng ID và UUID server. |
| `ChatFragment.java` | Nối transcript/gửi/retry/polling; giữ bubble, STT và TTS hiện tại. |
| `HomeFragment.java` | Hiện tên phiên thật thay lời chào cố định. |
| `fragment_login.xml`, `fragment_register.xml`, `fragment_speaking.xml`, `fragment_chat.xml` | Điều chỉnh nhãn email, ẩn username, thêm ID trạng thái; giữ thiết kế. |
| `LiveUiIntegrationTest.java` | Test opt-in với backend/MySQL/Gemini thật; tài khoản thử mới; báo cáo không chứa bí mật. |
| `ChatRecoveryTest.java` | Sáu test HTTP fixture cho các tình huống khó tái hiện an toàn bằng Gemini thật. |
| Tài liệu 14 và mục lục Contract | Ghi phạm vi và bằng chứng hiện tại. |

Các sửa manifest/level/lint ở audit 13 và toàn bộ backend/Docs đã phục hồi trước đó được giữ nguyên. Git vẫn có nhiều file untracked; không commit/push/merge/reset/clean.

## 6. Bước tiếp theo và trình tự demo an toàn

1. Chạy backend từ `backend` với Java 21 bằng lệnh đã có; giữ port 8081. Không import lại SQL.
2. Cài APK build mặc định, chọn Luyện nói → Đăng nhập. Đăng ký một tài khoản thử nếu cần; không dùng tài khoản admin để demo học tập.
3. Chọn mức A1–B2 và một chủ đề server; bắt đầu, nhập một câu tiếng Anh, bấm Gửi một lần.
4. Nếu báo dịch vụ chưa sẵn sàng, giữ nguyên bài và bấm Thử lại sau; không tạo UUID/tài khoản/bài mới liên tục. Hiện không cần gửi hoặc đổi mật khẩu/key để giải quyết HTTP 503 này.
5. Chỉ khi đã thấy câu Gemini thật trên UI và SQL đối chiếu đúng mới đánh dấu critical path đạt, rồi nối finish/review/retry, từ của tôi, trạng thái ôn và lịch sử server.

### Bản đồ nối tiếp đã đối chiếu, chưa triển khai

| Điểm nối | Code giao diện và dữ liệu cần dùng |
| --- | --- |
| Kết thúc | `ChatFragment.finishSession()` gọi `LearningApi.finish(id,false)`; đóng bài theo server, không lưu một bản lịch sử local thay thế. `MainActivity` truyền ID Long vào màn kết quả. |
| Nhận xét | `SpeakingResultFragment`: GET review, polling theo `reviewStatus/pollAfterSeconds`, retry chỉ khi `canRetry`; bind summary/strengths/weaknesses/recommendedLevel/levelFeedback vào các ô XML hiện có. Grammar lấy messageId/originalContent/correctedContent/feedback, không tự dựng câu mẫu. |
| Lưu từ | Checkbox mang ID `suggestedWords[].id`; POST `saved-words/from-suggestions`. Đọc `created/existing` và tải lại trạng thái `savedWordId`; không tạo deck trong database. |
| Danh sách từ | `FlashcardsFragment`: GET `saved-words` theo trang; danh sách đồng bộ phải được phân biệt với các bộ thẻ offline đang có. Không cần endpoint deck mới. |
| Ôn thẻ | Giữ layout/lật thẻ hiện tại trong `StudyFragment`; thẻ server cần giữ ID. PATCH `/saved-words/{id}/review` với LEARNING/MASTERED; chỉ chuyển thẻ sau khi server xác nhận, lỗi thì giữ nguyên thẻ. |
| Lịch sử | `SpeakingHistoryFragment`: GET `conversations`; OPEN mở Chat bằng ID, CLOSED mở review và transcript server. Không nối nút xóa local vào dữ liệu server vì contract không có DELETE. |

Việc triển khai bảng này phụ thuộc nghiệm thu chat thật theo yêu cầu gốc, hoặc người dùng xác nhận cho phép nối tiếp bằng fixture khi dịch vụ AI còn bị chặn. Fixture PASS không được thay nhãn E2E thật.

**Chưa trình bày app này như một demo đầy đủ vòng đời.** Hiện có thể trình bày phần đăng nhập/chủ đề/tạo bài/lưu câu và phục hồi lỗi; không trình bày feedback, flashcard hoặc lịch sử local như kết quả AI/server thật. Google login/forgot-reset/profile-sync vẫn chưa nối ở UI hiện tại.

APK cuối: `frontend/app/build/outputs/apk/debug/app-debug.apk`. `BuildConfig.API_BASE_URL` đã kiểm tra là `http://10.0.2.2:8081/`. Bản sao các file đã sửa nằm trong `.recovery/20261029/ui-integration-after-*.zip`; báo cáo máy đọc được tại `backend/build/ui-integration/final-summary.json`.
