# 12 — Bảng nối API và bản demo

> **Sau lần pull 29/10/2026:** đây là bảng tích hợp và bằng chứng của bản FE trước pull. Bản FE hiện tại chưa có đường gọi 20 API người học. Xem [file 13 — audit hiện tại](13-final-integration-audit.md) trước khi demo; không dùng các kết quả PASS lịch sử để xác nhận APK hiện tại.

Cập nhật 29/10/2026. Phạm vi: hoàn thiện vòng học trên **24 API đã triển khai**, contract 1.0.0. Đợt nối API ngày 28/10 tạm hoãn ba lỗi FE; yêu cầu hoàn thiện bản demo tiếp theo đã bao gồm xử lý ba lỗi đó. Không thay database, thêm API P1 hoặc viết lại giao diện. Kết quả ngày 28/10 ở mục 5 là bằng chứng của phiên bản trước các bản sửa mới.

## 1. API đã có nơi sử dụng

Mọi URL trong bảng nằm sau `/api/v1`. “Đã nối” xác nhận có lời gọi từ màn hình đang dùng hoặc trang admin, không đồng nghĩa tất cả dịch vụ bên ngoài đã được nghiệm thu.

Các file Android nằm trong `frontend/app/src/main/java/com/example/speakmate/`; trang admin nằm trong `backend/src/main/resources/static/admin/`.

| # | Method / URL | Nơi người dùng thao tác | Đường gọi hiện có |
| --- | --- | --- | --- |
| 1 | POST `/auth/register` | Form tạo tài khoản | `AccountActivity.submit` → `AuthApi.register` |
| 2 | POST `/auth/login` | Đăng nhập email/mật khẩu | `LoginActivity` → `AuthApi.login`; admin cũng dùng endpoint này |
| 3 | POST `/auth/google` | Tiếp tục với Google | `GoogleSignInHelper` → `LoginActivity` → `AuthApi.google` |
| 4 | POST `/auth/refresh` | Tự đổi token khi request gặp 401 | `SessionManager.request` → `AuthApi.refresh`; admin có refresh riêng trong `admin.js` |
| 5 | GET `/auth/me` | Khôi phục phiên khi mở app | `LoginActivity.restoreSession` → `SessionManager.restore` |
| 6 | POST `/auth/logout` | Cá nhân → Đăng xuất | `ProfileFragment.changeAccount`; nút logout trang admin |
| 7 | POST `/auth/google/link` | Cá nhân → Liên kết Google | `ProfileFragment.changeAccount` → `SessionManager.request` |
| 8 | POST `/auth/password/forgot` | Quên mật khẩu → Gửi mã | `AccountActivity.sendCode` → `AuthApi.forgot` |
| 9 | POST `/auth/password/reset` | Nhập mã và mật khẩu mới | `AccountActivity.submit` → `AuthApi.reset` |
| 10 | GET `/topics` | Luyện hội thoại → Danh sách chủ đề | `SpeakingFragment.load` → `LearningApi.get` |
| 11 | POST `/conversations` | Chọn chủ đề và mức luyện | `SpeakingFragment.create` → `LearningApi.create` |
| 12 | GET `/conversations` | Lịch sử; số bài ở Home | `HistoryFragment.load`, `HomeFragment` → `LearningApi.get` |
| 13 | GET `/conversations/{id}` | Chat hoặc xem transcript bài đã đóng | `ChatFragment.load`, `SpeakingResultFragment.transcript` |
| 14 | POST `/conversations/{id}/messages` | Gửi câu nhập hoặc câu nhận dạng từ mic | `ChatFragment.transmit` → `LearningApi.send` |
| 15 | POST `/conversations/{id}/finish` | Kết thúc bài | `ChatFragment.finish` → `LearningApi.finish(id, false)` |
| 16 | GET `/conversations/{id}/review` | Kết quả bài luyện; mở lại bài đã đóng | `SpeakingResultFragment.load` → `LearningApi.get` |
| 17 | POST `/conversations/{id}/review/retry` | Thử nhận xét lại khi server cho phép | `SpeakingResultFragment.retry` → `LearningApi.finish(id, true)` |
| 18 | GET `/saved-words` | Từ của tôi; số từ MASTERED ở Home | `FlashcardsFragment.load`, `HomeFragment` |
| 19 | POST `/saved-words/from-suggestions` | Chọn từ trong nhận xét → Lưu | `SpeakingResultFragment.save` → `LearningApi.saveWords` |
| 20 | PATCH `/saved-words/{id}/review` | Lật thẻ → Chưa nhớ / Đã nhớ | `SavedWordStudyFragment.answer` → `LearningApi.reviewWord` |
| 21 | GET `/admin/topics` | Danh sách chủ đề trên web admin | `admin.js`: `load()` |
| 22 | POST `/admin/topics` | Thêm chủ đề trên web admin | `admin.js`: submit `topicForm` khi chưa chọn bài sửa |
| 23 | PUT `/admin/topics/{id}` | Sửa chủ đề trên web admin | `admin.js`: submit `topicForm` với `editingId` |
| 24 | PATCH `/admin/topics/{id}/active` | Bật/tắt chủ đề trên web admin | `admin.js`: nút `toggle` |

