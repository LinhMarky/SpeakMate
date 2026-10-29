# SpeakMate — Bộ tài liệu chung FE và BE

> **Trạng thái mới nhất 30/10/2026:** Docker backend/MySQL hoạt động; một lời đáp Gemini thật HTTP 200 được lưu và đọc lại đúng. Chưa xác nhận vòng học đầy đủ Android sau pull hoặc backend/database demo chung. Đọc [15 — Docker và bàn giao nhóm](15-docker-va-ban-giao-nhom.md) và [README chạy dự án](../../README.md) trước; file 13–14 và các cập nhật bên dưới là lịch sử, không chứng minh E2E hiện tại.

Phiên bản contract **1.0.0**, lập ngày **25/10/2026**. Nhóm có **hai người: một FE, một BE**; người phụ trách BE là người dùng. Thời gian: **ba ngày liên tiếp từ mốc T0 do nhóm chọn**, giao bản demo ở T0 + 72 giờ. Chưa có ngày/giờ nộp cụ thể nên tài liệu không tự gán lịch theo ngày trên máy.

Đây là bộ tài liệu làm việc chung, đối chiếu trực tiếp với code hiện có. Cập nhật 26/10/2026: backend đã triển khai 24 operation P0, kiểm thử bằng MySQL tạm với AI thay tại ranh giới ngoài. Android học tập đã nối API, có mic/TTS và trang admin chủ đề đã có code. Cần xác minh FE–BE với DB nhóm và Gemini/Google/SMTP/giọng nói thật; nghiệm thu sản phẩm vẫn theo các mốc M0–M3. Hướng dẫn chạy và bản đồ code FE tại file 10.

Cập nhật 28/10/2026: đã rà đủ nơi gọi 24 API và chạy PASS vòng học trên giao diện Android với backend 8081/MySQL/Gemini thật, gồm lưu/ôn từ, mở lịch sử và đăng xuất. Google/SMTP và chất lượng mic/TTS còn cần cấu hình/kiểm thật; ba lỗi FE đã review tạm hoãn theo yêu cầu. Kết quả và giới hạn mới nhất ở file 11–12, thay cho trạng thái bị chặn của các đợt cũ.

Cập nhật 29/10/2026: yêu cầu hoàn thiện bản demo đã bao gồm sửa ba lỗi FE nói trên. Tám kiểm thử Android fixture và một vòng Android/MySQL/Gemini thật sau sửa đều PASS; dữ liệu đã đối chiếu JDBC. Hướng dẫn tạo OAuth client/SMTP ở [luồng tài khoản, mục 5–6](../SpeakMate-luong-tai-khoan.md); checklist demo và phần cần chỉ trong code ở [file 12, mục 6](12-bang-noi-api-va-ban-demo.md). Google/email và âm thanh vẫn cần nghiệm thu thủ công; không đánh dấu toàn bộ sản phẩm hoàn tất.

Cập nhật tiếp 29/10: Web OAuth client ID đã điền vào BE/Android và APK đã build lại. SMTP Gmail đã cấu hình, xác thực thật qua Spring Mail và kiểm kết nối lúc Spring Boot khởi động đều PASS; backend vẫn 8081. Chưa kiểm nhận email/đổi mật khẩu trọn vòng, đăng nhập Google thật hoặc mic/TTS. [File 11](11-kiem-chung-moi-truong-thuc-te.md) phân biệt kết nối dịch vụ đã đạt với các bước UI còn cần thực hiện.

## Đọc theo thứ tự

Tiến độ và kiểm chứng mới nhất: [15 — Docker và bàn giao nhóm](15-docker-va-ban-giao-nhom.md). Chi tiết code UI sau pull: [14 — Nối UI Android](14-android-ui-integration.md).

