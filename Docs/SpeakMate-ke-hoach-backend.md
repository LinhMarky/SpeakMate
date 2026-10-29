# Kế hoạch phát triển backend SpeakMate

**Đợt làm việc hiện tại có 1 FE + 1 BE và deadline 3 ngày.** Phạm vi P0/P1, phân công, mốc nghiệm thu và API được chốt ở [bộ chung FE–BE](Contract/README.md). Tài liệu này giữ giải thích và hướng phát triển dài hạn; không dùng toàn bộ backlog ở đây làm cam kết hoàn tất trong ba ngày.

Ngày lập: 20/10/2026. Cập nhật: 25/10/2026, giờ Việt Nam. Trạng thái: SQL v3 có 7 bảng; đã có nền User/Topic và luồng đăng ký, đăng nhập, Google, refresh, logout, quên mật khẩu. Android có đăng nhập email/Google, hồ sơ, liên kết và đăng xuất; các màn hình học còn demo. Google/SMTP thật cần cấu hình. Bước tiếp theo là quản lý chủ đề. [Luồng tài khoản](SpeakMate-luong-tai-khoan.md) chỉ rõ từng file; hướng dẫn chạy/kiểm chứng ở [README backend](../backend/README.md).

Ưu tiên sản phẩm là [vòng đời hội thoại giọng nói với AI](SpeakMate-vong-doi-hoi-thoai-AI.md): nói chuyện tự nhiên có transcript, chỉ nhận xét sau khi kết thúc, tùy chọn lưu từ thành flashcard. Cơ sở dữ liệu theo [thiết kế 7 bảng hiện tại](SpeakMate-thiet-ke-co-so-du-lieu.md), bỏ thao tác tạo bộ thẻ. [Mục lục tài liệu](README.md) chỉ dẫn những file đang dùng.

SpeakMate sẽ dùng Spring Boot và MySQL. Người thực hiện đảm nhận nhóm trưởng và toàn bộ backend. Thứ tự ưu tiên đã thống nhất: nghiệp vụ đầy đủ trước, mô hình dữ liệu tối giản hợp lý sau. Mục tiêu là một sản phẩm có tính thực tế và người thực hiện có thể tự giải thích, kiểm tra, sửa code. Điểm 9+ là mục tiêu phấn đấu; kế hoạch này không phải cam kết điểm hay bằng chứng rằng sản phẩm đã hoàn thành.

Phong cách tham khảo là backend InternMatching tại commit `80fb0711c70d06ad7cb6bcabd25b782138964121`. Repo này chỉ được đọc để tham khảo. Bước nền áp dụng tên package, Entity, enum và audit quen thuộc; các quy ước cho API/Service được triển khai khi đến từng chức năng.

Cập nhật sau khi đối chiếu thêm mã nguồn: người dùng đã đồng ý sáu cải tiến về DTO, exception/transaction, xác thực và quyền sở hữu, response phân trang có kiểu, luồng tự bọc response và ranh giới AI. Chi tiết, vị trí file và cách kiểm chứng được ghi tại [mục 5 của tài liệu quy ước code](SpeakMate-quy-uoc-code-va-lo-trinh-hieu-he-thong.md). Điều kiện áp dụng là người phụ trách tự giải thích được từng luồng. ERD và schema v3 đã được chốt theo nghiệp vụ; không cần làm lại bước thiết kế từ đầu.

## Những nguyên tắc đã thống nhất

- Giữ cấu trúc Controller, Service, Repository, Entity, DTO và Mapper quen thuộc.
- Giữ cách viết nghiệp vụ tuần tự, tên tiếng Anh rõ nghĩa và Mapper viết tay.
- Áp dụng các cải tiến đã trao đổi: DTO nhất quán; validation đầu vào; exception có ý nghĩa; transaction đúng ranh giới; kiểm tra chủ sở hữu; phân biệt access token với refresh token; kiểm thử nghiệp vụ.
- Thiết kế dữ liệu theo hướng đơn giản, kiểm tra 3NF, không thêm bảng hoặc trường thiếu mục đích. Schema hiện tại có bảy bảng, tính cả bảng nối; không bỏ nghiệp vụ cần thiết hoặc giấu quan hệ vào JSON để đạt số bảng. Nếu cần thêm bảng, nêu chính xác nghiệp vụ đòi hỏi nó trước khi thay đổi ERD.
- Có hai vai trò người học và quản trị viên. Trang quản trị có giao diện dùng được và chức năng được kiểm soát ở backend.
- Điểm nhấn đề xuất là nối hội thoại, phản hồi, ôn từ và luyện lại có mục tiêu; mỗi nhận xét cần gắn với nội dung người học đã thực sự viết hoặc nói qua nhận dạng văn bản.
- Mỗi lần triển khai tập trung một luồng nhỏ. Người thực hiện phải lần được đường đi của dữ liệu và lý do của từng quyết định.
- Tài liệu và ảnh ban đầu là tham khảo chức năng. Không mặc định kế thừa kiến trúc Firebase hoặc toàn bộ tính năng trên ảnh.
- Contract và endpoint dùng chung đã có tại [OpenAPI 1.0.0](Contract/openapi.json), phân biệt rõ auth đã có và API cần triển khai.

## Căn cứ xây dựng mục tiêu 9+

Ba trọng số dưới đây do người dùng cung cấp. Nội dung minh chứng là đề xuất xây dựng đồ án, không phải tiêu chí chi tiết đã được giảng viên xác nhận.