Android dùng `ApiClient`/Retrofit và `SessionManager` hiện có. Không tạo thêm lớp gọi HTTP hoặc refresh song song. Bản thân danh sách chủ đề, chat, nhận xét, từ đã lưu và lịch sử lấy dữ liệu server; bộ deck/demo local cũ không nằm trong điều hướng P0.

## 2. Cấu hình cho bản demo

- Backend: cổng **8081**, MySQL local `speakmate_v3`, Gemini giữ nguyên model đã cấu hình.
- Emulator: **`http://10.0.2.2:8081/`**. Retrofit tự thêm `api/v1/`.
- Điện thoại thật: URL là IP LAN của máy backend, cùng mạng; sửa URL trong `frontend/local.properties` rồi build lại. APK debug mới cho phép HTTP.
- Google: đã nối nút chọn tài khoản, đổi ID token và liên kết. Còn thiếu Web client ID ở BE/Android, cấu hình Android OAuth/package/SHA-1 và nghiệm thu thật.
- Email: đã nối form xin mã/đặt lại mật khẩu; còn thiếu SMTP và xác nhận email thật.
- Admin: mở `http://localhost:8081/admin/index.html`; dùng tài khoản ADMIN riêng theo [file 08](08-doc-code-va-chay-backend.md). Không tự nâng quyền USER hoặc nhập tài khoản/mật khẩu demo cố định.

Không đưa Gemini key vào Android. Các file local vẫn được Git bỏ qua. Google/SMTP thiếu cấu hình phải báo chưa sẵn sàng, không giả thành công.

## 3. Kiểm tra app với dịch vụ thật

Đã bổ sung `LiveLearningFlowInstrumentedTest` trong **androidTest**, không nằm trong APK sản phẩm. Test mặc định bỏ qua; chỉ chạy khi người phát triển chủ động bật `speakmate.liveVerification=true` và app dùng URL emulator 8081.

Test tạo tài khoản mới với mật khẩu ngẫu nhiên trong bộ nhớ, dùng `AuthApi` thật để đăng ký, sau đó bấm UI đăng nhập → chọn B2/chủ đề cà phê → 3 lượt chat → kết thúc/nhận xét → chọn lưu từ → lật/đánh dấu thẻ → mở lịch sử → refresh token → bấm đăng xuất. Kiểm UI cùng dữ liệu trả về từ backend, không dựng HTTP fixture hoặc thay Gemini.

Mỗi lần chạy có tối đa **3 reply + 1 review Gemini thật**; không tự chạy lại khi provider lỗi. Cần backend, DB và một chủ đề cà phê đang bật. Báo cáo chỉ chứa ID và nội dung hội thoại thử, không chứa password/token/key. Phiên local trước test được khôi phục sau khi chạy.

Các GET dùng để đối chiếu trong test được phép thử lại một lần khi lỗi mạng; `verificationReadRetries` ghi số lần thực tế. Không áp dụng cơ chế đó cho thao tác ghi/gọi AI và không đổi transport của app. Test cũng bấm nút “Tải lại kết quả” sẵn có để kiểm nhánh đọc báo cáo.

