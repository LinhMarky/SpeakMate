# 11 — Kiểm chứng môi trường thực tế

> **Cập nhật sau pull 29/10/2026:** các kết quả Android E2E PASS dưới đây thuộc bản FE trước pull. Audit bản hiện tại: backend HTTP/MySQL/Gemini PASS, Android chưa nối 20 API người học và chưa sẵn sàng demo trọn vòng. Kết quả, lỗi 500 lần đầu, sửa nhỏ và bằng chứng mới tại [file 13](13-final-integration-audit.md).

Cập nhật **29/10/2026**. Hoàn thiện và kiểm chứng phần đã có; đợt mới sửa ba lỗi FE trong luồng hiện tại, không thêm tính năng hoặc đổi API/schema. Những thay đổi có sẵn trong Git được giữ nguyên, kể cả việc xóa `frontend.zip` và các file chưa được track. Không commit/push.

Người phát triển đã chốt backend dùng **8081**, emulator dùng **`http://10.0.2.2:8081/`**. Kết quả chạy cổng 18081 dưới đây là **lịch sử của đợt trước**, không phải bằng chứng đã kiểm chứng 8081. Các lệnh ở mục 4 dùng cổng hiện tại; chỉ đổi cổng khi xác nhận 8081 không khả dụng.

## 0. Kết quả mới nhất — 29/10/2026

**Vòng học qua API và giao diện Android thật đã PASS**, sử dụng MySQL local và Gemini thật qua backend 8081. Chưa nghiệm thu mic, nhận dạng giọng nói, chất lượng TTS, Google login và email thật; còn cần kiểm tra như mục 4–5. Những dòng “key trống”, “1045” và “E2E bị chặn” trong các mục lịch sử dưới đây không còn là trạng thái MySQL/Gemini hiện tại.

### 29/10 — chẩn đoán đăng nhập từ ảnh màn hình

- Ảnh app hiển thị `Email hoặc mật khẩu không đúng` khi người phát triển thử một email chưa đăng ký. Đối chiếu **chỉ đọc MySQL `speakmate_v3`** theo đúng email trong ảnh: `TARGET_ACCOUNT_EXISTS=false`.
- Đường gọi: `LoginActivity.loginWithPassword` → `AuthApi.login` → `AuthController.login` → `AuthTransactionService.login`. User không tồn tại nên Service trả 401 `INVALID_CREDENTIALS` với thông báo chung; không phân biệt email không có và sai mật khẩu ở response.
- Diagnostic `backend/build/integration-validation/InspectLoginAccount.java` dùng prepared SELECT, không đọc hash/token, không sửa dữ liệu. Không tạo tài khoản, đổi mật khẩu hoặc nâng quyền từ thông tin trên ảnh.
- Bước tiếp theo: bấm **Tạo tài khoản bằng email**, dùng email hộp thư mình sở hữu và mật khẩu 8–64 ký tự, đăng ký rồi đăng nhập. Sau đó mới thử Quên mật khẩu với tài khoản vừa đăng ký; nếu là Google-only thì tiếp tục dùng nút Google. Ngưỡng 8 ký tự áp dụng khi tạo/đổi mật khẩu, không được giải thích thành nguyên nhân riêng của lỗi login trong ảnh.
- Không sửa mã ứng dụng nên không chạy lại build/test. Source và các thay đổi Git đang có được giữ nguyên; diagnostic Java đã compile và query thật thành công.

### 29/10, 10:38 — cấu hình và kiểm SMTP thật

- Người phát triển xác nhận Gmail gửi mã; đã điền host/port/username/from/password trong `backend/application-local.properties`, giữ nguyên cấu hình DB/AI/JWT/Google. Password chỉ nằm trong file local được ignore; không đưa giá trị vào tài liệu hoặc mã nguồn. Quét 218 file source không phát hiện secret theo quy tắc kiểm; local không được track.
- Kiểm kết nối bằng `CheckSmtpConnection` trong `backend/build/smtp-validation/`, dùng **JavaMailSenderImpl của chính dependency JAR hiện có** và cấu hình properties của backend. `testConnection()` PASS thật với Gmail: kết nối, STARTTLS bắt buộc và xác thực SMTP; exit 0. Không gọi `send`, không gửi email hoặc mã reset. Không thêm dependency hoặc mã sản phẩm.
- Lần restart nền bằng `Start-Process` và `--spring.mail.test-connection=true` thất bại: `MailSenderValidatorAutoConfiguration` → `MailConnectException` → `SocketException: Permission denied: getsockopt`. Đây là lỗi quyền mạng của lần chạy, không phải mật khẩu bị Gmail từ chối; lần test Java trực tiếp phía trên đã xác thực được.
- Chạy lại **Java trực tiếp với quyền mạng**, giữ 8081 và thêm cờ kiểm kết nối SMTP ở lần chạy này. Spring Boot khởi động thành công sau kiểm SMTP; Hikari/schema validate PASS, Tomcat 8081, PID **16728**. Cờ kiểm startup không được thêm vào file cấu hình và không bắt buộc khi người phát triển chạy `bootRun` bình thường.
- Bằng chứng: `backend/build/smtp-validation/result.json`, `CheckSmtpConnection.java`, `backend-direct.log`. Log của lần chạy nền lỗi được giữ riêng ở `backend.log`; không xóa hoặc biến lần thất bại thành PASS.
- **Chưa nghiệm thu nhận thư và đổi mật khẩu trọn vòng.** Người phát triển cần tự bấm Quên mật khẩu bằng tài khoản email/mật khẩu, xác nhận email đến, nhập mã trên app và đăng nhập bằng mật khẩu mới. Google-only không có mật khẩu SpeakMate để reset. HTTP 202 chỉ xác nhận đã nhận yêu cầu; chưa chứng minh email đến hộp thư.
- Đợt này chỉ thay local và tài liệu; không sửa code BE/Android hoặc schema, không chạy lại bộ unit/MySQL/Android build cũ. Đã biên dịch và chạy diagnostic Java, xác thực SMTP thật, khởi động backend thật với mail validator. Các thay đổi frontend/Gradle có sẵn của người phát triển được giữ nguyên.

Trạng thái “SMTP thiếu cấu hình” trong các mục lịch sử bên dưới đã được bước này thay thế. Xem [file 12, mục 9](12-bang-noi-api-va-ban-demo.md) cho thao tác nghiệm thu cuối.

### 29/10, 09:20 — cấu hình Google Web client

