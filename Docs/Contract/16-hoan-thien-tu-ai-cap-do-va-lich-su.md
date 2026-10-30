# 16 — Từ AI, C1/C2 và lịch sử tài khoản

Cập nhật **30/10/2026**, tiếp tục từ `dev/c56d1f3`, giữ các thay đổi local đã có. Đợt này hoàn thiện ba luồng học, không thêm endpoint, bảng hoặc Gemini provider/model mới. Không commit/push.

## 1. Nguyên nhân và cách dùng sau sửa

| Vấn đề trước đó | Nguyên nhân thực tế | Hành vi hiện tại |
| --- | --- | --- |
| AI flashcard không hoạt động | `GenerateDeckFragment.generate()` chỉ hiện thông báo chưa kết nối; thư viện/ôn thẻ đọc repository demo/local. Màn kết quả đã lưu suggestion nhưng ẩn nút mở từ và chưa đưa từ server sang các màn thẻ. | Nút **Từ AI trong bài luyện** mở lịch sử. Chọn bài đã kết thúc, chọn từ gợi ý thật và lưu vào **Từ của tôi**; thư viện/chi tiết/ôn thẻ đọc dữ liệu tài khoản. |
| C1/C2 không chọn/dùng được | XML đã có hai RadioButton nhưng bị ẩn; Java chỉ ánh xạ bốn mức. Backend enum, schema JSON AI và năm CHECK SQL cũng giới hạn đến B2. | Sáu mức A1/A2/B1/B2/C1/C2 xuyên suốt chọn bài, request, enum, SQL, prompt và khuyến nghị AI. Không đổi trình độ hồ sơ tự động. |
| Lịch sử không đồng bộ | `SpeakingHistoryFragment` đọc `SpeakingHistoryStore` trên máy. | GET lịch sử theo tài khoản đăng nhập, phân trang 20 bài, tải lại khi quay về, mở đúng ID mà không tạo bài/gửi lại tin. |

AI flashcard trong luồng này là **từ gợi ý từ bài hội thoại đã kết thúc**, theo API hiện có. Không tạo endpoint sinh bộ thẻ độc lập từ một chủ đề gõ tùy ý. Form/lớp local cũ được giữ để bảo toàn code; các bộ thẻ tự tạo trên thiết bị không được trình bày là dữ liệu cloud.

## 2. Vòng đời và các ID cần hiểu

1. Android chọn topicId và một trong sáu level, POST `/conversations` với createRequestId.
2. Gửi tin qua POST `/conversations/{id}/messages`; chat giữ transcript, không chấm/sửa giữa cuộc nói chuyện.
3. Chọn **Kết thúc → Xem tổng kết**, POST `/conversations/{id}/finish`. Backend đóng bài rồi mới gọi Gemini review và lưu nhận xét/corrections/suggestions trong transaction.
4. Màn kết quả GET `/conversations/{id}/review`. Chỉ poll khi PROCESSING có pollAfterSeconds > 0 và canRetry=false; dừng khi rời màn hình. Lease hết hạn/FAILED có canRetry=true thì người dùng bấm thử lại, gọi `/review/retry`; không tự gọi AI lặp.
5. COMPLETED hiển thị `suggestedWords`. Người dùng chọn các **suggestion.id**, gửi `suggestionIds` tới POST `/saved-words/from-suggestions`.
6. Đọc cả `created` và `existing`. Mỗi phần tử có `suggestionId` và `savedWord.id`; dùng ID trả về đánh dấu `savedWordId` trên từ gợi ý, khóa ô đã lưu và thông báo số từ mới/từ đã có.
7. GET `/saved-words?page=...&size=100` để đọc đủ trang. Mỗi Flashcard giữ **savedWordId thật**, nội dung, nghĩa, ví dụ và trạng thái server.
8. Lật thẻ trên máy. Bấm nhớ/chưa thuộc gọi PATCH `/saved-words/{id}/review` với MASTERED/LEARNING. Khóa thao tác trong lúc chờ; chỉ chuyển thẻ sau khi server xác nhận đúng ID và trạng thái. Lỗi giữ thẻ hiện tại để thử lại.
9. Lịch sử GET `/conversations?page=...&size=20`. OPEN mở ChatFragment bằng ID; CLOSED mở nhận xét đã lưu và transcript read-only. API không có DELETE nên nút xóa lịch sử server được ẩn.