Tại `frontend`, với emulator đang mở và các JDK đầy đủ hiện có:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain `
    '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-21' `
    '-Dorg.gradle.java.installations.paths=C:\Users\PC\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2,C:\Program Files\Java\jdk-21' `
    '-Pandroid.testInstrumentationRunnerArguments.class=com.example.speakmate.LiveLearningFlowInstrumentedTest' `
    '-Pandroid.testInstrumentationRunnerArguments.speakmate.liveVerification=true'
```

Kết quả lần chạy trong đợt này được ghi ở phần bổ sung bên dưới và [báo cáo kiểm chứng](11-kiem-chung-moi-truong-thuc-te.md). Không lấy kết quả fixture trước đây để xác nhận test live này.

## 4. Bàn giao bản app

Build `assembleDebug testDebugUnitTest lintDebug` không có tham số URL fixture. APK: `frontend/app/build/outputs/apk/debug/app-debug.apk`. Cài APK rồi dùng tài khoản riêng để thao tác; tài khoản tự sinh của bài test không lưu mật khẩu để đăng nhập lại.

Các thư mục `api`, `auth`, `ui`, `speech` và nhiều Activity/Fragment mới vẫn chưa được track trong Git. Khi bàn giao source phải mang đủ các file mới và resource tương ứng; không chỉ lấy các file có trạng thái modified. Không đưa file local, thư mục build hoặc cache vào source. Đợt này không commit/push/merge.

Phần chưa triển khai vẫn giữ nguyên: sửa hồ sơ, xóa từ, quản lý user và thống kê admin mở rộng. Các operation `CONTRACT_ONLY` không được đưa vào luồng sử dụng như thể đã có backend.

Mic/nhận dạng/TTS phải nghe và thử trên thiết bị demo. Test text/UI không xác nhận chất lượng âm thanh. Hướng dẫn từng bước tạo Google OAuth client và SMTP nằm ở [luồng tài khoản, mục 5–6](../SpeakMate-luong-tai-khoan.md).

## 5. Kết quả chốt bản

- **Native Android → Retrofit → backend 8081 → MySQL/Gemini thật: PASS.** Test cuối: 1 test, 0 lỗi/bỏ qua; 91,176 giây; Gradle SUCCESSFUL 2 phút 48 giây. Đăng nhập UI, B2/cà phê, ba lượt chat, nhận xét, lưu từ, lật/ôn thẻ, lịch sử và đăng xuất đã thao tác thành công. Refresh token cũng kiểm được qua lớp `AuthApi` thật.
- Bản ghi thử cuối: user 7, conversation 5, 6 tin, 1 correction, 3 từ gợi ý; thẻ đã chọn MASTERED. Đối chiếu JDBC với MySQL PASS; profile vẫn A1, token refresh đã thu hồi sau logout. Lần PASS không cần thử lại GET đối chiếu (`verificationReadRetries=0`).
- Hai lần trước giữ nguyên bằng chứng: lỗi thao tác `scrollTo` trong test và một lỗi mạng GET đối chiếu. Đã sửa helper/test, không sửa ba lỗi FE hoãn. Tổng đợt Android là 12 request Gemini thật, đều 200; không coi hai lần bị dừng là full-flow PASS.
- Admin: tài nguyên web thật trả 200 và khớp source; API chưa có token trả 401; cú pháp JavaScript PASS. Chưa chạy CRUD qua giao diện bằng tài khoản ADMIN thật.
- Google/email: đường gọi đã nối, còn thiếu cấu hình và kiểm dịch vụ thật. Mic/TTS: cần nghe/kiểm trên thiết bị. Năm API P1 chưa triển khai vẫn hoãn.
- Build cuối `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`: **exit 0, SUCCESSFUL 25 giây**, 7 task chạy / 71 up-to-date. App/test APK đóng gói được; unit mặc định 1 test dùng cache, lint 0 lỗi / 179 cảnh báo có sẵn. Backend không đổi nên không chạy lại bộ test BE trong đợt này; kết quả 77 unit + 27 MySQL/API trước đó nằm ở file 11.
- Sau lần live PASS, đổi cách điền mật khẩu thử sang `ActivityScenario.onActivity` để Espresso không ghi giá trị action vào log; đã compile test/lint lại thành công, không gọi thêm Gemini. Giá trị mật khẩu thử trong ba log của chính các lần chạy này đã được che, không chỉnh log ứng dụng/dữ liệu người dùng khác.
- APK xác nhận có URL 8081, không có URL fixture và không chứa Gemini key local trong DEX. Quét 218 file nguồn không thấy secret theo quy tắc kiểm; các file local không bị track; `git diff --check` PASS. Emulator read-only riêng `emulator-5564` đã được gửi `emu kill`, nhận OK; giữ backend 8081 để nhóm dùng tiếp.

## 6. Chuẩn bị buổi demo trên thiết bị thật

1. Chạy backend từ thư mục `backend`, kiểm MySQL/Gemini và cổng 8081. Cài APK debug build với URL máy demo; emulator dùng `10.0.2.2`, điện thoại dùng IP LAN của máy backend. Giữ máy tính/điện thoại cùng mạng và kiểm firewall cho đúng cổng. Không import lại SQL khi database đang dùng đã hợp lệ.
2. Dùng tài khoản USER riêng và tài khoản ADMIN riêng. Admin mở `http://localhost:8081/admin/index.html`, thêm một chủ đề thử; người học bấm tải lại chủ đề để thấy dữ liệu mới. Cách cấp ADMIN có kiểm soát nằm ở [file 08](08-doc-code-va-chay-backend.md); không sửa role của tài khoản đang học.
3. USER hồ sơ A1 chọn mức B2 và tình huống. Nói/nhập ít nhất ba lượt có nội dung; nếu trình bày nhận xét mức độ, nên có khoảng năm lượt. Có thể cố ý nói “Yesterday I go …”. Trong bài chỉ có hội thoại, chưa hiện nhận xét ngữ pháp.
4. Với giọng nói: bấm **Nói**, cấp quyền mic, kiểm câu nhận dạng và nghe AI. Mặc định bật gửi khi nói xong/tự đọc AI; có thể đổi ở **Âm thanh**. Đây là STT → hội thoại text → TTS theo lượt, chưa phải truyền audio liên tục. Kiểm cả từ chối quyền, dừng giọng đọc và mất mạng trên chính thiết bị demo.
5. Kết thúc → đọc tổng kết, ưu/nhược điểm, câu gốc/câu sửa, mức gợi ý. Đối chiếu câu sửa với transcript; gợi ý mức dựa nội dung thực tế, không luôn bắt A1 chọn B2 phải xuống A1. Mức hồ sơ không bị tự đổi. Không giới thiệu đây là bài chấm phát âm hay chứng nhận CEFR.
6. Chọn một từ gợi ý → lưu → **Từ của tôi** → lật thẻ → đánh dấu → lọc lại trạng thái. MASTERED là người học tự đánh dấu đã nhớ, không phải AI chứng minh đã thuộc. Chỉ từ người học chọn mới vào danh sách cá nhân.
7. Mở lịch sử → đúng chủ đề, mức và transcript; mở lại nhận xét. Ra nền khi chờ phản hồi rồi quay lại; nếu mất phản hồi, dùng khôi phục/thử lại có UUID cũ, không bắt đầu bài mới liên tục.
8. Trước buổi bảo vệ, thử Google và nhận email khôi phục thật theo [hướng dẫn cấu hình](../SpeakMate-luong-tai-khoan.md). Chưa cấu hình/kiểm thành công thì ghi rõ giới hạn, tiếp tục dùng đăng nhập email cho vòng học.