| Tiêu chí | Trọng số | Năng lực cần thể hiện | Minh chứng dự kiến |
| --- | --- | --- | --- |
| Độ khó | 30% | Hội thoại nhiều lượt có ngữ cảnh; lựa chọn mục tiêu luyện lại; phân quyền; dữ liệu nhất quán khi AI hoặc mạng lỗi | Giải thích luồng thật; demo retry không sinh trùng; kiểm thử sai quyền, rollback và chuyển trạng thái |
| Tâm huyết | 30% | Hoàn thiện mobile, trang quản trị và các tình huống sử dụng thực tế; có nội dung học được biên soạn, kiểm tra | Chạy trọn luồng trên điện thoại; tình huống theo trình độ; dữ liệu demo; tài liệu chạy; ghi nhận phản hồi người dùng thử |
| Ý tưởng và giải quyết vấn đề | 40% | Giúp người học sử dụng lại điều vừa học trong hội thoại tiếp theo, có căn cứ để nhìn thấy điểm còn yếu | Một chuỗi phiên học liên quan; từ mục tiêu; câu đã sử dụng; phản hồi cụ thể; giải thích giới hạn của AI |

Không dùng số bảng, số màn hình, số công nghệ hoặc độ dài code làm đại diện cho chất lượng. Phần sáng tạo cần tập trung vào trải nghiệm học liên tục và bằng chứng hữu ích cho người học. Các con số sử dụng và kết quả người dùng thử chỉ được đưa vào báo cáo khi đã đo thực tế.

## Phạm vi đề xuất cho bản nộp

Luồng sản phẩm cần hoàn thiện: đăng nhập → chọn tình huống và mức luyện → hai bên nói chuyện, hiện transcript → bấm Kết thúc → nhận xét/khuyến nghị mức luyện/lỗi ngữ pháp/từ mới → tùy chọn lưu từ → ôn flashcard. Luyện lại từ trong tình huống mới triển khai sau khi vòng chính ổn định. Quản trị viên vận hành chủ đề và tài khoản qua trang web quản trị.

| Nhóm chức năng | Nội dung thực hiện | Giới hạn giúp giữ dự án gọn |
| --- | --- | --- |
| Tài khoản | Đăng ký, đăng nhập, refresh, đăng xuất, hồ sơ, đổi mật khẩu, trình độ mặc định | Vai trò USER/ADMIN cố định; người đăng ký không tự chọn ADMIN |
| Speaking | Chọn chủ đề và A1/A2/B1/B2; hội thoại nhiều lượt tự nhiên; kết thúc mới nhận xét | Mỗi phiên chỉ nhận một tác vụ AI hợp lệ; kết thúc vô hiệu hóa lời đáp còn chạy |
| Giọng nói hai phía | Android nhận dạng lời người học, hiện text; tự đọc lời AI bằng TTS | Bắt buộc trong vòng chính; nói theo lượt; backend xử lý văn bản, chưa lưu audio |
| Lịch sử | Danh sách phiên, transcript, góp ý, tổng kết | Phiên đã kết thúc dùng để xem lại; chưa hỗ trợ tiếp tục phiên đã đóng |
| Flashcard | Lưu, sửa, xóa từ cá nhân; xem dưới dạng thẻ để ôn | Một danh sách Từ của tôi; không cần bộ thẻ |
| Ôn tập | Lật thẻ; đánh dấu mới, đang học, đã nhớ; lưu trạng thái gần nhất | Chưa lưu lịch sử chi tiết từng lần ôn hoặc thuật toán ôn cách quãng |
| Liên kết | Chọn từ đề xuất trong kết quả để lưu vào Từ của tôi | Tùy chọn; kiểm tra chủ sở hữu và trùng từ; không tự lưu mọi gợi ý |
| Luyện lại có mục tiêu | Chọn một nhóm nhỏ từ/cụm từ cá nhân cần luyện, tạo hội thoại có ngữ cảnh để sử dụng; cuối phiên chỉ ra câu đã dùng và góp ý | Quy tắc chọn mục tiêu rõ ràng; phiên nào đã luyện mục tiêu nào phải truy vết được |
| Tiến độ cá nhân | Số phiên đã đóng/có báo cáo đầy đủ, số từ đã lưu; trạng thái ôn và kết quả dùng từ | Định nghĩa từng số liệu riêng; không quy thành chứng nhận trình độ |
| Quản trị | Đăng nhập quản trị; tạo/sửa/bật/tắt chủ đề và tình huống; tìm/khóa/mở khóa người dùng; thống kê | Có giao diện thật; mọi thao tác được kiểm tra quyền tại backend; không quản trị nội dung riêng tư nếu không có nghiệp vụ |

Nhận dạng văn bản không đủ để kết luận về chất lượng phát âm. Phản hồi sau hội thoại tập trung vào ngữ pháp, từ vựng và diễn đạt. Đọc lời AI thành tiếng là phần bắt buộc; chất lượng giọng và nhịp chờ phải được thử trên điện thoại thật. Mặc định tự gửi bản nhận dạng cuối; có chế độ xác nhận trước khi gửi nếu cần sửa bản nháp.

Chức năng mở rộng như hội thoại giọng nói liên tục, chia sẻ bộ thẻ, mạng xã hội, bảng xếp hạng, thanh toán hoặc thuật toán ôn cách quãng nâng cao chỉ xem xét sau khi luồng chính đạt nghiệm thu. Các chức năng đó không phải điều kiện mặc định để hướng tới 9+. Luồng quên mật khẩu qua email cần được chốt cùng nghiệp vụ tài khoản nếu bản nộp có yêu cầu tự khôi phục tài khoản; không hứa có email khi chưa tích hợp và thử thật.

## Điểm nhấn: học xong có cơ hội sử dụng lại