- Người phát triển đã tạo Web OAuth client trên Google Cloud. Đã điền cùng Web client ID vào `backend/application-local.properties` và `frontend/local.properties`; chỉ thay hai trường Google tương ứng, giữ nguyên MySQL/Gemini/JWT/URL. Hai file vẫn ignore, không được track. Không lưu hoặc sử dụng client secret.
- `frontend`: `signingReport assembleDebug --no-daemon --console=plain` cùng tham số JDK hiện có **SUCCESSFUL 17 giây**, 6 task chạy / 29 up-to-date. APK chứa đúng Web client ID local, URL emulator 8081, không chứa URL fixture hoặc mẫu OAuth client secret. Quét 218 file source không phát hiện secret theo quy tắc kiểm.
- Debug SHA-1 đã lấy từ Gradle signingReport; giá trị để tạo Android client và thao tác tiếp theo nằm ở [file 12, mục 8](12-bang-noi-api-va-ban-demo.md). Khóa ký khác cần Android client tương ứng.
- Đã xác minh và dừng đúng backend cũ do tác vụ kiểm thử khởi động, chạy lại JAR hiện có để nạp local: PID **18044**, **8081**. Hikari/validate/startup thành công. Một request JSON với chuỗi token cố ý không hợp lệ trả **401 INVALID_GOOGLE_TOKEN**, chứng minh nhánh xác minh đã vượt kiểm tra thiếu cấu hình. Đây **không phải** đăng nhập Google thật, không chứng minh Client ID hợp lệ với Google.
- Probe đầu bị lệnh Windows làm mất dấu nháy JSON nên trả 400 INVALID_JSON trước verifier; sửa cách tạo JSON trong lệnh kiểm thử rồi mới nhận 401 như trên. Không sửa mã ứng dụng để xử lý lỗi của lệnh thử.
- **Chưa xác nhận Google login thật:** cần tạo Android OAuth client đúng package/SHA-1, thêm tài khoản test khi cần, cài APK mới trên thiết bị có Google Play services và đăng nhập/chọn tài khoản. SMTP và âm thanh vẫn chưa nghiệm thu. Những dòng thiếu Web client ID bên dưới là trạng thái trước bước cấu hình này.

### 29/10 — hoàn thiện bản demo sau sửa FE

- Sửa `SpeakingFragment`: giữ ID bài tạo thành công khi app xuống nền; mở bài khi quay lại, giữ UUID để khôi phục nếu mất phản hồi. Không gửi lại create khi đã biết ID.
- Sửa `FlashcardsFragment`: chỉ phản hồi của lượt tải mới nhất được cập nhật danh sách/bộ lọc. Sửa `HomeFragment` và layout: loading/lỗi/tải lại thống kê, giữ phần số liệu đã tải thành công; bỏ nội dung giới thiệu chưa đúng với luồng P0.
- `LearningFlowInstrumentedTest`: **8 test, 0 failures/errors/skipped**, 51,430 giây; Gradle `connectedDebugAndroidTest` SUCCESSFUL 1 phút 21 giây. Gồm ba regression mới và năm test luồng cũ: mất phản hồi, rời màn hình, đổi token đồng thời, form tài khoản và lưu UUID. Đây là HTTP fixture trên Android, **không dùng MySQL/Gemini/Google/SMTP thật**. XML đã lưu tại `backend/build/defense-readiness/fixture-results/`.
- Sau lần ngắt công việc, backend/emulator cũ không còn chạy; đã kiểm trạng thái trước khi khởi động lại backend từ `backend` bằng JAR hiện có trên **8081**, PID 13816. Hikari kết nối và Hibernate validate thành công; JDBC `SELECT 1` và kiểm đủ 7 bảng PASS. Không sửa cấu hình, không reset/import lại DB.
- Chạy lại **một** `LiveLearningFlowInstrumentedTest` sau sửa: **1 test, 0 failures/errors/skipped**, 29,986 giây; Gradle SUCCESSFUL 1 phút 7 giây. Đăng ký bằng API thật → đăng nhập UI → A1 chọn B2/cà phê → 3 tin/3 lời AI → kết thúc/nhận xét → lưu từ → lật thẻ/MASTERED → mở lịch sử → refresh → logout. **4 request Gemini thật, tất cả HTTP 200**, không thử lại GET đối chiếu (`verificationReadRetries=0`). Không chạy prompt kiểm provider riêng.
- Dữ liệu thử mới: `userId=8`, `conversationId=6`; 6 tin, 1 correction, 3 gợi ý, thẻ đã chọn MASTERED. JDBC chỉ đọc đối chiếu **đúng toàn bộ** transcript/requestId, review/correction/gợi ý/thẻ, không trùng; profile A1, refresh bị thu hồi sau logout, thời gian khớp đến nanosecond. Giữ nguyên dữ liệu khác.
- Nhận xét khớp tình huống gọi latte/ít đường, hỏi gợi ý và hóa đơn. Correction trỏ `messageId=33`, giữ toàn bộ câu và chỉ đổi “Yesterday I go” thành “Yesterday I went”. AI gợi ý A2 và luyện quá khứ; hồ sơ vẫn A1. Đây là đối chiếu nội dung lần thử này, không phải chứng nhận mọi nhận xét AI đều chính xác hoặc trình độ CEFR đã được kiểm định.
- Bằng chứng: `backend/build/defense-readiness/native-live-report.json`, `native-results.xml`, `native-live-report.json.sql-check.txt`; log backend mới trong cùng thư mục. Không chứa password/token/key trong bằng chứng JSON.
- MySQL/Gemini và URL Android vẫn đúng cấu hình; Google Web client ID/SMTP còn trống. Quét source không thấy secret local/mẫu API key theo quy tắc kiểm, hai file local được ignore và không track. Hướng dẫn cấu hình đã bổ sung ở [luồng tài khoản, mục 5–6](../SpeakMate-luong-tai-khoan.md).
- Timeout/AI JSON sai/chống gửi trùng/ownership ở BE giữ bằng chứng bộ test MySQL/API trước (AI được mock), không tuyên bố vừa gây lỗi provider thật. Rời màn hình và tạo bài ở nền được kiểm UI fixture mới. Google, SMTP, mic/TTS và CRUD admin qua giao diện thật vẫn cần nghiệm thu thủ công; `audioVerified=false`.
- Build cuối `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` SUCCESSFUL 36 giây; 10 task chạy / 68 up-to-date, unit mặc định 1/1 PASS. Lint 0 lỗi / 183 cảnh báo (gồm chuỗi giao diện viết trực tiếp mới; trước đó 179), chưa dọn cảnh báo ngoài phạm vi. APK có URL 8081, không có fixture URL/key local. Liên kết của bốn tài liệu và cú pháp 9 block PowerShell PASS; `git diff --check` PASS.

Kết quả đóng gói/lint cuối và danh sách file thay đổi ở [file 12, mục 7](12-bang-noi-api-va-ban-demo.md). Các ghi chú “ba lỗi FE hoãn” và “không sửa mã sản phẩm” bên dưới mô tả đợt 28/10, đã được đợt 29/10 thay thế.

### 28/10 — kiểm app Android thật sau khi chốt nối API