### Những điểm BE cần chỉ được trong code

Các file dưới đây nằm trong `backend/src/main/java/vn/tailinh/speakmate/`. Bản đồ chi tiết hàm và transaction ở [file 08](08-doc-code-va-chay-backend.md).

| Khi thầy hỏi | Điểm giải thích |
| --- | --- |
| Ai được đọc/sửa bài? | Controller lấy user đã xác thực; Service/Repository kiểm chủ sở hữu, không tin userId phía Android |
| Retry có tạo hai tin không? | UUID của thao tác + ràng buộc DB + trạng thái request; Android giữ UUID/nội dung trong `PendingRequests` |
| Đang chờ AI mà đóng bài thì sao? | Ghi trạng thái/claim trong transaction ngắn; gọi AI ngoài transaction; chỉ lưu khi trạng thái còn hợp lệ |
| Tổng kết AI lỗi có mất bài không? | Đóng hội thoại và sinh review là hai bước; transcript vẫn giữ, retry theo trạng thái server |
| Nhận xét liên quan câu nào? | Review dùng hội thoại thật, correction gắn messageId; kiểm/parsing trước khi lưu |
| Từ gợi ý khác từ đã lưu thế nào? | Gợi ý thuộc bài; saved_words thuộc người học, có trạng thái ôn riêng |