`FlashcardDeck.SAVED_WORDS_ID` chỉ là nhãn điều hướng của một danh sách **Từ của tôi** trên UI, không phải ID bảng deck mới. `Flashcard.savedWordId=0` dành cho thẻ local; ID dương phải đến từ backend. Backend vẫn kiểm chủ sở hữu bằng tài khoản đã xác thực, không tin ID do FE gửi.

Bài ít hơn **3 lượt USER hoặc 30 từ tiếng Anh** có thể INSUFFICIENT_DATA, không có từ gợi ý. Đây là quy tắc nghiệp vụ, không được tạo từ mẫu để lấp kết quả. API cũng cho phép review hợp lệ không đề xuất từ nào nếu không có nội dung phù hợp.

## 3. Bản đồ code để lần theo và giải thích

| File/chỗ xử lý | Trách nhiệm |
| --- | --- |
| `SpeakingFragment` + `fragment_speaking.xml` | Hiện sáu mức, ánh xạ RadioButton ↔ code level, tạo bài bằng API cũ. |
| `EnglishLevel`, `PromptBuilder`, `ai/review-schema.json` | Backend nhận sáu enum; hướng dẫn C1 chính xác/linh hoạt, C2 sắc thái/thành ngữ/lập luận; schema review cho khuyến nghị đủ sáu mức. |
| `ReviewController → ReviewService → ReviewTransactionService` | Giữ luồng đã có: claim/đóng bài, AI ngoài transaction, validate JSON, lưu report/corrections/WordSuggestion cùng transaction. |
| `SpeakingResultFragment` | Hiện reviewStatus, poll/retry có kiểm soát, chọn ID gợi ý và đọc kết quả created/existing. |
| `SavedWordController → SavedWordService` | API hiện có kiểm chủ sở hữu, chống lưu trùng theo user/word; chỉ chấp nhận LEARNING/MASTERED sau ôn. |
| `LearningApi.savedCards()` | Đọc các trang server, ánh xạ sang Flashcard, giữ ID/status thật và loại ID trùng giữa các trang. |
| `FlashcardsFragment`, `DeckDetailFragment` | Hiển thị từ đã lưu của tài khoản, loading/empty/lỗi và tải lại; bộ local vẫn riêng. |
| `StudyFragment` | Giữ giao diện lật/vuốt, PATCH tiến độ rồi mới chạy chuyển thẻ; lưu ID snapshot khi recreate. |
| `SpeakingHistoryFragment` | Tải lịch sử, trạng thái/thời gian/level, tải thêm/làm mới; phản hồi cũ không ghi đè lượt tải mới. |
| `ConversationController → ConversationTransactionService → ConversationRepository` | API list/detail đã có lọc userId; đọc bài người khác trả 404. |

Không đổi API contract ngoài mở rộng giá trị EnglishLevel. `ReviewValidator` đã kiểm enum/field bắt buộc và nguồn messageId; không có logic cắt mức ở B2 cần viết lại. Entity dùng VARCHAR(2), đủ chứa C1/C2, không cần đổi cột hoặc quan hệ.

## 4. Database cũ và cài mới

- Cài mới: `speakmate.sql` đã mở rộng năm CHECK; Compose vẫn init schema/seed trên volume rỗng như trước.
- Database v3 đang có dữ liệu: dùng [V4__english_levels_c1_c2.sql](../migrations/V4__english_levels_c1_c2.sql). Không sửa migration V3 cũ, không đổi tên DB và không chạy lại script cài mới.
- V4 chỉ ALTER CHECK ở users, topics, conversations; không UPDATE/DELETE dòng. Không có USE cố định để có thể chạy vào đúng database đã chọn (`speakmate` Docker hoặc database local trong datasource).
- Trên máy tác giả, đã áp V4 cho Docker `speakmate` và MySQL local đang cấu hình; trước/sau đối chiếu toàn bộ dòng cũ không đổi. Volume giữ nguyên. Backend local đã chạy JAR mới trên **8081**; Docker backend đã build/chạy bản mới ở host **8082**.

Máy đồng đội đã có volume cũ cần sao lưu, chọn đúng project/database rồi chạy migration trước khi dùng C1/C2. Từ gốc repo:

```powershell
docker compose cp .\Docs\migrations\V4__english_levels_c1_c2.sql mysql:/tmp/speakmate-levels.sql
docker compose exec mysql sh
```

Trong shell của container MySQL, chạy (không in mật khẩu):

```sh
MYSQL_PWD="$MYSQL_PASSWORD" mysql --user="$MYSQL_USER" --database=speakmate < /tmp/speakmate-levels.sql
exit
```

Sau đó từ PowerShell:

```powershell
docker compose up --build -d --no-deps backend
docker compose ps
```

Backend chạy trực tiếp: mở V4 bằng công cụ MySQL, chọn đúng database trong `spring.datasource.url`, chạy script rồi khởi động lại backend bằng Wrapper/JAR mới. Không dùng các lệnh này để reset DB hoặc xóa volume.

## 5. Kiểm chứng đã thực hiện

| Lớp kiểm tra | Kết quả thực tế |
| --- | --- |
| Backend Wrapper `test bootJar` và script `Test-MySql.ps1` | **77 unit + 29 MySQL integration test PASS**, không failure/error/skipped. MySQL test dùng server thử riêng 13317; AiClient là stub, HTTP adapter là mock. |
| Hai ca integration mới C1/C2 | Tạo bài, review và khuyến nghị C1/C2 lưu SQL; suggestion → saved word → LEARNING/MASTERED; lịch sử phân trang và ownership; C3 trả 400. |
| Docker build | Build mới chạy `test bootJar` thành công, recreate riêng backend; không dừng/xóa MySQL hoặc volume. |
| Android build/unit | `assembleDebug assembleDebugAndroidTest testDebugUnitTest` PASS. |
| Android HTTP fixture | 6 ChatRecoveryTest cũ và 5 LearningFeaturesTest mới PASS khi chạy riêng từng class; không dùng MySQL/Gemini. Có poll, retry lease hết hạn, created/existing, paging, chờ PATCH, giữ thẻ khi lỗi, recreate giữ ID, history không POST lại. |
| Native UI thật | AndroidJUnitRunner trực tiếp: **1 test PASS, 32,239 giây**. Đăng ký qua API, đăng nhập UI, chọn C2/cà phê, 3 reply Gemini, finish/review thật, chọn lưu từ, lật thẻ, MASTERED rồi LEARNING, mở lịch sử/nhận xét/transcript. |
| Đối chiếu MySQL thật | JDBC chỉ đọc: userId=18, conversationId=11, 6 tin khớp sender/content/requestId; report/correction/suggestions/từ đã lưu khớp SQL, không lưu trùng; hồ sơ vẫn A1. |
| Nội dung AI lần thử | 1 correction trỏ đúng USER text, 2 từ gợi ý; chọn lưu 1 từ, savedWordId=7. AI khuyên A2 theo bài thực tế dù chọn C2; không ép khuyến nghị bằng mức đã chọn hoặc tự sửa hồ sơ. |
| Sai tài khoản | Tài khoản thử B thấy lịch sử rỗng; GET bài A và PATCH từ A đều 404. |

Một lần native đầu dừng trước gọi AI vì test chưa mở form đăng nhập từ trang chủ; đã sửa riêng test. Lượt PASS sau dùng đúng **3 reply + 1 review**, không tự retry Gemini và không đổi key/model. Các lần lấy artifact gặp hạn chế ADB/run-as đã chuyển sang `adb pull`; không gửi thêm request AI chỉ để lấy bằng chứng.

Bằng chứng local bị ignore tại `backend/build/learning-completion/`: `test-summary.json`, `fixture-results/`, `learning-fixture-results/`, `native-live-result.json`, `docker-migration-result.json`. Kết quả JDBC được ghi cùng báo cáo bàn giao. Artifact không phải dữ liệu để import lên môi trường nhóm và không cần commit.

Lần kiểm UI thật dùng text; chưa nghiệm thu âm thanh/mic/TTS hoặc Google/SMTP. Bốn request AI và đối chiếu lần này không phải chứng nhận mọi nhận xét hoặc mức CEFR do AI sinh ra đều chính xác. Máy chủ demo dùng chung vẫn cần nhóm chốt theo file 15.