- Rà đủ **24 API IMPLEMENTED**: mỗi API có nơi gọi trong Android hoặc trang web admin. [Bảng nối API và bản demo](12-bang-noi-api-va-ban-demo.md) ghi rõ màn hình/hàm sử dụng. Không bổ sung API P1 và không sửa ba lỗi FE mà người dùng yêu cầu hoãn.
- Thêm `LiveLearningFlowInstrumentedTest` (androidTest, opt-in): tạo tài khoản thử mới, bấm UI đăng nhập → chọn B2/chủ đề cà phê → gửi 3 tin/nhận Gemini → kết thúc → hiện báo cáo → lưu từ → lật/đánh dấu thẻ → mở lịch sử → đổi refresh token → bấm đăng xuất. Dùng URL thật `http://10.0.2.2:8081/`, không có HTTP fixture.
- Lần xác nhận cuối **PASS: 1 test, 0 failures/errors/skipped**, 91,176 giây. Task `connectedDebugAndroidTest` exit 0, `BUILD SUCCESSFUL` 2 phút 48 giây, 5 task chạy / 61 up-to-date. Artifact: `frontend/app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/Pixel_8(AVD) - 15/live-integration-report.json`; bản lưu trong `backend/build/integration-validation/native-live-report.json`.
- Bằng chứng cuối: `userId=7`, `conversationId=5`; 6 tin, 1 correction, 3 từ gợi ý và thẻ MASTERED. JDBC chỉ đọc đối chiếu đúng transcript/requestId, report/correction/từ/thẻ; không trùng, profile vẫn A1; refresh token đã bị thu hồi sau logout. Correction giữ đủ câu gốc và chỉ đổi `go → went` trong tin có “Yesterday”.
- Test có khả năng thử lại **một GET đối chiếu** khi lỗi mạng và ghi bộ đếm riêng; không tự gửi lại thao tác ghi hoặc gọi Gemini. Lần PASS ghi `verificationReadRetries=0`. Các thao tác học trong app được bấm trên giao diện; refresh token được kiểm trực tiếp qua `AuthApi` và lưu bằng `SessionManager`.
- Giữ bằng chứng hai lần trước: lần 1 qua hết học/lịch sử/refresh nhưng test dùng `scrollTo` cho nút logout trong layout không cuộn; chỉ sửa test thành click. Lần 2 đủ 3 reply + review nhưng GET đối chiếu review gặp `NETWORK_ERROR`, backend kiểm lại vẫn trả 401 đúng ở `/auth/me`; chưa xác định nguyên nhân transport. Không sửa `ApiClient` để che lỗi. Lần 3 xác nhận toàn bộ thành công như trên.
- Đợt Android này có **12 request Gemini thật** qua 3 lần, tất cả upstream 200. Cộng 8 request của đợt API trước là 20 request trong chuỗi kiểm chứng hiện có; không gửi thêm prompt kiểm provider riêng.
- Web admin `index.html` và `admin.js` trả 200, nội dung khớp source; GET `/api/v1/admin/topics` khi chưa đăng nhập trả 401. `node --check` admin.js PASS. Chưa kiểm đăng nhập/thao tác admin thật với tài khoản ADMIN; các endpoint có bằng chứng MySQL test ở đợt trước.
- Google và SMTP vẫn thiếu cấu hình. Test chạy text trên emulator không xác nhận âm thanh; `audioVerified=false`. Ba lỗi FE hoãn vẫn còn, không coi toàn bộ app đã hết lỗi.

Mã sản phẩm/API/schema không thay trong đợt chốt nối này. Thay đổi nằm ở bài test opt-in, báo cáo và bảng bàn giao. Kết quả build APK/unit/lint sau test được ghi tại file 12.

Build cuối `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` PASS (25 giây; 7 task chạy / 71 up-to-date). Lint 0 lỗi, 179 cảnh báo; unit mặc định 1 test còn hợp lệ trong cache. Đã compile lại test sau khi đổi riêng cách điền mật khẩu thử để tránh action log ghi giá trị; không gọi thêm Gemini. Quét 218 file nguồn không phát hiện secret theo quy tắc kiểm, APK trỏ 8081 và không chứa key local trong DEX. Emulator thử read-only 5564 đã nhận lệnh đóng/OK; backend 8081 giữ chạy.

### Cấu hình và database đang dùng

- Người dùng đã lưu mật khẩu MySQL và Gemini key; cả hai đã được kiểm chứng bằng kết nối/request thật. Giữ nguyên model `gemini-3.5-flash-lite`, provider, thông tin xác thực và JWT.
- Database `speakmate` cũ có **8 bảng của thiết kế cũ**, tất cả rỗng lúc kiểm tra, không khớp Entity hiện tại. Sau khi người dùng cho phép chọn phương án phù hợp, đã tạo riêng **`speakmate_v3`** từ `speakmate.sql` hiện tại: 7 bảng và 5 chủ đề từ `Docs/seeds/V1__topics.sql`. Giữ nguyên database cũ; không DROP/reset hoặc cố áp migration auth lên thiết kế không tương thích.
- `backend/application-local.properties` chỉ đổi tên database trong URL sang `speakmate_v3`; xác nhận các dòng cấu hình khác giữ nguyên. Root `speakmate.sql` vẫn là script cài mới, không đổi tên mặc định trong tài liệu thiết kế chung.
- Backend chạy **8081**, Hikari kết nối được và Hibernate validate schema thành công. Android local/Gradle/APK dùng **`http://10.0.2.2:8081/`**; APK không chứa URL fixture 18080.
- Hai file local được Git ignore và không được track. Quét 216 file nguồn tracked/non-ignored không thấy mẫu Google API key/private key hoặc giá trị secret local theo quy tắc kiểm tra; không xuất secret ra báo cáo.
- Google Web client ID ở BE/Android và các mục SMTP vẫn chưa cấu hình. Đây là phần chưa xác minh, không cản đăng nhập mật khẩu và vòng học đã thử.

### Vòng học thật đã thực hiện

Lần xác nhận sau sửa prompt dùng tài khoản thử mới (user 3; tài khoản kiểm tra quyền user 4), `conversationId=2`, `savedWordId=2`. Mật khẩu ngẫu nhiên và access token chỉ tồn tại trong bộ nhớ script, không ghi vào bằng chứng. Lần thử đầu là conversation 1; giữ lại dữ liệu và bằng chứng, không sửa kết quả cũ.

| Bước | Kết quả thực tế |
| --- | --- |
| Đăng ký và đăng nhập | 201/200; `/auth/me` trả đúng USER/A1. Gọi không có token trả 401 |
| Chọn chủ đề và level | Chọn tình huống cà phê, B2; snapshot hồ sơ vẫn A1 |
| Bắt đầu bài | 201, transcript rỗng. Gửi lại createRequestId trả 200 và cùng conversationId |
| Gửi 3 tin và nhận Gemini | 3 HTTP 200; lời đáp liên quan gọi latte ít đường, gợi ý đồ uống, giá và hóa đơn. Khoảng 1,42 / 1,30 / 1,01 giây mỗi lượt trong lần xác nhận |
| Tự lưu hội thoại | GET trả đúng 6 tin USER/AI theo thứ tự, giữ nguyên câu gốc, không đưa correction vào chat. JDBC chỉ đọc đối chiếu chính xác content/sender/requestId trong MySQL |
| Kết thúc và nhận xét | 200, `COMPLETED`, khoảng 2,88 giây. JSON được parse và qua `ReviewValidator`; review/correction/từ gợi ý đối chiếu đúng với MySQL |
| Lưu từ | Lưu gợi ý thành công; lưu lại trả thẻ có sẵn, không nhân bản |
| Ôn flashcard qua API | PATCH `LEARNING` rồi `MASTERED` thành công. GET và SQL giữ đúng trạng thái, thời gian ôn. Script không thao tác lật thẻ trên màn hình |
| Mở lịch sử | Danh sách có bài CLOSED; mở lại review giữ nguyên nhận xét và correction. Level hồ sơ vẫn A1 |
| Gửi tin trùng | Cùng requestId trả lại cùng AI message, không thêm hàng và không gọi Gemini thêm; replay khoảng 0,03 giây |
| Dữ liệu người khác | GET/send/finish/review/retry và lưu/ôn từ của user khác đều 404. Lịch sử và từ của user 4 rỗng |

