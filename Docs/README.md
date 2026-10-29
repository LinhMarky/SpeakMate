# Tài liệu hiện hành của SpeakMate

Cập nhật: **30/10/2026**, giờ Việt Nam. Thành viên mới chạy từ **[README chính](../README.md)**; kết quả Docker hiện tại và trạng thái dữ liệu dùng chung ở [bàn giao nhóm](Contract/15-docker-va-ban-giao-nhom.md).

**FE và BE làm việc từ [bộ tài liệu chung FE–BE](Contract/README.md).** Bộ này chốt phạm vi, màn hình, OpenAPI, ví dụ JSON, phân công **1 FE + 1 BE** và deadline **3 ngày từ T0**. Các tài liệu bên dưới giải thích chuyên sâu; kế hoạch thời gian và API của đợt ba ngày theo bộ chung.

SpeakMate dùng một backend Spring Boot và một MySQL. Người học nói chuyện với AI, xem transcript và chỉ nhận nhận xét khi kết thúc; sau đó có thể lưu từ để ôn. Admin quản lý tình huống, tài khoản và thống kê. Schema hiện tại có **7 bảng**; flashcard là cách ôn danh sách **Từ của tôi**, không cần bộ thẻ.

## Đọc theo việc đang làm

| Cần làm gì? | File cần đọc |
| --- | --- |
| Clone dev, điền .env, chạy Docker/Android đúng URL | [README chính](../README.md), [kiểm API Docker](../README-Docker.md) |
| Review trước push và phân biệt local/demo dùng chung | [Bàn giao Docker và nhóm](Contract/15-docker-va-ban-giao-nhom.md) |
| Cả nhóm bắt đầu dự án/đợt ba ngày | [Mục lục chung FE–BE](Contract/README.md) |
| Chốt dữ liệu FE gửi/nhận và URL | [Hợp đồng API](Contract/03-hop-dong-api.md), [OpenAPI](Contract/openapi.json), [ví dụ JSON](Contract/examples.json) |
| Xem mỗi người làm gì trong ba ngày | [Kế hoạch 1 FE + 1 BE](Contract/06-ke-hoach-3-ngay.md) |
| Chạy backend và xem kết quả kiểm thử | [README backend](../backend/README.md) |
| Đọc từng hàm chat/review/từ, cấu hình Gemini và chuẩn bị admin | [Đọc code backend học tập](Contract/08-doc-code-va-chay-backend.md) |
| Chạy Android/admin, biết màn nào gọi API nào | [Tích hợp Android và admin](Contract/10-tich-hop-android-va-admin.md) |
| Xem kiểm chứng mới nhất và cấu hình dịch vụ thật | [Kiểm chứng môi trường thực tế](Contract/11-kiem-chung-moi-truong-thuc-te.md) |
| Hiểu đăng ký, Google, quên mật khẩu; cấu hình và thử API | [Luồng tài khoản](SpeakMate-luong-tai-khoan.md) |
| Nâng database đã dùng v2 lên v3 | [Migration v3, chạy một lần](migrations/V3__account_auth.sql) |
| Tạo database mới | [speakmate.sql](../speakmate.sql) |
| Hiểu từng bảng/cột, khóa, quan hệ và 3NF | [Thiết kế cơ sở dữ liệu](SpeakMate-thiet-ke-co-so-du-lieu.md) |
| Xem sơ đồ quan hệ để trình bày | Mục 3 trong [Thiết kế cơ sở dữ liệu](SpeakMate-thiet-ke-co-so-du-lieu.md) |
| Hiểu hội thoại, kết thúc, báo cáo và xử lý lỗi | [Vòng đời hội thoại AI](SpeakMate-vong-doi-hoi-thoai-AI.md) |
| Hiểu cách đặt tên, vị trí code và trách nhiệm từng lớp | [Quy ước code](SpeakMate-quy-uoc-code-va-lo-trinh-hieu-he-thong.md) |
| Xem kế hoạch backend dài hạn và căn cứ sản phẩm | [Kế hoạch backend](SpeakMate-ke-hoach-backend.md); lịch đợt này theo bộ chung |
| Tái hiện kiểm tra ràng buộc SQL trên server thử riêng | [Script kiểm tra schema](check_speakmate_schema.py) |

## Bước hiện tại

Backend trong `backend/` dùng Java 21, Gradle Wrapper, Spring Boot và package `vn.tailinh.speakmate`. Đã có 24 operation P0 và schema v3 7 bảng/8 FK, `ddl-auto=validate`. Docker/MySQL và một lời đáp Gemini thật đã được kiểm qua HTTP và SQL. Vòng học đầy đủ trên FE sau pull, Google/email/âm thanh và demo nhiều thiết bị vẫn cần nghiệm thu riêng. Test MySQL với AI stub, Android HTTP fixture và kiểm dịch vụ thật là các bằng chứng khác nhau; đọc file 15 trước các báo cáo lịch sử.

SQL và tài liệu CSDL là nguồn thống nhất về dữ liệu. Tài liệu vòng đời là thiết kế chức năng cần làm, không phải bằng chứng chức năng đã hoạt động. Phiên bản build, lệnh chạy và kết quả kiểm chứng bước nền được ghi trong README backend.

Các tài liệu thiết kế 8 bảng và bộ Entity nháp cũ được bỏ khỏi bộ tài liệu hiện hành để tránh triển khai nhầm. Dùng các đường dẫn trong bảng trên khi tiếp tục code.