| Tài liệu | Dùng để quyết định gì? |
| --- | --- |
| [13 — Audit tích hợp cuối](13-final-integration-audit.md) | Trạng thái APK hiện tại, bảng 24 API, bằng chứng thật/mock, lỗi đã sửa và điểm chặn demo |
| [01 — Phạm vi và hiện trạng](01-pham-vi-va-hien-trang.md) | Làm gì trong ba ngày, phần nào đã có, phần nào hoãn |
| [02 — Màn hình và nghiệp vụ](02-man-hinh-va-nghiep-vu.md) | FE vẽ/bind UI theo luồng nào; BE phải bảo đảm hành vi nào |
| [03 — Hợp đồng API](03-hop-dong-api.md) | URL, JSON, lỗi, phân trang, chống gửi trùng và cách FE gọi |
| [openapi.json](openapi.json) | Định nghĩa máy đọc được của mọi request/response trong contract |
| [examples.json](examples.json) | Dữ liệu minh họa chung để FE làm UI trong lúc BE triển khai |
| [04 — Kiến trúc và bản đồ code](04-kien-truc-va-ban-do-code.md) | File nào xử lý việc gì; trạng thái, transaction, AI, dữ liệu |
| [05 — Tích hợp và môi trường](05-tich-hop-va-moi-truong.md) | Chạy máy nào, cấu hình gì, giao việc và đổi contract thế nào |
| [06 — Kế hoạch ba ngày](06-ke-hoach-3-ngay.md) | Công việc cụ thể của hai người, mốc bàn giao và cách cắt phạm vi |
| [07 — Kiểm thử và demo](07-kiem-thu-va-demo.md) | Khi nào được coi là xong; ca lỗi và kịch bản bảo vệ |
| [08 — Đọc code và chạy backend](08-doc-code-va-chay-backend.md) | Hàm nào làm gì, cấu hình Gemini, tạo admin, chạy kiểm thử |
| [09 — Đề xuất chiều sâu nghiệp vụ](09-de-xuat-chieu-sau-nghiep-vu.md) | Mục tiêu tình huống, góp ý có hành động, đề xuất dùng lại từ; tách rõ phần đã làm và phần chưa triển khai |
| [10 — Tích hợp Android và admin](10-tich-hop-android-va-admin.md) | Luồng P0 đã nối, bản đồ file FE, cấu hình/chạy và giới hạn kiểm chứng |
| [11 — Kiểm chứng môi trường thực tế](11-kiem-chung-moi-truong-thuc-te.md) | Kết quả mới, lỗi cấu hình, ranh giới mock/thật và thao tác nghiệm thu local |
| [12 — Bảng nối API và bản demo](12-bang-noi-api-va-ban-demo.md) | Nơi gọi đủ 24 API đã triển khai, cách kiểm app với backend thật, cấu hình và phần tạm hoãn |

## Nguồn thống nhất

- **Phạm vi ba ngày:** file 01 và 06. P0 phải đạt; P1 làm sau khi P0 đã qua nghiệm thu.
- **Tên field, URL, kiểu dữ liệu:** openapi.json. File 03 giải thích cách sử dụng; examples.json minh họa đúng schema.
- **Quy tắc nghiệp vụ và lỗi:** file 02, 03, 04. SQL chỉ bảo vệ một phần, không thay kiểm tra quyền/trạng thái ở Service.
- **CSDL:** [speakmate.sql v3](../../speakmate.sql) và [từ điển dữ liệu](../SpeakMate-thiet-ke-co-so-du-lieu.md), vẫn 7 bảng/8 FK. Bộ tài liệu này không thay schema.
- **Code đã triển khai:** thư mục backend và frontend. OpenAPI đánh dấu 24 operation P0 `IMPLEMENTED`; 5 P1 còn `CONTRACT_ONLY`. IMPLEMENTED xác nhận có code và kiểm thử tương ứng, không xác nhận AI/Google/SMTP thật hoặc Android đã tích hợp.

Các tài liệu backend/vòng đời trước đây vẫn giữ vai trò giải thích chuyên sâu. Khi có khác biệt về endpoint hoặc ưu tiên triển khai của đợt ba ngày, dùng contract chung này. Đổi contract phải sửa OpenAPI, ví dụ và ghi chú màn hình cùng lúc; hai người xác nhận trong công việc nhóm trước khi tích hợp.

## Việc đầu tiên tại T0

1. Cả hai đọc 01–03; thống nhất ID màn hình và operationId, không tự đổi tên field.
2. BE chuẩn bị MySQL v3, tài khoản admin, khóa AI, Google OAuth client và SMTP; FE cài app trên điện thoại demo và kiểm tra nhận dạng/giọng đọc tiếng Anh.
3. FE dùng examples.json trong chế độ mock được nhận diện rõ; BE triển khai đúng contract. Bản nghiệm thu phải chuyển về server thật và bỏ phản hồi demo.
4. Cuối ngày 1 phải nói chuyện bằng text với AI thật; cuối ngày 2 phải chạy trọn vòng học trên Android; ngày 3 tích hợp admin, kiểm lỗi, đóng gói và tập demo.

Hai người có khoảng **26 giờ làm việc/người trong ba ngày**, không phải 72 giờ code liên tục. Đây là lịch gấp dựa trên nền auth/UI đang có. Nếu AI hoặc vòng text chưa chạy ở mốc ngày 1, xử lý đường chính trước; không lấy một màn hình có dữ liệu giả để đánh dấu hoàn thành.

## Kiểm tra bộ tài liệu

Ngày 25/10/2026 đã kiểm tra: JSON đọc được; mọi `$ref` nội bộ tìm thấy; operationId không trùng; path parameter khớp URL; 31 response fixture và 6 request mẫu phù hợp phần schema sử dụng (type/null/enum/bắt buộc/giới hạn/oneOf); 9 endpoint đánh dấu IMPLEMENTED khớp method/path trong hai Auth Controller; không có liên kết Markdown nội bộ bị thiếu. Đây là kiểm tra nhất quán tài liệu, không phải test các API mới hoặc một chứng nhận đầy đủ của công cụ OpenAPI bên ngoài.