JDBC đối chiếu lần cuối: **6 tin chính xác, 1 correction, 2 từ gợi ý, 1 thẻ MASTERED**, report đúng, processing token đã xóa, không trùng tin/thẻ. Sai số thời gian ôn so với response Java là 100 nano giây, nằm trong độ chính xác `DATETIME(6)`.

Tổng cộng **8 request Gemini thật**, đều có upstream HTTP 200: 3 reply + 1 review ở lần đầu, rồi 3 reply + 1 review để xác nhận sửa lỗi và hoàn thành phần kiểm tra còn lại. Không gọi trực tiếp provider ngoài integration có sẵn, không đổi model, không tự retry liên tục.

### Lỗi quan sát được và sửa tối thiểu

1. **Schema cũ không khớp code:** giải quyết bằng database mới đã được chấp thuận như trên, không sửa Entity hoặc quan hệ dữ liệu để chạy theo thiết kế cũ. MySQL 1045 trước đó hết sau khi người dùng lưu đúng cấu hình. `503 AI_NOT_CONFIGURED` trước đó do key trống; request thật hiện đã tới Gemini và nhận 200.
2. **Phép so sánh trong script kiểm chứng quá chặt:** lần đầu PATCH thẻ trả Java Instant có nano giây, GET từ MySQL chỉ có micro giây; so toàn bộ JSON bằng nhau làm script dừng trước bước lịch sử. SQL chứng minh trạng thái MASTERED đã lưu đúng, chênh lệch chỉ 300 nano giây. Chỉ sửa helper trong thư mục build: so trường nghiệp vụ chính xác, thời gian trong dung sai 1 micro giây; không đổi code timestamp của sản phẩm.
3. **AI trả correction là một đoạn trích:** lần đầu sửa đúng `go → went` nhưng bỏ `Thank you.` và `What do you recommend?` trong cùng tin. Bổ sung 4 dòng ở `PromptBuilder.review`: giữ toàn bộ tin nhắn/các câu đúng và tránh kết luận đã lên/vượt cấp độ từ một bài ngắn. Không đổi DTO, validator, API hoặc cách lưu dữ liệu.

Đã đọc và so nội dung thật của lần xác nhận: message 9 có câu gốc `Thank you. Yesterday I go to another cafe, but their coffee was too sweet for me. What do you recommend?`; correction giữ nguyên toàn bộ câu, chỉ đổi **go → went**. Giải thích dùng “Yesterday” làm căn cứ thì quá khứ; hai từ gợi ý “take away”, “receipt” có trong hội thoại và nghĩa phù hợp ngữ cảnh. Ưu/nhược điểm viện dẫn đúng lời USER; profile không tự tăng theo gợi ý A2.

Đây là kiểm tra nội dung của **một tình huống**, không chứng nhận AI luôn đúng hoặc kết luận CEFR của người học. Lời nhận xét vẫn có cách diễn đạt đánh giá như “tự tin”, “tốt hơn mức A1”; không dùng đó làm bằng chứng phát âm hay tiến bộ dài hạn. Prompt giảm vấn đề quan sát được, không bảo đảm mọi kết quả sau này đều giữ đủ câu.

### Build và các ca lỗi

| Lệnh / kiểm tra | Kết quả và phạm vi |
| --- | --- |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-MySql.ps1` tại backend | Exit 0; `build mysqlTest` SUCCESSFUL 1 phút 3 giây, 6 task chạy / 2 up-to-date. Chạy lại **77 unit + 27 MySQL/API**, 0 lỗi/bỏ qua. MySQL tạm 13317 độc lập; AI/Google/email trong test được mock |
| `assembleDebug testDebugUnitTest lintDebug` tại frontend, với các JDK ở lệnh bên dưới | Exit 0, SUCCESSFUL 34 giây; 2 task chạy / 46 up-to-date. Assemble/unit/lint report được tái sử dụng, không phải chạy mới toàn bộ test. XML gần nhất: 1 unit test mặc định kiểm phép cộng; lint 0 lỗi, 179 cảnh báo có sẵn |
| Timeout / JSON sai / reply lỗi | Test hiện có chạy lại PASS: hủy future khi timeout, trả AI_TIMEOUT, giữ USER để retry đúng UUID; review sai không lưu dở. Không cố ép Gemini thật lỗi hoặc tiêu quota để gây timeout |
| Pending/trùng/rời màn | Test MySQL pending/late reply chạy lại PASS. Test Android rời màn và phục hồi có bằng chứng fixture ở mục 3; chưa lặp lại bằng native UI với Gemini thật |
| Android instrumentation | Kết quả gần nhất chạy riêng full learning flow: 1 test PASS với HTTP fixture; không chạy lại instrumentation sau sửa prompt BE. Không suy ra mic/TTS thật từ fixture |

### File thay đổi và bước tiếp theo

- Mã sản phẩm: `backend/src/main/java/vn/tailinh/speakmate/service/ai/PromptBuilder.java` — thêm đúng 4 dòng hướng dẫn review.
- Cấu hình local được ignore: `backend/application-local.properties` — chỉ tên DB trong URL. Không sửa Android trong đợt này.
- Tài liệu: báo cáo này. Helper/bằng chứng trong `backend/build/integration-validation` được ignore, không phải thành phần runtime.
- Backend kiểm chứng đang chạy 8081 (PID ghi trong `backend/build/integration-validation/live-backend.pid`); kiểm tra listener trước khi tự chạy thêm `bootRun`.
- **Bước tiếp theo:** cài APK hiện tại, đăng ký tài khoản test riêng trên app và làm mục 4: nói → nghe AI → kết thúc → đọc nhận xét → lưu/lật/ôn thẻ → mở lịch sử; thử ra nền khi chờ reply rồi quay lại. Tài khoản ngẫu nhiên của script không có mật khẩu lưu để dùng trên UI. Sau đó cấu hình/kiểm Google và SMTP theo mục 5. Không cần tạo Gemini key mới hoặc nhập lại database.

Bằng chứng mới: `backend/build/integration-validation/live-api-validation.json` (`PASS_API_FLOW`), `live-api-validation.json.sql-check.txt`, `live-api-validation-first.json`, `live-backend-first.log`, `live-backend-final.log`. Log chẩn đoán provider chỉ bật `jdk.httpclient.HttpClient.log=requests` (URI/method/status, không headers/body); key được gửi bằng header. Không bật log headers hoặc nội dung auth.

## 0.1. Lịch sử kiểm chứng cổng 8081 — trước ngày 28/10

Đợt thống nhất cổng chỉ thay cổng/URL, giữ nguyên thông tin DB, JWT, model Gemini, OAuth và SMTP. Bảng dưới ghi nhận đợt đã hoàn tất trước; kết quả kiểm tra mới nhất nằm ở phần ngày 27/10 ngay sau bảng. Không khởi động backend với DB nhóm khi thông tin đăng nhập còn bị từ chối.

| Kiểm tra | Kết quả đợt thống nhất cổng trước đó |
| --- | --- |
| Backend cấu hình và JAR | `application.properties`, file local và file mẫu dùng `${PORT:8081}`; JAR đã đóng gói đúng giá trị này |
| Android URL | Gradle fallback và `frontend/local.properties` dùng `http://10.0.2.2:8081/`, để Retrofit tự nối `api/v1/` |
| Build/test backend | PASS: `Test-MySql.ps1` chạy `build mysqlTest`, 77 unit + 27 MySQL/API test; dùng DB tạm 13317, các dịch vụ bên ngoài vẫn mock |
| Test UI Android | PASS: 8 test, 0 lỗi/bỏ qua trên Pixel_8/API35; gồm mất phản hồi, rời màn khi pending, lưu/ôn từ và mở lại lịch sử. HTTP fixture nằm trong emulator, không gọi Gemini/MySQL nhóm |
| Build Android sau test | PASS: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest (1 test), lintDebug (0 lỗi, 179 cảnh báo cũ); đã build lại APK mặc định và xác nhận BuildConfig dùng `http://10.0.2.2:8081/`. Emulator thử đã đóng |
| MySQL nhóm | Thử JDBC chỉ đọc một lần với cấu hình hiện có: SQLState `28000`, mã `1045`; đã dừng kiểm tra live, không thử mật khẩu khác hoặc sửa xác thực server |
| Gemini | `app.ai.api-key` còn trống; giữ nguyên model/provider. Không gửi request Gemini thật, chưa xác minh reply/review hoặc lưu kết quả AI thật |
| Git/secrets | Hai file local được ignore và không được track. Quét 215 file nguồn tracked/non-ignored không thấy giá trị secret local trùng trong source hoặc mẫu Google API key/private key |
| E2E thật trên 8081 | BLOCKED bởi MySQL và Gemini. Kết quả mock/fixture và đợt HTTP 18081 trước không thay thế bước này |

