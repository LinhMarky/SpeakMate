# 01 — Phạm vi sản phẩm và hiện trạng

## 1. Mục tiêu bản demo ba ngày

Người học ngại nói tiếng Anh có một đối tác AI đóng vai theo tình huống. Hai bên trao đổi bằng giọng nói theo lượt, có transcript; **chỉ sau Kết thúc** mới có góp ý và từ nên học. Người học chọn lưu từ, ôn bằng flashcard và xem lại bài. Admin thêm/sửa/bật/tắt tình huống để app có nội dung thực tế.

Giá trị cần chứng minh bằng demo: có đối thoại nối tiếp theo ngữ cảnh, có góp ý dựa trên câu đã nói, có lịch sử và có bước ôn sau bài. Số màn hình hoặc số bảng không thay thế những hành vi này.

## 2. Phạm vi đã khóa

| Mã | P0 — phải hoàn thành | Phân công chính |
| --- | --- | --- |
| F01 | Đăng ký/đăng nhập email, Google, refresh, logout; UI quên/đặt lại mật khẩu dùng API sẵn có | FE form/cấu hình Android; BE cấu hình và xác minh dịch vụ |
| F02 | Danh sách tình huống thật; chọn A1/A2/B1/B2 độc lập với mức hồ sơ | FE chọn/lưu ID; BE GET topics và kiểm chủ đề đang bật |
| F03 | Hội thoại text nhiều lượt với AI thật, không nhận xét giữa phiên | Cả hai |
| F04 | Nhận dạng tiếng Anh và đọc lời AI bằng Android; cho nhập text khi mic/TTS không dùng được | FE; BE chỉ nhận/trả text |
| F05 | Kết thúc; ưu/nhược điểm, câu sửa, khuyến nghị mức luyện, từ gợi ý; xử lý thiếu dữ liệu và retry | BE nghiệp vụ/AI; FE trạng thái/kết quả |
| F06 | Lịch sử và khôi phục phiên đã được server tạo | Cả hai |
| F07 | Chọn lưu từ gợi ý; một danh sách Từ của tôi; lật thẻ, tự đánh dấu Chưa nhớ/Đã nhớ | Cả hai |
| F08 | Trang admin tối thiểu: login, danh sách, thêm/sửa/bật/tắt chủ đề | FE HTML/CSS/JS đơn giản; BE API và quyền ADMIN |
| F09 | Chống gửi trùng, chặn sai chủ sở hữu, giữ transcript khi AI lỗi, không phát câu AI muộn sau Kết thúc | BE giữ dữ liệu; FE giữ requestId/trạng thái |
| F10 | APK, hướng dẫn chạy, dữ liệu demo, kết quả kiểm thử và bài trình bày luồng code | Cả hai |

**P1, ngoài cam kết ba ngày:** sửa tên/trình độ hồ sơ, xóa từ cá nhân, admin danh sách/khóa USER và thống kê. OpenAPI có định nghĩa P1 để ghi nhận hướng phát triển; FE không được phụ thuộc chúng để hoàn thành P0.

**Sau bản demo:** luyện lại 1–3 từ có mục tiêu (`word_practices`), thêm/sửa từ thủ công, đánh giá sao `self_rating`, thuật toán ôn theo lịch, giọng dịch vụ nâng cao. Schema đã có chỗ cho một số hướng này; chưa cần tạo màn hình hoặc endpoint cho đủ mọi bảng.

Không làm trong đợt này: decks/bộ thẻ, từ điển toàn cục, chia sẻ, bảng xếp hạng, thanh toán, phân quyền động, microservices, WebSocket/WebRTC hoặc lưu audio. Thao tác lưu flashcard là lưu vào Từ của tôi trong app, không phải xuất Anki/PDF.

## 3. Hiện trạng được đối chiếu với code