Ví dụ minh họa: trong tình huống nhận phòng khách sạn, người học chưa biết cách nói “I'd like to check in”. App đưa ra góp ý, cho lưu cụm từ, hỗ trợ ôn và ở phiên sau tạo một tình huống phù hợp để dùng lại. Cuối phiên, app dẫn lại câu thực tế và giải thích mức độ phù hợp. Ví dụ này minh họa nghiệp vụ, không phải kết quả đo chất lượng mô hình.

| Bước | Hành vi sản phẩm | Dữ liệu hoặc căn cứ cần giữ |
| --- | --- | --- |
| Hội thoại | Người học trao đổi theo tình huống và level | Transcript theo đúng thứ tự lượt |
| Phản hồi sau Kết thúc | AI nhận xét, sửa câu và gợi ý từ/cụm từ hữu ích | Góp ý gắn với câu USER có thật; đề xuất gắn với phiên |
| Ôn | Người học chọn lưu, sửa nội dung nếu cần và tự đánh giá đã nhớ/chưa vững | Thẻ cá nhân và trạng thái ôn hiện tại |
| Chọn mục tiêu | Đề xuất một nhóm nhỏ thẻ đang học hoặc chưa nhớ; người học xác nhận | Danh sách mục tiêu cố định cho phiên mới; thứ tự chọn có quy tắc |
| Luyện lại | AI tạo cơ hội sử dụng từ trong tình huống hợp lý | Mục tiêu nằm trong ngữ cảnh gửi AI; không chỉ mở một cuộc chat chung |
| Đối chiếu | Hiển thị câu người học đã dùng, nhận xét dùng đúng/chưa phù hợp/chưa có bằng chứng | Tham chiếu tin nhắn có thật; backend kiểm tra ID thuộc phiên và trích dẫn khớp nội dung |

Phương án chọn mục tiêu dễ giải thích: lọc thẻ của chính người học ở trạng thái cần học, ưu tiên thẻ lâu chưa ôn, rồi để người học xác nhận. Chưa cần mô hình machine learning riêng để gọi chức năng này là cá nhân hóa. Số mục tiêu nhỏ, chẳng hạn tối đa ba, là tham số đề xuất cần thử với hội thoại thật.

Không coi “AI nói người học tiến bộ” là đủ bằng chứng. Lưu câu thực tế, bối cảnh và tiêu chí nhận xét; trường hợp chưa sử dụng từ hoặc câu quá ngắn cần trả kết quả chưa đủ bằng chứng. So sánh những mục tiêu tương ứng qua các phiên, không lấy điểm AI ở hai bài khác nhau rồi khẳng định tăng trình độ. Nhận xét AI vẫn cần được kiểm tra bằng bộ ví dụ do nhóm đọc và đánh giá thủ công.

## Trang quản trị cần vận hành được

| Nghiệp vụ | Hành vi cần có | Ca kiểm chứng |
| --- | --- | --- |
| Chủ đề và tình huống | Tạo, xem, sửa nội dung/level phù hợp; bật hoặc ngừng cho chọn | Chủ đề tắt không tạo được phiên mới kể cả gọi API trực tiếp; lịch sử cũ vẫn xem được |
| Người dùng | Tìm kiếm, phân trang, xem thông tin cần quản lý, khóa/mở khóa | Tài khoản đang bị khóa bị từ chối ở lần truy cập tiếp theo, kể cả có access token chưa hết hạn |
| Thống kê | Người dùng đang hoạt động/bị khóa; phiên đã hoàn thành theo khoảng ngày; mức sử dụng chủ đề | Có định nghĩa và truy vấn đối chiếu được; số liệu phản ánh DB thật |
| Phân quyền | Chỉ quản trị viên hợp lệ được sử dụng chức năng quản trị | Tài khoản USER gọi URL hoặc request quản trị bị từ chối; không chỉ ẩn nút trên giao diện |

Hướng triển khai đề xuất: web quản trị dùng Thymeleaf trong cùng ứng dụng Spring Boot để giới hạn số nền tảng phải học thêm. Đây là lựa chọn cho phạm vi hiện tại, không phải yêu cầu bắt buộc nếu nhóm đã có thành viên phụ trách web bằng công nghệ khác.

Nếu dùng Thymeleaf: controller quản trị trả view, controller mobile trả JSON; cả hai dùng chung Service nghiệp vụ. Web dùng phiên đăng nhập và bảo vệ CSRF cho thao tác thay đổi dữ liệu; mobile dùng cơ chế token được thiết kế riêng. Quy tắc kiểm tra vai trò và trạng thái tài khoản áp dụng ở backend trên cả hai đường truy cập. Không tắt CSRF toàn ứng dụng khi có trang web dùng cookie đăng nhập.

Tắt chủ đề, khóa tài khoản và thay đổi nội dung phải có quy tắc bảo toàn lịch sử. Đề xuất cho phép phiên đã mở tiếp tục với bối cảnh được ghi nhận lúc mở; chủ đề đã được sử dụng không bị xóa cứng. Admin không tự khóa tài khoản đang dùng và không có giao diện tự cấp quyền tùy ý. Khởi tạo tài khoản quản trị bằng quy trình riêng với thông tin bí mật ngoài mã nguồn.

## Kiến trúc và phong cách code

Một ứng dụng Spring Boot xử lý nghiệp vụ, một MySQL lưu dữ liệu. Android và trang quản trị đi qua cơ chế xác thực, phân quyền tương ứng rồi dùng chung nghiệp vụ. Backend gọi dịch vụ AI khi cần; khóa AI đặt ở backend. Kiến trúc một ứng dụng là đủ cho phạm vi này; độ sâu nằm ở quy tắc và cách xử lý dữ liệu.