Các thay đổi ở đợt thống nhất cổng: `backend/src/main/resources/application.properties`, `backend/application-local.properties.example`, hai file local (chỉ cổng/URL), `frontend/app/build.gradle.kts`, các URL server OpenAPI và hướng dẫn liên quan. Không sửa Controller/Service/Repository/schema hay thêm test giả cho Gemini thật.

### Kiểm tra lại ngày 27/10/2026

Đã đọc lại Git status/diff và giữ nguyên toàn bộ công việc có sẵn. Lần kiểm tra này chỉ cập nhật báo cáo; không sửa mã ứng dụng, cấu hình local hoặc dữ liệu MySQL nhóm.

| Kiểm tra | Kết quả thực tế |
| --- | --- |
| Cấu hình đã lưu trên ổ đĩa | `backend/application-local.properties` vẫn trống `spring.datasource.password` và `app.ai.api-key`. Lần kiểm tra các biến `DB_PASSWORD`, `GEMINI_API_KEY`, `SPRING_DATASOURCE_PASSWORD`, `APP_AI_API_KEY` trước đó đều chưa có ở Process/User/Machine. Người dùng cần hướng dẫn tạo Gemini key; đã bổ sung các bước ở mục 4. Lần đọc cuối sau test UI vẫn chưa có hai giá trị cần thiết |
| Cổng và URL | 8081 chưa có listener; backend/local dùng 8081, Android local dùng `http://10.0.2.2:8081/`. Chưa khởi động backend do cấu hình DB vẫn bị từ chối |
| MySQL thật | Lần thử JDBC chỉ đọc gần nhất trả SQLState `28000`, mã `1045`. Cấu hình không đổi nên không thử lại cùng thông tin bị từ chối; không chạy lệnh ghi hoặc sửa xác thực MySQL |
| Gemini thật | Chưa gửi request nào vì key vẫn trống. `GeminiAiClient.generate` trả `503 AI_NOT_CONFIGURED` trước khi gọi mạng khi thiếu key; không thay model/provider |
| Backend build/test | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-MySql.ps1` tại `backend`: exit 0, `BUILD SUCCESSFUL`. Đã chạy lại 27 MySQL/API test trên DB tạm 13317, không lỗi/bỏ qua. Task 77 unit test và đóng gói JAR được Gradle báo `UP-TO-DATE`, không phải 77 test mới chạy trong đợt này |
| Android unit/lint | XML unit gần nhất: 1 test, 0 lỗi/bỏ qua; test này chỉ kiểm phép cộng. Lint gần nhất: 0 lỗi, 179 cảnh báo. Không sửa mã Android trong đợt kiểm tra lại |
| Test UI còn dang dở | Đã chạy lại riêng `LearningFlowInstrumentedTest#loginChatLostResponseReviewSaveStudyAndHistoryUseServerData`: PASS, XML xác nhận 1 test, 0 failures/errors/skipped. `connectedDebugAndroidTest` exit 0, 2 phút 53 giây; 6 task chạy, 60 `UP-TO-DATE`. Luồng đăng nhập → 3 lượt chat → phục hồi mất response/recreate → feedback → lưu/ôn từ → lịch sử dùng HTTP fixture trong emulator. Đợt 8 test trước từng bị gián đoạn; lần này chỉ xác nhận test được chạy riêng |
| Khôi phục APK 8081 | PASS sau test: `assembleDebug` exit 0, 5 task chạy, 29 `UP-TO-DATE`. DEX trong APK có `http://10.0.2.2:8081/`, không có URL fixture `http://127.0.0.1:18080/`. Đã gửi `emu kill` cho riêng emulator thử `emulator-5562`, nhận OK/exit 0. Lệnh xác nhận lại ADB trống bị người dùng từ chối; không thử lại, chưa xác nhận trạng thái thiết bị sau cùng |
| Secrets | Hai file local được ignore và không được track. Quét 216 file nguồn tracked/non-ignored: không phát hiện mẫu Google API key/private key hoặc giá trị secret local trùng trong nguồn theo các quy tắc đã kiểm tra; không in giá trị bí mật |
| E2E dùng dịch vụ thật | Chưa thực hiện: login → chọn topic/level → reply → kiểm tra lưu MySQL → finish/review → lưu từ → ôn flashcard → mở lịch sử. Chưa xác minh nội dung feedback/correction thật |

Các ca timeout, JSON lỗi, gửi trùng, rời màn hình và ownership có bằng chứng test ở mục 3; dịch vụ AI trong các test đó được mô phỏng. Chúng không chứng minh Gemini hoặc vòng học thật đã chạy thành công. Hướng dẫn tiếp tục ở mục 4 chỉ thực hiện sau khi hai giá trị trong file local đã được lưu và kết nối MySQL chỉ đọc thành công.