Điểm mạnh để trình bày là vòng học và khả năng khôi phục dữ liệu; các mục P1 vẫn hoãn. Mục này là checklist thao tác, không phải xác nhận rằng mọi bước thủ công đã được nghiệm thu.

## 7. Bản sửa ngày 29/10/2026

| File thay trong đợt này | Mục đích |
| --- | --- |
| `SpeakingFragment.java` | Giữ ID bài vừa tạo ở nền, khôi phục/mở đúng bài khi quay lại |
| `FlashcardsFragment.java` | Bỏ phản hồi tải từ đã cũ, dùng đúng trang/bộ lọc của request |
| `HomeFragment.java`, `fragment_home.xml` | Loading/lỗi/tải lại thống kê, giữ số liệu thành công, chỉnh copy đúng tính năng |
| `LearningFlowInstrumentedTest.java` | Ba regression mới, khôi phục file pending của user fixture sau test |
| File 11, 12, README contract, `SpeakMate-luong-tai-khoan.md` | Bằng chứng mới, bàn giao demo, hướng dẫn Google/SMTP |

Không thay backend, schema, API contract hoặc file cấu hình local. Source/đổi tên có sẵn trước đợt này được giữ nguyên.

- Android fixture: 8/8 PASS, bao gồm tạo bài khi xuống nền, lọc về sai thứ tự và Home lỗi một request. Không gọi dịch vụ AI thật.
- Android → backend 8081 → MySQL/Gemini thật: 1/1 PASS sau sửa, 29,986 giây; Gradle 1 phút 7 giây. User thử 8, conversation 6; 4 request Gemini đều 200. JDBC đối chiếu transcript/nhận xét/thẻ PASS, không trùng, profile A1, logout thu hồi refresh.
- Google/SMTP chưa cấu hình. Mic/TTS và thao tác admin đầy đủ cần kiểm trên thiết bị/tài khoản demo riêng. Không giới thiệu bản giao là đã nghiệm thu toàn bộ các phần này.
- Backend không thay trong đợt này; bộ 77 unit + 27 MySQL/API ở mục lịch sử file 11 là kết quả đợt trước, không phải test BE vừa chạy lại. MySQL thật mới được kiểm chỉ đọc và qua vòng Android ở trên.
- Build cuối tại `frontend`: `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --no-daemon --console=plain` cùng tham số JDK ở mục 3: **exit 0, BUILD SUCCESSFUL 36 giây**, 10 task chạy / 68 up-to-date. Unit mặc định 1/1 PASS; lint **0 lỗi, 183 cảnh báo** (đợt trước 179; có thêm cảnh báo chuỗi giao diện viết trực tiếp theo style hiện có). Không coi unit mặc định là bằng chứng nghiệp vụ; bằng chứng nghiệp vụ là 8 fixture và 1 live ở trên.
- APK cuối `frontend/app/build/outputs/apk/debug/app-debug.apk`, 10.325.924 byte; kiểm DEX có URL emulator **8081**, không có URL fixture 18080 hoặc Gemini key local. Bốn tài liệu sửa có liên kết nội bộ hợp lệ, 9 block PowerShell parse thành công; `git diff --check` PASS. Quét 218 file source không phát hiện secret theo quy tắc kiểm; file local vẫn ignore/untracked. Không commit/push/merge.
- Đã đóng emulator read-only riêng 5564, nhận OK. Backend PID 13816 vẫn nghe 8081; `/api/v1/auth/me` không có token trả 401 đúng dự kiến. Giữ backend và MySQL local cho nhóm dùng tiếp.

## 8. Google Web client đã cấu hình — 29/10, 09:20