| Package | Trách nhiệm | Quy ước áp dụng |
| --- | --- | --- |
| `controller` | Nhận request, kích hoạt validation, trả response/view và xử lý chi tiết HTTP | Gọn; không truy vấn Repository trực tiếp; tách controller quản trị nếu dùng view |
| `service` | Thực hiện use case, kiểm tra chủ sở hữu, điều phối lưu dữ liệu | Class cụ thể; constructor injection; hàm theo nghiệp vụ |
| `repository` | Truy vấn và lưu Entity | Derived query cho truy vấn đơn giản; truy vấn riêng khi thật sự cần |
| `entity` | Ánh xạ cấu trúc lưu trữ | Không dùng làm request hoặc response trực tiếp |
| `dto/request` | Dữ liệu client được phép gửi | Chỉ có trường cần thiết; validation ngay tại đầu vào |
| `dto/response` | Dữ liệu trả cho mobile hoặc phục vụ giao diện quản trị | Kiểu dữ liệu rõ ràng; tái sử dụng khi cùng cấu trúc |
| `util/mapper` | Chuyển Entity sang DTO | Getter/setter tường minh; không truy vấn DB, không mở transaction |
| `util/constant` | Enum và hằng có ý nghĩa | Enum lưu bằng tên, không theo thứ tự số |
| `exception` | Lỗi nghiệp vụ và xử lý lỗi tập trung | Phân biệt dữ liệu sai, không tìm thấy, không có quyền, xung đột |
| `security`, `config` | Xác thực, cấu hình và các thành phần dùng chung | Tách khỏi nghiệp vụ Speaking và Flashcard |
| `service/ai` | Giao tiếp với nhà cung cấp AI | Interface `AiClient` nhỏ; tách việc gọi HTTP khỏi Service nghiệp vụ |

Bước nền dùng Java 21, Gradle và Spring Boot tại `backend/`, package `vn.tailinh.speakmate`. Phiên bản dependency được khai báo trong file build; lệnh chạy và cấu hình MySQL nằm ở [README backend](../backend/README.md). Không lấy phiên bản Spring Boot trong repo cũ làm yêu cầu cố định.

| Nội dung | Quy ước đề xuất |
| --- | --- |
| Tên class | Danh từ tiếng Anh, PascalCase: `ConversationService`, `SavedWordMapper` |
| Tên biến | camelCase, chỉ rõ vai trò: `request`, `currentUser`, `existingWord`, `response` |
| Hàm nghiệp vụ | `startConversation`, `sendMessage`, `finishConversation`, `saveWordsFromSuggestions` |
| Request DTO | `CreateConversationDTO`, `SendMessageDTO`; giữ hậu tố DTO quen thuộc |
| Response | `CreateConversationResponse`, `FetchConversationResponse`; tái sử dụng khi cấu trúc giống nhau |
| Mapper | `ConversationMapper.toFetchConversationResponse(...)`; tên hàm chỉ rõ DTO được tạo |
| ID | `Long id`, hướng tự tăng như dự án cũ |
| Tên bảng và cột | Tên bảng số nhiều; snake_case cho cột và khóa ngoại |
| Thời điểm | `Instant` cho thời điểm lưu; chuyển múi giờ khi hiển thị |
| Phân trang | `ResultPaginationResponse<T>` và danh sách có kiểu cụ thể |
| Auditing | Giữ `AbstractAuditingEntity` và các tên `createdAt`, `createdBy`, `updatedAt`, `updatedBy`; bỏ generic không được sử dụng |

DTO tham chiếu dữ liệu khác bằng ID khi phù hợp, không chứa Entity. Người dùng hiện tại lấy từ xác thực, không lấy một `userId` tùy ý trong request làm căn cứ cấp quyền. Có một thành phần dùng chung để lấy tài khoản hiện tại nhằm tránh lặp cách kiểm tra tại nhiều Service.

Mapper có thể là helper static vì chỉ chuyển dữ liệu. Annotation transaction không đặt trên helper static. Ranh giới transaction nằm ở phương thức nghiệp vụ của Spring bean và phải được kiểm thử rollback. Đã thống nhất exception nghiệp vụ cần hủy thao tác kế thừa `RuntimeException`; lỗi không mong đợi trả thông báo an toàn, chi tiết phục vụ chẩn đoán nằm ở log backend. Cách kiểm chứng và đường đi qua handler được mô tả trong tài liệu quy ước code.

Tiếp tục giữ Service là class cụ thể, `@RequiredArgsConstructor`, dependency `private final`, Mapper viết tay và cách xử lý tuần tự. Không cần tạo cặp Service/ServiceImpl cho mọi chức năng. Không thêm base CRUD tổng quát chỉ để giảm vài dòng nếu làm khó theo dấu nghiệp vụ. Kiểm tra quyền theo vai trò có thể dùng Spring Security; kiểm tra tài nguyên thuộc ai vẫn cần thể hiện rõ trong use case.

## Thiết kế dữ liệu hiện tại

Schema hiện tại là [speakmate.sql v3](../speakmate.sql), giải thích tại [tài liệu CSDL](SpeakMate-thiet-ke-co-so-du-lieu.md). Mã backend chính thức nằm tại `backend/`; đã ánh xạ User/Topic và triển khai auth, các Entity còn lại bổ sung theo từng luồng.

| Nhóm dữ liệu | Số bảng | Lý do tồn tại |
| --- | --- | --- |
| Người dùng | 1 | Danh tính, vai trò cố định, trạng thái tài khoản và thiết lập cá nhân |
| Chủ đề hội thoại | 1 | Danh mục chủ đề và bối cảnh dùng để bắt đầu phiên |
| Hội thoại — conversations | 1 | Bối cảnh, mức luyện, kết thúc, trạng thái phân tích và nhận xét |
| Tin nhắn — messages | 1 | Câu gốc hai phía; chỉ gắn sửa lỗi vào câu USER sau kết thúc |
| Từ được gợi ý — word_suggestions | 1 | Các từ trong kết quả bài, kể cả khi người học không lưu |
| Từ cá nhân — saved_words | 1 | Người sở hữu trực tiếp, nội dung học và trạng thái ôn |
| Mục tiêu luyện lại — word_practices | 1 | Một từ được chọn cho một hội thoại; giữ nội dung lịch sử và bằng chứng |