Bằng chứng test UI chạy riêng: `frontend/app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_8(AVD) - 15.xml` và `frontend/app/build/reports/androidTests/connected/debug/index.html`. Lệnh dùng task `connectedDebugAndroidTest`, URL fixture `-Pspeakmate.api.baseUrl=http://127.0.0.1:18080/` và bộ lọc `-Pandroid.testInstrumentationRunnerArguments.class=com.example.speakmate.LearningFlowInstrumentedTest#loginChatLostResponseReviewSaveStudyAndHistoryUseServerData`. Sau đó chạy `assembleDebug` bỏ hai tham số `-P` để trả APK về URL local 8081.

Hai lần build khôi phục APK đầu tiên thất bại vì Gradle chọn Java 25 JRE của VSCode thiếu `jlink.exe`. Lệnh thành công dưới đây dùng các JDK đầy đủ đã có trên máy, chạy tại `frontend`; không sửa cấu hình project/global:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat assembleDebug --no-daemon --console=plain `
    '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-21' `
    '-Dorg.gradle.java.installations.paths=C:\Users\PC\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2,C:\Program Files\Java\jdk-21'
```

### Những mục còn thiếu tại ngày 27/10 (lịch sử)

1. Trong `backend/application-local.properties`, kiểm tra `spring.datasource.url`, `spring.datasource.username` và tự điền đúng `spring.datasource.password`. Mật khẩu hiện trống và server từ chối. Không đổi plugin xác thực, bật truy cập rộng hoặc reset tài khoản MySQL chỉ để bỏ qua 1045. Dùng lệnh chỉ đọc ở mục 4 để kiểm tra; không gửi giá trị cho người khác.
2. Điền `app.ai.api-key` trong cùng file. Dòng trống trong file local ghi đè giá trị mặc định có `${GEMINI_API_KEY:}` ở `application.properties`; chỉ đặt biến phụ `GEMINI_API_KEY` có thể chưa đủ khi dòng local vẫn trống. Không đổi model để chữa lỗi thiếu key.
3. Khi hai cấu hình hợp lệ, khởi động backend 8081, cài APK có URL 8081 và thực hiện vòng học ở mục 4. Chưa thực hiện bước ghi E2E lên DB nhóm trong lần này; dùng tài khoản/schema test riêng khi nghiệm thu.
4. Google và SMTP vẫn cần cấu hình/thiết bị theo mục 5. Mic/TTS và chất lượng nhận xét cần người dùng nghe/đọc thực tế.

## 1. Kết quả đợt trước và phần đang bị chặn (lịch sử)

| Kiểm tra | Kết quả thực tế | Giới hạn |
| --- | --- | --- |
| Backend `build mysqlTest` | PASS: 77 unit + 27 MySQL/API test, không lỗi/bỏ qua | MySQL/JPA/JWT/BCrypt chạy thật; AI, Google và gửi email được thay tại ranh giới ngoài |
| Android native UI | PASS: 8 instrumentation test trên Pixel_8/API 35 | Gọi HTTP fixture trong androidTest; không gọi backend/Gemini thật |
| Android build/unit/lint | PASS: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest (1 test), lintDebug (0 lỗi) | Lint vẫn có 179 cảnh báo có sẵn; unit test mặc định chỉ kiểm phép cộng |
| MySQL nhóm ở localhost:3306 | FAIL: JDBC trả SQLState `28000`, mã MySQL `1045` | Dùng đúng cấu hình hiện tại, không đoán mật khẩu; không thực hiện lệnh ghi |
| JAR thật + HTTP + MySQL tạm | PASS cho auth, chủ đề, tạo/replay bài và ownership | Cổng 18081, DB `127.0.0.1:13317/speakmate_test`, không dùng dữ liệu DB nhóm |
| Gửi tin qua JAR thật | BLOCKED: `503 AI_NOT_CONFIGURED` | USER vẫn được lưu, GET cho thấy `pendingReply.status=RETRYABLE`; chưa có lời AI |
| Gemini reply/review thật | NOT VERIFIED | Key chưa được điền tại lần kiểm tra cuối; không gọi provider bằng key giả |
| Google/SMTP thật | NOT VERIFIED | Chưa có cấu hình cần thiết |
| Mic, chất lượng TTS và nội dung nhận xét thật | NOT VERIFIED | Cần điện thoại và Gemini thật; UI fixture không chứng minh chất lượng âm thanh/nội dung |

Không đánh dấu vòng học thật hoàn tất bằng kết quả của fixture. Hội thoại được tự lưu từng lượt ở backend, không có API/nút “Save conversation” riêng.

## 2. Cấu hình ghi nhận ở đợt trước, không ghi giá trị bí mật

`backend/application-local.properties` và `frontend/local.properties` đang được Git bỏ qua. Chỉ trạng thái có/trống được kiểm tra; không đưa key, token hoặc mật khẩu vào tài liệu/log kiểm chứng.

| Mục | Hiện trạng | Việc cần làm |
| --- | --- | --- |
| `spring.datasource.url`, `spring.datasource.username` | Có cấu hình; trỏ tới MySQL local/schema speakmate | Xác nhận đúng DB dùng để phát triển |
| `spring.datasource.password` | Trống; kết nối hiện tại bị từ chối 1045 | Tự điền mật khẩu đúng trên máy |
| `app.auth.jwt-secret` | Có; Base64 giải mã được ít nhất 32 byte | Giữ nguyên giá trị hiện có |
| `app.ai.api-key` | Trống | Tự điền Gemini API key trên backend |
| `app.ai.model` | Có: `gemini-3.5-flash-lite` | Cần xác nhận quyền/quota bằng request thật |
| `app.auth.google-client-id` | Trống | Điền Google **Web** client ID |
| `speakmate.google.webClientId` trên Android | Trống | Phải trùng Web client ID backend |
| SMTP host/port/username/password và `app.mail.from` | Chưa cấu hình | Điền dịch vụ gửi email thật, dùng STARTTLS |
| Android `speakmate.api.baseUrl` | Lần kiểm tra trước trỏ `http://10.0.2.2:8080/` | Mục tiêu hiện tại là `http://10.0.2.2:8081/`; cần build/cài lại APK |