Đã điền Web client ID vào hai file local và build lại APK; không sử dụng client secret. `signingReport assembleDebug` SUCCESSFUL 17 giây, 6 task chạy / 29 up-to-date. APK đúng ID local và URL emulator 8081. Backend đã restart để nạp cấu hình trên 8081 (PID 18044); token cố ý không hợp lệ bị từ chối 401. Chưa xác nhận một lần chọn tài khoản Google/đổi token thật.

Thao tác tiếp theo trên Google Cloud, cùng project với Web client:

1. Đóng hộp tạo Web client → **Clients → Create client → Android**. Tên: `SpeakMate Android`, package: **`com.example.speakmate`**.
2. Dán SHA-1 của debug APK vừa build trên máy này:

```text
F2:07:7B:E2:55:2C:88:06:2D:A0:CB:36:0E:64:05:5E:1A:C9:CD:89
```

3. Tạo Android client. Giữ Web client ID đã điền trong hai file local; Android client được khai báo trên Cloud để nhận diện package/khóa ký. Máy FE ký bằng khóa khác phải tạo thêm Android client với SHA-1 của máy FE. Xem [codelab Google](https://codelabs.developers.google.com/sign-in-with-google-android).
4. Vào **Audience → Test users → Add users** nếu ứng dụng đang Testing và có mục này, thêm email dùng demo. Cài APK mới, dùng thiết bị có Play services và tài khoản Google, bấm **Tiếp tục với Google**. Với email đã có tài khoản mật khẩu, đăng nhập mật khẩu rồi liên kết cùng email ở Cá nhân.

APK vẫn tại `frontend/app/build/outputs/apk/debug/app-debug.apk`. Thiết bị thật cần build với IP LAN của máy backend; APK URL 10.0.2.2 hiện tại dành cho emulator. Hướng dẫn chi tiết Google/SMTP ở [luồng tài khoản, mục 5–6](../SpeakMate-luong-tai-khoan.md). Bước cấu hình mới này thay trạng thái Web client ID trống của các mục lịch sử; SMTP vẫn thiếu cấu hình. Không commit/push/merge.

## 9. SMTP đã cấu hình và xác thực thật — 29/10, 10:38

Đã cấu hình Gmail gửi mã trong file local được ignore, cổng 587/STARTTLS bắt buộc. Xác thực thật qua Spring Mail PASS; backend khởi động với `--spring.mail.test-connection=true` PASS trên **8081**, PID 16728. Lần chạy nền trước đó bị quyền mạng chặn, đã xử lý bằng cách chạy Java trực tiếp có quyền mạng; không thay code mail. Chi tiết ở [file 11](11-kiem-chung-moi-truong-thuc-te.md).

Chưa gửi thư từ công cụ kiểm và chưa xác nhận email đến hoặc đổi mật khẩu thành công. Tự thử trên app:

1. Chọn một tài khoản thử đã đăng ký bằng **email/mật khẩu** và có hộp thư nhận được. Gmail gửi mã có thể khác email của tài khoản nhận. Tài khoản chỉ dùng Google tiếp tục dùng Google để đăng nhập; luồng reset không tạo mật khẩu cho Google-only.
2. Màn đăng nhập → **Quên mật khẩu** → nhập email tài khoản thử → gửi mã. Kiểm Inbox và Spam; mã nhập trực tiếp trên app, không đưa vào chat hoặc báo cáo.
3. Nhập mã nhận được và mật khẩu mới → xác nhận → đăng nhập bằng mật khẩu mới. Kiểm mật khẩu cũ không đăng nhập được và mã đã dùng không dùng lại được.
4. Nếu xin lại mã, chờ ít nhất 60 giây; mã có hạn 10 phút, tối đa 5 lần sai. 202 có thể áp dụng cho email không tồn tại/Google-only/cooldown, không xác nhận đã gửi thư.
5. Khi thay App Password trong file local, khởi động lại backend từ thư mục `backend`. Mật khẩu đã chia sẻ trong chat nên được thu hồi, thay bằng mật khẩu mới tự điền trên máy; không gửi lại.

Không cần thay Android vì SMTP nằm ở backend. Đợt SMTP không sửa mã ứng dụng/schema, không commit/push/merge; giữ nguyên công việc frontend/Gradle đang có.
