# 07 — Kiểm thử, nghiệm thu và bảo vệ

## 1. Khi nào một việc được coi là xong?

**Ready để FE tích hợp:** API thật gọi được, đúng contract, có dữ liệu/ca lỗi mẫu, BE chỉ rõ các side effect và cấu hình ngoài. **Verified:** FE chạy màn hình với API thật, test hành vi/DB quan trọng đạt và không có phản hồi demo thay thế.

Một việc chưa xong nếu chỉ có Entity, chỉ có ảnh UI, chỉ nhận JSON đúng dạng nhưng không lưu được, hoặc chỉ chạy fixture khi demo. Kiểm thử mới trong file này là **yêu cầu cần thực hiện**, chưa phải kết quả đã chạy.

## 2. Ma trận kiểm thử P0

Mỗi ca ghi máy/build, ngày giờ, người chạy, PASS/FAIL và evidence. Với DB test ghi/xóa, dùng MySQL tạm riêng theo README backend.

| ID | Ca thử | Kết quả FE/API/DB mong đợi | Người chính |
| --- | --- | --- | --- |
| AUTH-01 | Đăng ký email thường; thử role=ADMIN | User mới USER; field lạ 400; hash không trả response | BE + FE |
| AUTH-02 | Email trùng/sai mật khẩu | 409/401 đúng; không mất form hoặc lộ hash | Cả hai |
| AUTH-03 | Google thật: tài khoản mới, bấm hủy | Mới tạo USER/A1 và vào app; hủy không tạo phiên/không báo thành công | Cả hai |
| AUTH-04 | Google trùng email tài khoản local | 409 link-required; login local rồi liên kết đúng email; không tự gộp | Cả hai |
| AUTH-05 | Access hết hạn, hai request cần refresh | Chỉ một refresh; replay đúng body/UUID; xoay token đã lưu trước khi dùng | FE |
| AUTH-06 | Logout/login thiết bị khác/reset | Access/refresh cũ bị thu hồi; không quay vào bài bằng phiên cũ | BE |
| AUTH-07 | Forgot/reset thật: sai mã, hết hạn, dùng lại | Generic 202; attempt sai được commit; đúng đổi mật khẩu và thu hồi phiên; không tự login | BE + FE |
| TOPIC-01 | Admin thêm/sửa, tên trùng | 201/200; trùng 409; USER gọi admin 403 | Cả hai |
| TOPIC-02 | Admin tắt topic trong lúc learner chọn | GET learner không còn; create bằng ID tắt 409 | Cả hai |
| TOPIC-03 | Sửa/tắt topic sau khi bài đã mở | Snapshot bài cũ giữ nguyên; bài đã mở tiếp tục được | BE |
| CONV-01 | Create hai lần cùng UUID/body, kể cả response mất | Một conversation; 201 rồi 200; topic tắt sau lần tạo không làm replay thất bại | BE + FE |
| CONV-02 | Cùng create UUID nhưng đổi level/topic | 409 REQUEST_CONFLICT, không tạo bản mới | BE |
| TURN-01 | Gửi câu, retry cùng requestId/content | Một USER và tối đa một AI cùng requestId; kết quả cũ không gọi AI lại | BE + FE |
| TURN-02 | Cùng requestId nhưng đổi content | 409; không sửa ngược câu đã lưu | BE |
| TURN-03 | Hai lượt mới đồng thời | Chỉ một lượt được nhận; lượt kia 409 TURN_PENDING; UI không chồng TTS | Cả hai |
| TURN-04 | Provider reply timeout/response lỗi | USER giữ lại; pendingReply RETRYABLE; retry đúng mã, không nhân bản USER | BE |
| TURN-05 | App mất response rồi mở lại | GET thấy trạng thái thật, không thêm lại AI/user hoặc tự phát lịch sử | FE |
| TURN-06 | USER thứ 21, content rỗng/quá 1000 | Không ghi tin vi phạm; vẫn Kết thúc được | BE + FE |
| END-01 | Bấm Kết thúc khi AI đang chạy | endedAt ghi một lần; response AI muộn không ghi/phát sau đóng; giữ USER đã commit | Cả hai |
| END-02 | Gọi finish lặp/hai lần song song | Không đổi endedAt; không tạo hai báo cáo hợp lệ | BE |
| REVIEW-01 | Ít hơn 3 USER hoặc 30 từ | INSUFFICIENT_DATA; giải thích, không suy ra level/lỗi/từ giả | Cả hai |
| REVIEW-02 | 3+ USER/30+ từ, có lỗi ngữ pháp | COMPLETED đúng cấu trúc; câu gốc lấy DB, correction gắn đúng messageId | Cả hai |
| REVIEW-03 | AI trả correction ID của bài khác/AI message | Từ chối cả báo cáo, FAILED; không có dữ liệu lưu một nửa | BE |
| REVIEW-04 | Provider review timeout/sai JSON; retry | Bài vẫn đóng, transcript/endedAt giữ; FAILED → retry → kết quả hợp lệ | Cả hai |
| REVIEW-05 | Dừng server lúc PROCESSING, khởi động lại | Sau lease90s GET canRetry=true; retry nhận token mới; GET không tự gọi provider | BE |
| REVIEW-06 | Attempt cũ về sau retry | Không ghi đè/xóa dấu xử lý attempt mới | BE |
| REVIEW-07 | A1 hồ sơ chọn B2: bài yếu và bài tốt | Khuyến nghị theo nội dung; không luôn ép A1; users.level không tự đổi | Cả hai |
| WORD-01 | Chọn 1 trong nhiều từ; không chọn từ | Chỉ mục chọn thành saved_words; không chọn vẫn hoàn tất bài | Cả hai |
| WORD-02 | Lưu từ đã có hoặc bấm lưu lặp | Không trùng; existing có ID đúng; không ghi đè nghĩa | BE + FE |
| WORD-03 | Nhóm ID có một từ thuộc người khác/không hợp lệ | Cả request thất bại, không tạo một phần | BE |
| WORD-04 | Lật thẻ, Chưa nhớ, Đã nhớ | Lật không ghi; status/time cập nhật chỉ sau nút đánh giá | Cả hai |
| OWNER-01 | User A đoán ID bài/từ/gợi ý của B | 404, không trả transcript/report/word của B, kể cả client tự sửa body | BE |
| VOICE-01 | Từ chối mic, không có speech service/TTS | Có thông báo và text fallback, không giả trạng thái đang nghe/nói | FE |
| VOICE-02 | TTS đang phát rồi người dùng chạm mic/end | Dừng TTS đúng, không tự ghi âm tiếng AI, không phát câu đến muộn | FE |
| LIFE-01 | Xoay màn hình, xuống nền/quay lại, app đóng khi review | Dừng mic/TTS; khôi phục server; không tạo lại bài hoặc treo loading vô hạn | FE |
| ADMIN-01 | USER hoặc chưa login gọi admin API trực tiếp | 403/401, không phụ thuộc nút có bị ẩn hay không | BE |
| UI-01 | API rỗng/400/401/409/429/502/503/504 | Có đường sửa/thử lại/phục hồi phù hợp; không spinner vô hạn | FE |