Bảy bảng đã tính cả word_practices cho quan hệ luyện lại; không có decks. Trang quản trị không mặc định cần bảng riêng: tài khoản có vai trò, chủ đề có trạng thái và thống kê được truy vấn từ dữ liệu hiện có. Luyện lại có mục tiêu triển khai sau vòng hội thoại chính, không là điều kiện bắt đầu một cuộc trò chuyện thông thường.

Số bảng tính cả bảng nối. Bảy bảng là kết quả của phạm vi nghiệp vụ hiện tại, không phải chứng cứ đạt 3NF. Phân tích khóa và phụ thuộc hàm nằm trong tài liệu CSDL; snapshot giữ đúng dữ liệu lịch sử.

Quy tắc kiểm tra khi thiết kế:

1. Xác định khóa ứng viên và phụ thuộc hàm; kiểm tra 1NF, 2NF, 3NF trên từng quan hệ. Một cột ID tự tăng không tự chứng minh chuẩn hóa.
2. Mỗi thuộc tính phải có nhu cầu nghiệp vụ. Với dữ liệu chưa rõ mục đích, để ngoài thiết kế cho đến khi có lý do cụ thể.
3. Ưu tiên tính số thẻ, số lượt, số phiên và tỷ lệ bằng truy vấn. Không lưu các bộ đếm lặp khi chưa có nhu cầu hiệu năng được chứng minh.
4. Từ cá nhân thuộc trực tiếp users qua saved_words.user_id; tin nhắn/gợi ý/mục tiêu đi qua conversations để kiểm tra chủ sở hữu.
5. Trạng thái ôn hiện tại nằm trên saved_words; chia sẻ nội dung học cho nhiều người sẽ cần xem lại quan hệ tiến trình.
6. Mức hồ sơ lúc mở phiên, mức được chọn và mức khuyến nghị cuối bài có ý nghĩa riêng. Đổi hồ sơ không sửa lịch sử; một nhận xét AI không tự đổi users.level.
7. Từ vựng AI đề xuất và flashcard mà người dùng đã lưu, chỉnh sửa có vòng đời khác nhau. Cần giải thích rõ trước khi quyết định liên kết hoặc sao chép nội dung.
8. Tổng kết AI là kết quả cần giữ để xem lại; không coi nó như một bộ đếm có thể tính lại miễn phí.
9. Enum chỉ dùng cho tập giá trị nhỏ, ổn định như level hoặc trạng thái. Dữ liệu có mô tả và nội dung riêng như chủ đề cần cân nhắc bảng.
10. Ràng buộc không trùng ở Service và DB phải bảo đảm cùng quy tắc; kiểm tra cả trường hợp gửi đồng thời.
11. Bối cảnh và mục tiêu đã dùng trong một phiên là dữ liệu lịch sử. Việc sửa chủ đề/từ cá nhân sau đó không được khiến người đọc hiểu sai phiên cũ; SQL v3 giữ snapshot tại lúc mở bài/chọn luyện.
12. Ba thuộc tính “người học tự đánh dấu nhớ”, “AI nhận xét dùng phù hợp” và “đã xuất hiện trong câu nào” có ý nghĩa khác nhau. Không lưu gộp thành một điểm chung thiếu định nghĩa.

ERD/từ điển dữ liệu và SQL v3 đã đồng bộ, kiểm tra trên MySQL tạm. Bước nền ánh xạ User/Topic bằng `ddl-auto=validate`; các Entity và use case tiếp theo phải được kiểm tra khi triển khai, không coi SQL hợp lệ là bằng chứng API đã đúng.

## Những quy tắc phải làm rõ trước khi code

| Vấn đề | Hướng đề xuất ban đầu | Điều cần kiểm tra |
| --- | --- | --- |
| Kết thúc / có báo cáo | ended_at ghi khi đóng; chỉ review_status = COMPLETED là có báo cáo đầy đủ | Hội thoại quá ngắn vẫn đóng, ghi INSUFFICIENT_DATA; không bịa nhận xét |
| Trình độ phiên | Ghi nhận khi bắt đầu; giữ nguyên trong phiên | Hồ sơ thay đổi không tác động phiên cũ |
| Trùng từ cá nhân | Một mục cho mỗi cách viết trong Từ của tôi; chuẩn hóa khoảng trắng/hoa thường | UNIQUE(user_id, word); chưa tách nhiều nghĩa thành các thẻ cùng chữ |
| Lưu từ sau bài | Chỉ lưu các gợi ý người dùng chọn | Không chọn gì vẫn hoàn thành hội thoại; thư viện rỗng là trạng thái hợp lệ |
| Thẻ AI | Sinh bản xem trước, người dùng sửa rồi xác nhận lưu | Không xuất hiện trong thư viện trước xác nhận; lúc lưu phải validate lại |
| Phiên ôn flashcard | Lưu trạng thái gần nhất ngay sau mỗi lựa chọn | Kết quả của lượt đang học có thể ở trạng thái giao diện; chưa lưu lịch sử từng lượt ôn |
| Chủ đề | Admin quản lý danh mục và tình huống; thẻ AI có thể có mô tả chủ đề nhập tự do | Tắt chủ đề chỉ ngăn phiên mới; cần giữ đúng bối cảnh của phiên cũ khi sửa nội dung |
| Mục tiêu luyện lại | Người học xác nhận từ/cụm từ sẽ luyện trước khi mở phiên | Giữ được danh sách lịch sử; thẻ bị sửa/xóa không làm sai bằng chứng cũ |
| Đánh giá dùng từ | Nhận xét gắn với mục tiêu và câu thực tế trong phiên | Chưa có câu phù hợp thì ghi chưa đủ bằng chứng; có bộ ví dụ kiểm tra kết quả AI |
| Khóa tài khoản | Mọi lần truy cập mới kiểm tra trạng thái tài khoản từ backend | Cần xét cả JWT còn hạn, phiên web còn hạn và yêu cầu AI đang chạy khi bị khóa |
| Refresh session | Đề xuất bản đầu chỉ giữ một refresh session hiện hành mỗi tài khoản | Đăng nhập mới thay refresh cũ; access cũ có thể còn hiệu lực đến hết hạn |
| Đăng xuất | Thu hồi refresh session và xóa thông tin xác thực trên thiết bị | Phải mô tả đúng thời điểm access token hết hiệu lực |
| Đổi mật khẩu | Kiểm tra mật khẩu hiện tại, lưu bản băm mới và xác định phiên nào bị thu hồi | Không ghi mật khẩu/token vào log; không để client tự thay quyền tài khoản |