| Phần | Đã có | Còn thiếu/không được giả định |
| --- | --- | --- |
| BE nền | Spring Boot/Java 21, MySQL validate; User, Topic, Conversation, Message, WordSuggestion, SavedWord và audit | WordPractice chưa dùng P0, chưa ánh xạ Entity |
| Auth BE | 9 endpoint tại AuthController/PasswordResetController, JWT/refresh/hash/reset và phân quyền đường dẫn admin | Google/SMTP thật chưa cấu hình và kiểm chứng bằng tài khoản thật |
| Auth Android | LoginActivity/AccountActivity, Google, register/forgot/reset, SessionManager/SessionStore, profile/link/logout | Google/SMTP thật chưa nghiệm thu |
| Chủ đề | 5 API learner/admin và màn chọn chủ đề từ server; admin HTML/CSS/JS | Cần dữ liệu/tài khoản ADMIN trên DB nhóm và thử trình duyệt thực tế |
| Chat | API create/get/list/send, lưu MySQL, snapshot/UUID/lease; ChatFragment đã nối API và mic/TTS Android | Gemini thật và chất lượng giọng nói trên điện thoại chưa nghiệm thu |
| Kết quả | API finish/get/retry, validator, lưu atomic; FE render báo cáo/câu sửa/mức luyện/từ theo server | Chất lượng review Gemini thật chưa nghiệm thu |
| Flashcard | FE/BE list/chọn lưu/ôn từ; Từ của tôi theo tài khoản; lật thẻ không gọi API | Deck/demo cũ giữ trong source nhưng không nằm trong điều hướng P0 |
| Lịch sử/admin | HistoryFragment mở lại bài và trang admin chủ đề đã có code | Cần thử FE–BE–provider cùng một môi trường; P1 users/stats vẫn hoãn |

Kiểm chứng đã có trước đợt này: BE 68 unit + 11 MySQL/API test đạt; Android 3 instrumentation test đạt; build/lint Android đạt với 0 lỗi và 150 cảnh báo. Những kết quả đó chủ yếu bao phủ nền/auth, **không chứng minh Speaking, AI, từ vựng hoặc admin đã chạy**.

Cập nhật 26/10/2026: BE `build mysqlTest` đạt **75 unit + 27 MySQL/API test**, không lỗi/bỏ qua. Trong đó 16 test tích hợp mới kiểm vòng học và cạnh tranh; AiClient được thay bằng stub trong test. Đây chưa phải kết quả E2E Android hoặc đánh giá Gemini thật. Chi tiết đọc code/cấu hình tại [file 08](08-doc-code-va-chay-backend.md).

Đợt tích hợp Android/admin: xem [file 10](10-tich-hop-android-va-admin.md) để phân biệt kiểm thử native UI với HTTP fixture, MySQL thật tạm và phần provider/thiết bị thật còn cần xác nhận. Các mở rộng dùng lại từ trong file 09 tạm hoãn; không thêm vào cam kết ba ngày.

Android đã chạy `connectedDebugAndroidTest` trên API 35 với Retrofit: **7 test đạt, 0 lỗi/bỏ qua**. Bao gồm vòng học qua HTTP fixture, khôi phục Activity, form tài khoản và refresh đồng thời; chưa phải chạy với Gemini thật.

## 4. Quy tắc sản phẩm không được cắt vì deadline

1. Trong chat không hiển thị/đọc lời sửa lỗi, điểm số, trình độ hay bảng từ mới.
2. Người hồ sơ A1 được chọn B2; khuyến nghị cuối bài dựa trên bài thực tế, không ép luôn về A1 và không tự đổi users.level.
3. Từ gợi ý thuộc báo cáo bài; chỉ từ được chọn mới thành saved_words. Không lưu toàn bộ tự động.
4. Lịch sử là dữ liệu server; tắt/sửa chủ đề không viết lại snapshot bài cũ.
5. Client không gửi userId, role, AI result hoặc processingToken để quyết định dữ liệu/quyền.
6. Mất mạng/AI lỗi không được xóa câu đã lưu hoặc âm thầm tạo một phiên/lượt mới.
7. Nhận xét dựa trên transcript; không chấm phát âm hay chứng nhận CEFR từ dữ liệu text.

## 5. Nội dung demo tự biên soạn

| Tình huống | Mức gợi ý | Vai/bối cảnh cho scenario |
| --- | --- | --- |
| Nhận phòng khách sạn | A1 | AI là lễ tân; người học nhận phòng, hỏi ăn sáng và gửi hành lý |
| Gọi món tại quán cà phê | A1 | AI là nhân viên; người học gọi món và hỏi lựa chọn đồ uống |
| Giới thiệu ở lớp học | A2 | AI là bạn học mới; trao đổi tên, sở thích, việc học |
| Lên kế hoạch du lịch | B1 | AI là bạn đồng hành; cùng bàn lịch, chi phí và hoạt động |
| Thảo luận cách học hiệu quả | B2 | AI là bạn học; trao đổi quan điểm và lý do |

Scenario mô tả vai/tình huống, không chứa khóa API hoặc prompt kỹ thuật. Quy tắc hệ thống nằm ở PromptBuilder. Nhóm tự tạo nội dung bằng admin; ID trong examples.json là minh họa, không hardcode vào Android.