## 3. Cách kiểm thử và bằng chứng

- BE dùng unit test cho quyết định nghiệp vụ và MySQL integration cho transaction/UNIQUE/locking. Mock/stub chỉ nằm ở provider/mail/Google boundary, không thay DB bằng một mô hình có semantics khác để chứng minh khóa.
- Kiểm lỗi AI bằng test adapter có nhãn TEST: timeout, sai JSON, sai messageId, response đến muộn. Bản demo kết nối provider thật; không lén dùng adapter trả cố định.
- FE dùng examples.json cho trạng thái UI rồi chạy E2E với server thật; tối thiểu một điện thoại/máy có Google Play services, mic, nhận dạng và giọng tiếng Anh đúng.
- Ghi quan sát DB: số row, message.request_id, conversation.ended_at/review_status; không đưa password/token vào ảnh. Cùng UUID retry mà response đổi kiểu success/existing không nhất thiết sai; side effect không được nhân bản.
- Ghi bảng case ID → PASS/FAIL → build/thiết bị → evidence → lỗi cần sửa. Không gắn PASS hàng loạt vì “build xanh”.

Đo thử ít nhất năm lượt nói: thời điểm chốt nhận dạng, gửi HTTP, nhận reply, bắt đầu TTS; ghi độ trễ thực tế và engine/provider đang dùng. Timeout20/45s là giới hạn xử lý cấu hình, không phải lời hứa AI trả sau đúng số giây đó. Nghe thử là điều kiện nghiệm thu voice; JSON hợp lệ không chứng minh giọng tự nhiên.

## 4. Tiêu chí giao M3

| Nhóm | Điều kiện |
| --- | --- |
| Đường chính | Tất cả F01–F10 P0 của file 01 chạy trên app/server thật; phần cấu hình ngoài được kiểm chứng đúng chức năng |
| Dữ liệu/quyền | OWNER-01, ADMIN-01, TURN-01/02/03, END-01/02, REVIEW-03/04/06, WORD-02/03 không còn lỗi |
| Nội dung | Không nhận xét giữa bài; review có căn cứ; chưa đủ dữ liệu không bịa; mức hồ sơ không bị tự đổi |
| Thiết bị | Mic/TTS thật dùng được; lỗi/quyền bị từ chối có đường tiếp tục; mở lại khôi phục được dữ liệu server |
| Vận hành | Admin topic dùng được, ít nhất 5 tình huống; tài khoản demo riêng; hướng dẫn clone/config/build/run và APK đầy đủ |
| Bản giao | Không secret trong Git, không mock trong P0, build/check đã chạy được ghi cụ thể; lỗi còn lại công bố |