Các hướng đề xuất trong bảng chưa thay thế việc chốt nghiệp vụ ở chặng tiếp theo. Đặc biệt, hỗ trợ nhiều thiết bị hoặc yêu cầu đăng xuất làm mất hiệu lực mọi access token ngay lập tức sẽ cần quyết định kỹ thuật riêng.

## Tích hợp AI theo cách có thể giải thích được

Service quyết định quyền truy cập và quy tắc lưu dữ liệu. AI client chỉ giao tiếp với nhà cung cấp và chuyển kết quả về kiểu dữ liệu được kiểm tra. AI không tự quyết định chủ sở hữu, ID hay quyền ghi dữ liệu.

Đề xuất dùng một interface nhỏ `AiClient` khi triển khai tích hợp: có implementation gọi nhà cung cấp và bản giả lập chỉ dùng trong kiểm thử. Đây là chỗ có nhu cầu thay thế cụ thể; các Service CRUD vẫn là class đơn giản. Bản demo chức năng AI phải dùng tích hợp thật; dữ liệu giả lập phải được nhận diện rõ là phục vụ phát triển hoặc kiểm thử.

Hai hàm chính là `generateReply` để trò chuyện và `reviewConversation` sau Kết thúc. Không sinh hoặc phát lời sửa lỗi trong lượt nói. Bản đồ file, transaction ngắn và xử lý kết thúc khi AI còn chạy ở [mục 6–8 của vòng đời AI](SpeakMate-vong-doi-hoi-thoai-AI.md).

Một lượt hội thoại cần xử lý theo ý nghĩa sau:

1. Xác thực người dùng và kiểm tra phiên thuộc người đó, còn cho phép gửi tin.
2. Kiểm tra nội dung, yêu cầu trùng và thứ tự lượt. Chốt cơ chế bảo đảm chỉ một yêu cầu AI đang chạy trong phiên.
3. Lưu lượt người học và trạng thái xử lý bằng thao tác DB ngắn.
4. Tải ngữ cảnh cần thiết, xây prompt, gọi AI ngoài transaction DB kéo dài.
5. Kiểm tra cấu trúc phản hồi, giới hạn độ dài và các trường cần có.
6. Kiểm tra token xử lý và phiên chưa đóng, lưu riêng câu trả lời AI rồi xóa dấu xử lý. Chưa ghi góp ý/ngữ pháp/từ mới ở bước này.
7. Nếu thất bại, giữ câu người học, ghi nhận trạng thái phù hợp và cho phép thử lại. Yêu cầu đã hoàn thành nhưng client mất phản hồi không được tạo lại tin nhắn trùng.

`@Transactional` không hoàn tác được một lần gọi nhà cung cấp AI. Vì vậy cần thiết kế trạng thái và thử lại rõ ràng. Khi kết thúc phiên, lỗi tạo tổng kết không được làm mất transcript; việc kết thúc phiên và trạng thái tạo tổng kết cần phân biệt.

Kiểm tra sớm khả năng gọi AI bằng một thử nghiệm nhỏ, đo thời gian và kiểm tra JSON trước khi làm nhiều màn hình phụ thuộc AI. Nhà cung cấp, model, chi phí và giới hạn sử dụng chưa được chọn trong kế hoạch này.

Với luyện lại, AI nhận mục tiêu đã được backend xác định, không tự chọn ID từ nguồn ngoài. Đầu ra nhận xét phải tham chiếu các tin nhắn trong phiên; backend kiểm tra tham chiếu và đoạn trích, đồng thời nhóm kiểm tra chất lượng diễn giải bằng các ví dụ có đáp án kỳ vọng. Nội dung do người học gửi được coi là dữ liệu hội thoại, không được phép sửa quy tắc hệ thống hoặc quyết định quyền truy cập. Đặt giới hạn độ dài đầu vào, số lượt/ngữ cảnh và timeout để kiểm soát độ trễ, chi phí.

Không tuyên bố bảo đảm nhà cung cấp AI chỉ bị gọi hoặc tính phí một lần trong mọi tình huống lỗi mạng. Mục tiêu có thể kiểm chứng trước mắt là bản ghi và kết quả ứng dụng không bị nhân đôi khi retry; quy tắc retry nhà cung cấp phải được giải thích riêng.

## Các chặng triển khai và tiêu chí hiểu bài

Nghiệp vụ chính và schema v3 đã có. Từ ngày 24/10/2026, thứ tự dưới đây bắt đầu ở bước code nền; không quay lại phân tích từ đầu. Chưa có hạn nộp và thời gian làm mỗi ngày, nên kế hoạch dùng mốc hoàn thành. Kiểm thử và ghi chép thực hiện trong từng chặng.