Đã đối chiếu [trang model của Google](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite): model này hỗ trợ text và structured outputs. Điều đó không xác nhận API key/quota của tài khoản đã dùng được. Backend vẫn parse và kiểm tra nghiệp vụ bằng `ReviewValidator`, theo [hướng dẫn structured output](https://ai.google.dev/gemini-api/docs/structured-output).

Không dừng Tomcat hoặc đổi file secret trong lúc chủ máy đang sửa cấu hình. Hiện tại chạy SpeakMate bằng cổng **8081** và build Android với URL tương ứng như mục 4; không tiếp tục dùng cổng thử 18081 của đợt trước.

## 3. Các ca lỗi và đường đi đã kiểm chứng

| Ca | Bằng chứng | Đường code chính |
| --- | --- | --- |
| AI JSON/grammar ID sai | `reviewParsesStructuredOutputAndRejectsUnknownFields`, `malformedReportRollsBackEntireReportAndRetryKeepsEndTime` | `GeminiAiClient.review` → `ReviewValidator` → `ReviewTransactionService.complete`; báo cáo lỗi không lưu dở |
| Timeout transport | Hai test mới `waitingTimeoutCancelsPendingRequestAndReturnsAiTimeout`, `httpTimeoutReturnsAiTimeoutWithoutProviderDetails` | `GeminiAiClient.generate` → catch timeout → `AI_TIMEOUT`; hủy future còn chờ, không lộ chi tiết upstream |
| Timeout giữ USER | `failedProviderKeepsUserAndRequiresSameRequestForRecovery` | `claimReply` commit USER → AI lỗi → `failReply`; thử lại cùng UUID |
| Gửi/tạo trùng | MySQL test idempotency/concurrency; JAR thật replay create trả 200 và cùng ID | `ConversationTransactionService.create/claimReply/completeReply` |
| Kết thúc khi AI còn chạy | `duplicateWhilePendingReturns202AndFinishDiscardsLateReply` | `ReviewTransactionService.claim` đóng bài/vô hiệu token; lời AI muộn không được lưu |
| Rời màn chat khi reply pending | Test mới `leavingChatDuringReplyKeepsNewScreenAndHistoryRecoversOneTurn` | `NetworkFragment.generation`, `ChatFragment.onPause/onDestroyView`, `PendingRequests`; màn mới giữ nguyên và mở lịch sử khôi phục một lượt |
| Truy cập dữ liệu người khác | Bổ sung finish/review/retry/history trong `StudyIntegrationTest`; HTTP thật trả 404 | JWT → owner query trong TransactionService; bị chặn trước khi gọi AI |
| Lưu từ và mở lại lịch sử | MySQL test full report + Android fixture học trọn vòng, mở lại bài CLOSED | `SavedWordService`, `SavedWordStudyFragment`, `HistoryFragment`, `SpeakingResultFragment` |

Timeout và AI response lỗi được mô phỏng có kiểm soát trong test, không cố tình đốt quota hoặc ngắt mạng dịch vụ thật. Test rời màn hình kiểm callback/UI và dữ liệu; chưa chứng minh bằng tai rằng TTS trên thiết bị thật đã dừng.

### Lỗi đã gặp qua môi trường thật

1. **MySQL 1045:** file local đang có mật khẩu trống → JDBC kết nối tới 3306 → MySQL từ chối xác thực. Chưa đủ điều kiện khởi động backend với DB nhóm. Không sửa SQL/schema để chữa lỗi đăng nhập DB.
2. **Cổng 8080 không phải SpeakMate ở đợt trước:** Android base URL cũ đi tới Tomcat. Không phải lỗi Retrofit hoặc endpoint; hiện tại dùng 8081 cho cả backend và URL khi build app.
3. **POST `/api/v1/conversations/1/messages` trả 503:** Security/DTO → `ConversationController.send` → `ConversationService.send` → `claimReply` lưu USER → `GeminiAiClient.generate` phát hiện key trống → `failReply` bỏ mã xử lý. GET bài sau đó trả 200, giữ câu gốc và trạng thái RETRYABLE. Chưa có request nào tới Gemini trong phép thử này.

Trong đợt kiểm chứng trước, không phát hiện lỗi production cần sửa; các sửa mã khi đó chỉ nằm trong ba file test. Lần thống nhất cổng 8081 chỉ thay cấu hình/URL và tài liệu như mục 0.

## 4. Thao tác local tiếp theo

### Tạo và lưu Gemini API key

1. Mở [API keys trong Google AI Studio](https://aistudio.google.com/api-keys), đăng nhập Google và chấp nhận điều khoản nếu được yêu cầu.
2. Nếu project mặc định đã có key, copy key đó. Nếu chưa có, chọn **Create API key** và project của mình để tạo. Nếu project có sẵn chưa xuất hiện, vào **Dashboard → Projects → Import projects** trước. Xem [hướng dẫn chính thức của Google](https://ai.google.dev/gemini-api/docs/api-key).
3. Mở `backend/application-local.properties`, dán key ngay sau `app.ai.api-key=`; không thêm dấu nháy, không sửa tên model.
4. Bấm **Ctrl+S**. Chỉ thông báo đã lưu; không gửi key qua chat hoặc đưa vào Android. File local đang được Git bỏ qua.
5. Trong cùng file, `spring.datasource.password` cần mật khẩu MySQL thật. Khi cả hai đã lưu, kiểm tra MySQL chỉ đọc rồi khởi động backend tại thư mục `backend` để nó đọc đúng file local. Backend đang chạy cần được khởi động lại để nhận cấu hình mới.

Tạo được key chưa chứng minh model/quota hoạt động. Request reply đầu tiên qua backend là phép kiểm tra Gemini thật; chỉ tiếp tục các lượt và review khi request đó thành công.

### Kiểm MySQL chỉ đọc

Điền thông tin thật vào file local, không gửi qua chat. Trong PowerShell, có thể kiểm bằng MySQL client; `-p` sẽ hỏi mật khẩu và không hiển thị ký tự:

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' `
    --no-defaults --protocol=TCP --host=127.0.0.1 --port=3306 `
    --user=root -p --database=speakmate_v3 --execute='SELECT 1; SHOW TABLES;'
```

Nếu dùng user khác, đổi `--user` cho khớp cấu hình. Lỗi 1045 cần sửa thông tin đăng nhập; lỗi 1049 cần xác nhận database đã được chuẩn bị. Không chạy script cài mới đè dữ liệu đang có. Chỉ dùng tài khoản/schema test cho các bước ghi tiếp theo; sao lưu và làm theo [hướng dẫn CSDL](../SpeakMate-thiet-ke-co-so-du-lieu.md) nếu phải chuẩn bị DB.

### Chạy backend và khớp URL Android

Sau khi cấu hình DB test và Gemini hợp lệ, tại thư mục backend:

```powershell
$env:PORT = '8081'
.\gradlew.bat bootRun
```

Giữ terminal này. Tại thư mục frontend, emulator trên cùng máy dùng:

```powershell
.\gradlew.bat assembleDebug '-Pspeakmate.api.baseUrl=http://10.0.2.2:8081/'
```

Cài APK mới từ `frontend/app/build/outputs/apk/debug/app-debug.apk`. Điện thoại thật dùng IP LAN của máy backend và cùng mạng; không dùng `10.0.2.2` hoặc `127.0.0.1` của điện thoại. Nếu firewall chặn, chỉ mở cổng thử cần thiết trên mạng riêng. URL đã chọn phải trỏ đúng SpeakMate. Google client ID đổi cũng cần build/cài lại app.

### Vòng học thật tối thiểu

1. Đăng ký/đăng nhập một **tài khoản test**; chuẩn bị tình huống cà phê bằng admin hoặc seed trên DB test.
2. Chọn topic và B2 khi hồ sơ A1. Bắt đầu bài, transcript phải rỗng. Nói ba câu dưới hoặc nội dung tương đương, tổng ít nhất 30 từ tiếng Anh:
   - `Hello, I would like a medium latte to take away, please. Could you make it with less sugar?`
   - `Thank you. Yesterday I go to another cafe, but their coffee was too sweet for me. What do you recommend?`
   - `That sounds good. I will try your suggestion today. How much is the drink, and could I have a receipt, please?`
3. Sau mỗi câu phải có lời AI liên quan đến ngữ cảnh và được đọc; trong chat chưa hiện nhận xét hoặc bảng từ. Ghi độ trễ quan sát được, không tự nhận là realtime.
4. Mở lại bài/lịch sử: các câu phải còn vì backend tự lưu. Kết thúc, chờ báo cáo. Nhận xét cần dựa vào câu thực tế, câu gốc không bị sửa mất, không tự đổi level hồ sơ. Không bắt buộc AI luôn khuyên về A1 hoặc luôn tìm đúng một số lỗi cố định.
5. Chọn một từ gợi ý → lưu → Từ của tôi → lật thẻ → Chưa nhớ/Đã nhớ. Lật thẻ không ghi lần ôn; nút đánh giá mới PATCH lên server. Nếu AI không gợi ý từ nào thì đó có thể là kết quả hợp lệ, nhưng phần lưu từ thật của lần thử đó chưa được nghiệm thu.
6. Mở lịch sử → chọn bài đã đóng → phải thấy lại báo cáo. Đưa app xuống nền lúc còn chờ một lời AI; quay lại phải phục hồi từ server, không sinh trùng câu hoặc phát toàn bộ lịch sử.

Đây là tối thiểu **3 lần sinh reply + 1 lần sinh review**. Không gọi thêm API chat trực tiếp ở AI Studio để chứng minh điều backend đã làm được; không tự retry liên tục khi hết quota. Nếu lỗi, ghi HTTP status/error code, conversationId/requestId và bước đang làm; không chụp token/password/API key.

### Kiểm một request backend mà không lộ token

Ví dụ dưới chỉ chạy sau khi backend thật đã hoạt động và tài khoản test đã tồn tại. Không thực hiện trên tài khoản/dữ liệu production. Gán response vào biến; không in toàn bộ `$login`.

```powershell
$api = 'http://127.0.0.1:8081/api/v1'
$email = Read-Host 'Email tài khoản test'
$securePassword = Read-Host 'Mật khẩu tài khoản test' -AsSecureString
$credential = [System.Management.Automation.PSCredential]::new($email, $securePassword)
$loginBody = @{ email=$email; password=$credential.GetNetworkCredential().Password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$api/auth/login" -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization='Bearer ' + $login.data.access_token }
$topics = Invoke-RestMethod -Uri "$api/topics?page=1&size=20" -Headers $headers
$topic = $topics.data.result | Where-Object { $_.name -like '*cà phê*' } | Select-Object -First 1
if ($null -eq $topic) { throw 'Chuẩn bị chủ đề cà phê trên DB test trước.' }
$createBody = @{ topicId=$topic.id; level='B2'; createRequestId=[guid]::NewGuid().ToString() } | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri "$api/conversations" -Headers $headers -ContentType 'application/json' -Body $createBody
$id = $created.data.id
$turnBody = @{ content='Hello, I would like a medium latte to take away, please. Could you make it with less sugar?'; requestId=[guid]::NewGuid().ToString() } | ConvertTo-Json
$turn = Invoke-RestMethod -Method Post -Uri "$api/conversations/$id/messages" -Headers $headers -ContentType 'application/json' -Body $turnBody
$turn.data.aiMessage.content
```

Nếu muốn kiểm chống trùng sau khi lượt thành công, gửi lại **đúng `$turnBody`** và so `aiMessage.id`; không sinh UUID mới. Nó phải trả lại lượt cũ. Sau phép thử gỡ các biến chứa mật khẩu/token hoặc đóng terminal. Không ghi transcript phiên PowerShell có response đăng nhập.

## 5. Google và SMTP cần kiểm thủ công

Google: backend `app.auth.google-client-id` và Android `speakmate.google.webClientId` cùng dùng **Web client ID**. OAuth client Android nằm cùng Cloud project, package `com.example.speakmate`, SHA-1 đúng khóa ký APK (lấy bằng `frontend\gradlew.bat signingReport`). Luồng ID token hiện tại không cần client secret hoặc redirect callback. Tham khảo [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation) và [xác minh ID token](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

- Google → hủy: ở lại màn đăng nhập và bấm lại được.
- Google email chưa đăng ký: tạo USER/A1, đăng nhập lại giữ cùng ID.
- Email đã có tài khoản mật khẩu: Google login trả `GOOGLE_LINK_REQUIRED`; đăng nhập mật khẩu rồi liên kết cùng email; Google sau đó vào cùng tài khoản. Liên kết email khác bị từ chối.

SMTP: điền `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password`, `app.mail.from`. Gmail thường dùng `smtp.gmail.com:587`, STARTTLS và App Password; xem [App Password](https://support.google.com/accounts/answer/185833?hl=en) và [Gmail SMTP](https://developers.google.com/workspace/gmail/imap/imap-smtp). Không nhập mật khẩu vào tài liệu.

- Dùng email test có hộp thư mình sở hữu và tài khoản mật khẩu. Gửi mã: HTTP 202 chỉ xác nhận nhận tác vụ, phải kiểm thấy email thật/spam.
- Nhập mã sáu số trong 10 phút, đổi mật khẩu. Mật khẩu cũ không đăng nhập được, mới đăng nhập được; phiên cũ bị thu hồi, mã không dùng lại được.
- Thời gian xin lại 60 giây, tối đa năm lần sai. Không kiểm reset bằng tài khoản production. Tài khoản Google-only không tự tạo mật khẩu qua luồng reset.

## 6. Bằng chứng và các file liên quan

- Backend unit/MySQL: `backend/build/reports/tests/test/index.html`, `backend/build/reports/tests/mysqlTest/index.html`.
- Android UI: `frontend/app/build/reports/androidTests/connected/debug/index.html`; lint: `frontend/app/build/reports/lint-results-debug.html`.
- Artifact kiểm chứng trong phiên: `backend/build/integration-validation/configuration-status.json`, `api-validation.json`. Các file trong build có thể bị xóa khi clean; không phải thành phần chạy của sản phẩm. Báo cáo API không chứa token/password/key.
- Test bổ sung: `backend/src/test/java/vn/tailinh/speakmate/StudyIntegrationTest.java`, `backend/src/test/java/vn/tailinh/speakmate/service/ai/GeminiAiClientTest.java`, `frontend/app/src/androidTest/java/com/example/speakmate/LearningFlowInstrumentedTest.java`.
- Đường lỗi AI: `ConversationService`, `ConversationTransactionService`, `GeminiAiClient`, `ReviewValidator`, `ReviewTransactionService`; lifecycle: `NetworkFragment`, `ChatFragment`, `SpeechController`.

Các tiến trình thử ở đợt cũ và MySQL tạm được dừng/dọn sau kiểm tra; không dừng Tomcat hay MySQL 3306. Backend live mới nhất đang chạy 8081 như mục 0. Chỉ xác nhận Google/SMTP/voice thật khi có bằng chứng của chính lần chạy đó.