Lỗi nghiêm trọng không chấp nhận: lộ dữ liệu người khác, tạo trùng câu khi retry, mất transcript sau lỗi AI, ghi AI sau đóng, báo cáo lưu dở, cho USER ghi chủ đề, màn hình học trả mẫu cứng nhưng được giới thiệu là AI thật. Vấn đề font/khoảng cách nhỏ có thể ghi backlog nếu không cản sử dụng; không dùng backlog để giấu thiếu một luồng P0.

## 5. Kịch bản trình bày 7–10 phút

1. Nêu vấn đề: người học cần được nói trước rồi nhận góp ý có căn cứ; minh họa vòng học và một MySQL/Spring Boot.
2. Admin thêm một tình huống, cho thấy người học tải được chủ đề mới. Dùng ADMIN riêng để không thay phiên USER trên điện thoại.
3. USER hồ sơ A1 chọn B2, nói khoảng 5 lượt đủ nội dung với AI. Cố ý dùng một câu quá khứ sai; AI vẫn trò chuyện.
4. Kết thúc, xem nhận xét/câu sửa/mức được khuyên; chỉ đúng messageId/câu gốc; giải thích đây là gợi ý luyện, không chấm phát âm/CEFR chính thức.
5. Chọn một từ, lưu, mở Từ của tôi, lật thẻ và đánh dấu; mở lịch sử để thấy bài vẫn còn.
6. Minh họa một ca dữ liệu/quyền đã chuẩn bị: retry cùng UUID không nhân bản USER hoặc A truy cập bài B bị chặn. Nếu mô phỏng provider lỗi, gọi rõ đây là test và cho thấy transcript được giữ/retry được.
7. Mở code để BE chỉ Controller → Service → TransactionService → AI ngoài transaction → Mapper; FE chỉ mic/TTS và trạng thái nhận response.

Không dành phần lớn thời gian khoe login/framework. Độ sâu nằm ở vòng học có căn cứ và xử lý lỗi đúng. Không dùng số lượt test, số bảng hoặc lời hứa 9–10 làm thay bằng chứng sản phẩm.

## 6. Các câu cần tự trả lời

| Câu hỏi | Chỗ tìm lời giải |
| --- | --- |
| Vì sao có ba loại UUID/token? | File 04 mục 3, UNIQUE trong SQL |
| Vì sao không giữ transaction khi đợi AI? | File 04 mục 4; khóa DB và timeout |
| Vì sao đóng bài thành công dù review lỗi? | endedAt commit riêng trước provider; REVIEW_FAILED không xóa transcript |
| Server chết giữa lời đáp/báo cáo thì làm gì? | Lease90s + GET trạng thái + retry có token mới |
| Vì sao không có decks? | Một danh sách saved_words, flashcard là cách hiển thị/ôn |
| Vì sao có cả suggestion và saved_word? | Lịch sử bài giữ mọi gợi ý; người học chỉ lưu một phần và có dữ liệu cá nhân riêng |
| Có khẳng định gọi AI đúng một lần được không? | Không với mạng/provider bên ngoài; kiểm soát kết quả DB hợp lệ và chống trùng |
| Tắt chủ đề có phá bài cũ không? | Snapshot tên/scenario/level, không cập nhật lại lịch sử |
| Vì sao A1 chọn B2 không bị cấm và không luôn khuyên A1? | Ba giá trị level khác nhau; recommendation dựa bài |
| Admin biết hết transcript người học không? | Không; quyền quản trị chủ đề không bỏ ownership của bài |
| Một kỹ thuật nào đã cố ý chưa làm? | P1/luyện lại/streaming được hoãn; có lý do phạm vi và deadline |

## 7. Phiếu kết quả để nhóm điền

| Mục | Kết quả thực tế |
| --- | --- |
| Build/commit/APK/server URL | Chưa điền |
| Máy/OS/speech engine/TTS voice/model AI | Chưa điền |
| Case ID đã PASS/FAIL | Chưa điền |
| Google/SMTP/AI thật đã xác minh | Chưa điền |
| Bug còn lại và ảnh hưởng | Chưa điền |
| Người FE/BE xác nhận, thời điểm | Chưa điền |

Không điền trước các dòng này bằng kết quả của nền auth hoặc bằng fixtures. Bộ tài liệu chung được nghiệm tra về JSON/liên kết; nghiệm thu sản phẩm chỉ có sau khi các chức năng mới thực sự được code và chạy.