| Chặng | Công việc | Kết quả chạy được hoặc tài liệu cần có | Người thực hiện phải giải thích được |
| --- | --- | --- | --- |
| 1 | Dựng nền backend | Project Java 21/Gradle/Spring Boot; kết nối MySQL; User, Topic, AbstractAuditingEntity, Role, EnglishLevel; validate ánh xạ | Mỗi file dùng làm gì; enum khác bảng; SQL và Entity liên hệ thế nào; audit chạy khi nào |
| 2 | Hoàn thành tài khoản | Đăng ký/đăng nhập, role, trạng thái khóa, hồ sơ, đổi mật khẩu; format lỗi; test token và quyền | Request qua Security ra sao; vai trò, chủ sở hữu và trạng thái khác nhau thế nào |
| 3 | Trang quản trị tối thiểu và danh mục chủ đề | Giao diện đăng nhập; tạo/sửa/bật/tắt chủ đề; quản lý tài khoản | Vì sao ẩn nút chưa đủ; trạng thái chủ đề/tài khoản ảnh hưởng use case nào |
| 4 | Hội thoại AI và giọng nói hai phía | Thử AI nhỏ → text có ngữ cảnh → mic/TTS; ownership, retry, không nhận xét giữa phiên | Dữ liệu trước/sau AI; thứ tự lượt; chỗ nào mobile xử lý |
| 5 | Kết thúc và nhận xét đầy đủ | Lịch sử; ưu/nhược điểm; ngữ pháp; mức luyện; từ mới; thiếu dữ liệu; retry phân tích | Đóng phiên khác phân tích thành công; kết quả cũ đến muộn được chặn ra sao |
| 6 | Nối từ hội thoại, ôn và luyện lại có mục tiêu | Chọn lưu, ôn, mở phiên mới theo mục tiêu, chỉ ra câu làm bằng chứng | Quy tắc cá nhân hóa; quan hệ dữ liệu; cách kiểm tra nhận xét AI |
| 7 | Hoàn thiện tích hợp mobile, thống kê và sử dụng thực tế | Luồng trên điện thoại; dashboard thật; phản hồi người dùng thử | Ranh giới Android/backend; cách tính số liệu; phân biệt bằng chứng với nhận xét |
| 8 | Nghiệm thu và luyện bảo vệ | Hướng dẫn chạy; demo, ca lỗi, tài liệu quyết định; tự sửa một yêu cầu nhỏ | Theo dấu một request, giải thích SQL và bảo vệ từng quyết định thiết kế |

Sau khi dữ liệu và nghiệp vụ đủ rõ, mới thực hiện bước API contract riêng để nhóm mobile tích hợp. Không chốt endpoint hoặc JSON chi tiết trong tài liệu này.

Mỗi chặng có hai điều kiện hoàn thành: chức năng chạy đúng và người phụ trách giải thích được. Tích hợp mobile bắt đầu từ những luồng nhỏ đã ổn định, không đợi viết xong toàn bộ backend mới ghép. Thử nghiệm AI trước khi xây đầy đủ Speaking cần có một ví dụ luyện lại để phát hiện sớm nếu điểm nhấn khó đạt chất lượng mong muốn.

## Cách làm việc để hiểu sâu

Mỗi lượt hỗ trợ tập trung vào một use case hoặc một phần nhỏ của use case. Một đơn vị công việc gồm:

1. Mô tả chức năng bằng lời và một ví dụ dữ liệu cụ thể.
2. Xác định đầu vào, chủ sở hữu, điều kiện hợp lệ và kết quả mong muốn.
3. Theo dấu những bản ghi sẽ đọc, thêm, sửa hoặc xóa.
4. Viết thứ tự xử lý ngắn rồi mới viết code.
5. Giải thích annotation, dependency và câu truy vấn mới xuất hiện.
6. Chạy ca thành công, dữ liệu sai và truy cập sai chủ sở hữu khi phù hợp; kiểm tra dữ liệu sau lỗi.
7. Người thực hiện tự giải thích lại và thử sửa một yêu cầu nhỏ.

Khung câu hỏi áp dụng sau mỗi use case: ai được gọi; dữ liệu được đọc từ đâu; điều kiện nào cần kiểm tra; vì sao dùng quan hệ này; transaction bắt đầu và kết thúc ở đâu; lỗi ở giữa để lại gì; gửi lại hoặc gửi đồng thời thì sao; ca kiểm thử nào chứng minh. Với phần AI thêm hai câu: AI nhận chính xác dữ liệu nào và backend tin hoặc kiểm tra từng phần đầu ra ra sao.

Kiểm tra khả năng hiểu bằng thao tác cụ thể: tự lần từ Controller đến câu truy vấn; giải thích một bản ghi trước/sau request; đổi một quy tắc nhỏ và dự đoán nơi cần sửa. Khi chưa giải thích được một quyết định, chia nhỏ chức năng để làm rõ trước khi thêm tầng hoặc công nghệ mới. Không cần học thuộc từng dòng; cần biết lý do của code và tự tìm đúng phần để thay đổi.

Một bài thực hành nhỏ ở chặng Flashcard là lưu một nhóm từ được chọn từ kết quả bài. Chức năng thể hiện DTO, validation, chủ sở hữu, Mapper và transaction: có một ID sai chủ sở hữu thì không lưu dở cả nhóm; mục đã có trả về thông tin hiện có, không âm thầm ghi đè nghĩa.

Sau mỗi chức năng, ghi lại: mục đích, đầu vào, hàm xử lý, dữ liệu tác động, lý do đặt transaction, lỗi có thể xảy ra, kết quả kiểm thử và một câu hỏi bảo vệ. Comment trong code giải thích quyết định khó thấy; phần diễn giải dài đặt trong ghi chú chức năng.

## Phân công và kiểm tra tiến độ

Người dùng phụ trách nghiệp vụ tổng thể, CSDL, backend, tích hợp AI, kiểm thử backend và điều phối nhóm. Thành viên mobile phụ trách giao diện, điều hướng, microphone, trạng thái màn hình và tích hợp API. UI quản trị phải có người phụ trách rõ trong phân công nhóm; phương án Thymeleaf giúp người làm Spring xử lý khi nhóm chưa có người làm web. Nhóm trưởng cần nắm luồng mobile và ranh giới trách nhiệm để giải thích toàn dự án; không nhất thiết phải tự viết toàn bộ UI. Chưa mặc định số thành viên hoặc hạn nộp từ tài liệu tham khảo.

Theo dõi tiến độ bằng chức năng chạy được và điều đã hiểu. Ví dụ: lưu từ sau bài đã qua test sai chủ sở hữu, thay vì chỉ ghi hoàn thành năm class. Mỗi thay đổi nghiệp vụ phải cập nhật quy tắc trước khi thay đổi dữ liệu và code liên quan.

Các ca kiểm thử ưu tiên gồm đăng ký trùng email, token sai loại hoặc hết hạn, người dùng A truy cập dữ liệu B, USER gọi quản trị, tài khoản bị khóa nhưng còn token, chủ đề tắt nhưng vẫn được gửi ID trực tiếp, rollback nhóm lưu từ không hợp lệ, gửi lại request không tạo tin trùng, AI sai định dạng hoặc timeout, lỗi tổng kết giữ nguyên transcript, kết thúc khi lời AI chưa về, không nhận xét giữa phiên, bằng chứng trỏ sai tin nhắn và thống kê không tính phiên chỉ mới mở là có báo cáo.

Kiểm thử ràng buộc và transaction trên MySQL của môi trường phát triển/kiểm thử. Các bài test AI dùng dữ liệu kiểm soát được; có thêm lần kiểm tra tích hợp thật trước demo. Không dùng database đang chứa dữ liệu quan trọng để thử các ca ghi/xóa.

## Bản demo và minh chứng cho bảo vệ

Một kịch bản demo gắn kết nên thể hiện: admin tạo một tình huống; người học nhìn thấy và hội thoại; nhận góp ý, lưu từ và ôn; mở phiên luyện lại với mục tiêu đã chọn; xem câu làm bằng chứng; admin xem số liệu phát sinh từ các phiên đó. Chuẩn bị thêm một ca từ chối quyền và một ca lỗi AI/retry để giải thích cách hệ thống giữ dữ liệu.

Trước khi nộp, cần có: ERD và giải thích 3NF; ghi chú luồng chính; các quyết định thiết kế có lý do; hướng dẫn chạy từ môi trường sạch; dữ liệu demo không chứa thông tin nhạy cảm; kết quả các ca thử quan trọng; nội dung và kết quả người dùng thử có thật. Nếu demo dùng transcript đã lưu do dịch vụ AI gặp sự cố, phải ghi rõ đây là lịch sử đã lưu, không trình bày như phản hồi AI mới.

Đề xuất thử với một nhóm nhỏ người học phù hợp, chẳng hạn 3–5 người nếu điều kiện cho phép: họ có hoàn thành luồng, hiểu góp ý, tìm được từ cần ôn và sử dụng được mục tiêu ở phiên sau không. Quy mô này giúp tìm lỗi sử dụng và thu phản hồi ban đầu, chưa đủ để tuyên bố hiệu quả học tập trên diện rộng.

## Điểm bắt đầu cho lần làm việc tiếp theo

Đọc [README backend](../backend/README.md), chạy backend và lần theo [luồng tài khoản](SpeakMate-luong-tai-khoan.md). Đối chiếu User với users, thực hiện register → login → me → refresh → logout, rồi giải thích mỗi request làm đổi field nào.

Sau khi hiểu luồng tài khoản, triển khai quản lý chủ đề: USER xem tình huống đang bật; ADMIN tạo/sửa/bật/tắt. Thử ca USER gọi API quản trị, chủ đề trùng tên và chủ đề bị tắt. Topic đã có Entity/Repository, chưa có CRUD endpoint.

SQL v3 là nguồn schema hiện hành; phiên bản này bổ sung trường auth vào users, giữ 7 bảng/8 FK. Năm Entity nghiệp vụ còn lại, admin CRUD, AI và mobile integration vẫn là công việc tiếp theo. Google/SMTP thật cần cấu hình. Trạng thái kiểm thử cụ thể được ghi tại README backend; repo InternMatching không bị thay đổi.

## Nguồn tham khảo

- [Backend InternMatching đã đọc](https://github.com/LinhMarky/InternMaching/tree/80fb0711c70d06ad7cb6bcabd25b782138964121/backend).
- Tài liệu tổng quan app luyện nói tiếng Anh và ba ảnh do người dùng cung cấp; chỉ dùng làm tham khảo chức năng.
- [Spring Data JPA về ranh giới transaction](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html).
- [Spring Framework về quy tắc rollback](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html).
- [RFC 8725 về phân biệt các loại JWT](https://www.rfc-editor.org/rfc/rfc8725.html#section-3.12).
- [Spring Security về phân quyền ở phương thức](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).
- [Spring Security về CSRF cho ứng dụng web và client khác](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

Tài liệu Spring được tham khảo theo nguyên tắc thiết kế. Phiên bản dependency và cú pháp cấu hình cụ thể sẽ đối chiếu lại với phiên bản thực sự chọn khi khởi tạo SpeakMate.
