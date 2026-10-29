# SpeakMate — Quy ước code và lộ trình hiểu hệ thống

Ngày lập: 21/10/2026. Cập nhật: 25/10/2026, giờ Việt Nam.

Tài liệu quy ước để người phụ trách trực tiếp viết backend, hiểu vị trí và tác dụng của từng phần. Người dùng đã đồng ý áp dụng sáu cải tiến ở mục 5, với điều kiện code vẫn dễ lần theo và tự giải thích được. Các tên field ở mục 4 theo schema v3; DTO và API contract chi tiết được chốt theo từng chức năng.

Đọc [kế hoạch backend](SpeakMate-ke-hoach-backend.md), [tài liệu CSDL](SpeakMate-thiet-ke-co-so-du-lieu.md) và [vòng đời hội thoại AI](SpeakMate-vong-doi-hoi-thoai-AI.md). Mã chính thức ở `backend/`: Java 21, Gradle, Spring Boot, User/Topic, audit và Role/EnglishLevel. Luồng tài khoản đã triển khai; xem [bản đồ file tài khoản](SpeakMate-luong-tai-khoan.md). Các use case chủ đề/AI triển khai tiếp theo; trạng thái kiểm chứng ở [README backend](../backend/README.md).

## 1. Mục tiêu của cách làm

- Người phụ trách backend tự viết, chạy, kiểm tra và sửa code. Hỗ trợ tập trung vào phân tích, giải thích, review và gỡ lỗi theo từng chức năng.
- Với mỗi file do mình viết, xác định được ai gọi nó, đầu vào, đầu ra, dữ liệu tác động và lý do cần nó.
- Sản phẩm có một vòng học hoàn chỉnh: hội thoại bằng giọng nói kèm text → kết thúc → nhận xét và từ mới → tùy chọn lưu từ → ôn → luyện lại. Ưu tiên hoàn thiện hội thoại và kết quả trước luyện lại.
- Độ khó tập trung vào ba vấn đề có ích: hội thoại nhiều lượt có ngữ cảnh; luyện lại theo mục tiêu; dữ liệu nhất quán khi AI hoặc mạng lỗi.
- Mục tiêu 9–10 được theo đuổi bằng chất lượng sản phẩm, minh chứng kiểm thử và khả năng giải thích. Điểm cụ thể còn phụ thuộc tiêu chí và đánh giá của giảng viên.

## 2. Đã tham khảo gì từ InternMatching

Đọc trực tiếp các file sau từ nhánh `main` trên GitHub trong lần đối chiếu này:

- [User.java](https://github.com/LinhMarky/InternMaching/blob/main/backend/src/main/java/vn/tailinh/internmatching/entity/User.java): tên field, cột, quan hệ, enum và trạng thái tài khoản.
- [AbstractAuditingEntity.java](https://github.com/LinhMarky/InternMaching/blob/main/backend/src/main/java/vn/tailinh/internmatching/entity/AbstractAuditingEntity.java): bốn field audit.
- [ApplicationController.java](https://github.com/LinhMarky/InternMaching/blob/main/backend/src/main/java/vn/tailinh/internmatching/controller/ApplicationController.java): endpoint, constructor injection và response.
- [ApplicationService.java](https://github.com/LinhMarky/InternMaching/blob/main/backend/src/main/java/vn/tailinh/internmatching/service/ApplicationService.java): kiểm tra dữ liệu, chủ sở hữu và luồng xử lý tuần tự.
- [ApplicationMapper.java](https://github.com/LinhMarky/InternMaching/blob/main/backend/src/main/java/vn/tailinh/internmatching/util/mapper/ApplicationMapper.java): ánh xạ bằng getter/setter.
- [CreateApplicationDTO.java](https://github.com/LinhMarky/InternMaching/blob/main/backend/src/main/java/vn/tailinh/internmatching/dto/request/application/CreateApplicationDTO.java) và [FetchApplicationResponse.java](https://github.com/LinhMarky/InternMaching/blob/main/backend/src/main/java/vn/tailinh/internmatching/dto/response/application/FetchApplicationResponse.java): cách đặt tên và DTO lồng.

Đọc bổ sung luồng Company, entity Job/Resume, response chung và xử lý lỗi từ bản local ở `D:/BACKEND/WebFinal/InternMatching/RIPT1307-03-2026-Nhom4-KTHP`. Lần rà soát cải tiến đọc thêm `IdInvalidException`, `GlobalException`, `FormatRestResponse`, `ResultPaginationResponse`, `UserRepository`, `PermissionInterceptor` và `service/override/UserDetailsService`. Chưa xác nhận toàn bộ bản local trùng với `main`. Đây là đối chiếu các phần đại diện cho phong cách code, chưa phải kiểm toán toàn bộ repo. Liên kết `main` có thể thay đổi sau ngày đọc.

## 3. Quy ước quen thuộc nên giữ

| Phần | Quy ước cho SpeakMate | Ví dụ |
| --- | --- | --- |
| Package gốc | Theo tên dự án | `vn.tailinh.speakmate` |
| Các tầng | `controller`, `service`, `repository`, `entity`, `dto/request`, `dto/response`, `util/mapper` | `ConversationController`, `ConversationService` |
| Service | Class cụ thể, `@Service`, `@RequiredArgsConstructor`, dependency `private final` | `private final ConversationRepository conversationRepository` |
| Entity và bảng | Entity số ít, PascalCase; bảng số nhiều, snake_case | `Conversation` → `conversations` |
| ID | `Long id`, tự tăng | Giống `User.id` |
| Field Java và cột DB | camelCase và snake_case | `lastReviewedAt` → `last_reviewed_at` |
| Boolean | Field không thêm tiền tố `is`; ánh xạ cột tường minh | `active` → `is_active`, theo `User.active` |
| Quan hệ trong Entity | Field là đối tượng có tên rõ nghĩa | `User user`, `SavedWord savedWord`, `Conversation conversation` |
| Tham chiếu trong request DTO | Gửi ID của tài nguyên cần thao tác | `topicId`, `suggestionIds`, `savedWordIds` |
| Enum | `util/constant`, giá trị viết hoa, lưu dạng tên | `SavedWordStatus.LEARNING`, `ReviewStatus.PROCESSING` |
| Audit | Giữ bốn tên quen thuộc trong lớp cha | `createdAt`, `createdBy`, `updatedAt`, `updatedBy` |
| Thời điểm mới | Dùng `Instant`, thống nhất đuôi `At` | `endedAt`, `lastReviewedAt` |
| Request | `Create<X>DTO`, `Update<X>DTO`; thao tác riêng có tên riêng | `CreateConversationDTO`, `SendMessageDTO` |
| Response | Chọn thống nhất kiểu `Create/Fetch/Update<X>Response` đã có ở module Application | `FetchSavedWordResponse` |
| Mapper | Helper static, getter/setter tường minh | `SavedWordMapper.toFetchSavedWordResponse(savedWord)` |
| Biến trong Service | Vai trò rõ, gần cách viết hiện tại | `currentUser`, `currentConversation`, `request`, `response` |
| Hàm Service | Gắn thao tác với đối tượng để dễ tìm | `startConversation`, `sendMessage`, `finishConversation`, `retryReview` |
| Repository | Query đơn giản có tên thể hiện điều kiện | `findByIdAndUserId`, `existsByNameAndUserId` |
| Response chung | Giữ `RestResponse<T>`; dùng `ResultPaginationResponse<T>` với `List<T> result` | `statusCode`, `error`, `message`, `data` |

Repo cũ có một số biến thể như `CreatedUserResponse`, `ResUserDTO`, `CreateApplicationResponse`. Trong module auth, RegisterRequest/LoginRequest/GoogleLoginRequest/RefreshTokenRequest và ForgotPasswordDTO/ResetPasswordDTO đều là request DTO; UserResponse dùng chung cho các thao tác trả hồ sơ, TokenResponse dùng chung cho các thao tác cấp token. Không tạo nhiều response có cùng trường chỉ để đổi tên thao tác.

Quy tắc mọi thời điểm mới dùng đuôi `At` là bước thống nhất cho SpeakMate; repo cũ còn dùng `startDate`, `endDate`. Quy tắc cột boolean `is_active` có bằng chứng trong `User`, không có nghĩa mọi entity cũ đều đã dùng thống nhất.

## 4. Tên field theo schema v3 cho 7 entity

Mỗi entity có `id` và kế thừa các field audit. Bảng dưới liệt kê phần nghiệp vụ theo SQL v3. Đã ánh xạ User và Topic; danh sách này hướng dẫn các Entity còn lại khi tới chức năng tương ứng.

| Entity / bảng | Field Java | Ý nghĩa |
| --- | --- | --- |
| `User` / `users` | `name`, `email`, `password`, `googleSubject`, `level`, `role`, `active`, `refreshToken`, `refreshExpiresAt`, `authVersion`, `passwordResetCodeHash`, `passwordResetExpiresAt`, `passwordResetSentAt`, `passwordResetAttempts` | Danh tính, mật khẩu/Google, trình độ/vai trò, phiên và mã khôi phục hiện hành; ý nghĩa từng field ở tài liệu CSDL |
| `Topic` / `topics` | `name`, `description`, `level`, `scenario`, `active` | Chủ đề, độ khó và tình huống hội thoại |
| `Conversation` / `conversations` | `user`, `topic`, `createRequestId`, `profileLevelSnapshot`, `level`, `topicNameSnapshot`, `scenarioSnapshot`, `endedAt`, `reviewStatus`, `summary`, `strengths`, `weaknesses`, `recommendedLevel`, `levelFeedback`, `selfRating`, `processingToken`, `processingStartedAt` | Bối cảnh, mức luyện, vòng đời và báo cáo sau khi đóng |
| `Message` / `messages` | `conversation`, `sender`, `content`, `requestId`, `correctedContent`, `feedback` | Câu gốc và mã lượt; chỉ ghi câu sửa sau Kết thúc |
| `WordSuggestion` / `word_suggestions` | `conversation`, `word`, `meaning`, `example` | Từ/cụm từ trong kết quả bài, kể cả khi không được chọn lưu |
| `SavedWord` / `saved_words` | `user`, `word`, `meaning`, `example`, `status`, `lastReviewedAt` | Từ cá nhân trực tiếp thuộc người dùng, hiển thị dạng flashcard để ôn |
| `WordPractice` / `word_practices` | `conversation`, `savedWord`, `wordSnapshot`, `meaningSnapshot`, `result`, `evidenceMessage`, `feedback` | Một từ được luyện trong một phiên, snapshot và bằng chứng |

Ánh xạ những tên dễ nhầm:

| Field trong Entity | Cột DB | Field tham chiếu ở DTO khi cần |
| --- | --- | --- |
| `user` | `user_id` | `userId` chỉ khi nghiệp vụ cho phép; chủ sở hữu hiện tại lấy từ xác thực |
| `topic` | `topic_id` | `topicId` |
| `conversation` | `conversation_id` | `conversationId` hoặc ID trong URL |
| `savedWord` | `saved_word_id` | `savedWordId` |
| `evidenceMessage` | `evidence_message_id` | `evidenceMessageId` |
| `scenarioSnapshot` | `scenario_snapshot` | `scenarioSnapshot` nếu màn hình cần |
| `wordSnapshot` | `word_snapshot` | `wordSnapshot` |
| `correctedContent` | `corrected_content` | `correctedContent` |
| `requestId` | `request_id` | `requestId` |
| `lastReviewedAt` | `last_reviewed_at` | `lastReviewedAt` |
| `refreshToken` | `refresh_token` | Hợp đồng auth hiện mô tả `refresh_token`; ánh xạ JSON tường minh, không đổi field Java |
| `active` | `is_active` | `active` |
| `reviewStatus` | `review_status` | `reviewStatus` |
| `profileLevelSnapshot` | `profile_level_snapshot` | `profileLevelSnapshot` |
| `recommendedLevel` | `recommended_level` | `recommendedLevel` |

Các bổ sung cần giải thích được trước khi viết:

- `Conversation.level` giữ mức đã chọn A1/A2/B1/B2; `profileLevelSnapshot` giữ mức hồ sơ lúc mở. `recommendedLevel` là khuyến nghị cuối bài, không tự đổi `User.level`.
- `scenarioSnapshot` giữ bối cảnh dùng khi bắt đầu; sửa `Topic.scenario` không đổi bối cảnh của phiên đang học hoặc đã lưu.
- `Message.correctedContent` và `feedback` luôn NULL khi đang trò chuyện; chỉ ghi trên tin USER sau khi phân tích cuối bài thành công. `content` gốc được giữ nguyên. Lời đáp và báo cáo dùng DTO riêng.
- `WordPractice.feedback` lưu nhận xét cho mục tiêu đó; `result` xác nhận bằng chứng xuất hiện, không tự đồng nghĩa với thành thạo.
- Đầu ra AI dùng `wordPracticeId` để nhận diện mục tiêu. `savedWordId` có thể NULL sau khi xóa từ cá nhân; snapshot vẫn giữ nội dung đã luyện.
- Không lưu cờ `saved` trên gợi ý. UI đối chiếu với Từ của tôi để biết hiện có từ ấy hay chưa; không coi đó là lịch sử từng lần lưu.
- `createRequestId` nhận diện lần mở hội thoại; `requestId` nhận diện lượt; `processingToken` nhận diện một lần thử AI. Quy tắc timeout, retry, trạng thái báo cáo và đóng phiên theo tài liệu vòng đời AI.

## 5. Sáu cải tiến đã thống nhất trước khi thiết kế CSDL

Trạng thái: người dùng đã đồng ý áp dụng. Mỗi cải tiến phải có vị trí code rõ, một ví dụ thực tế và cách kiểm chứng. Triển khai theo chức năng để người viết nắm từng phần trước khi chuyển tiếp.

Luồng Service quen thuộc từ `ApplicationService` có thể áp dụng:

1. Lấy người dùng hiện tại.
2. Tải tài nguyên cần thao tác.
3. Kiểm tra tồn tại, chủ sở hữu, trạng thái và dữ liệu trùng.
4. Thực hiện quy tắc nghiệp vụ.
5. Lưu thay đổi với ranh giới transaction rõ ràng.
6. Chuyển sang response bằng Mapper.

### 5.1 DTO nhất quán và đúng quyền quyết định của client

**Căn cứ:** `ApplicationController.create` nhận Entity dù repo có `CreateApplicationDTO`; DTO tạo còn chứa ID, trạng thái và thông tin audit.

**Áp dụng:** Controller nhận request DTO và trả response DTO. Ví dụ `SaveWordsFromSuggestionsDTO` chỉ nhận `suggestionIds`; backend kiểm tra chủ sở hữu hội thoại nguồn rồi lấy từ/nghĩa từ DB, tự gán chủ sở hữu bản lưu, trạng thái NEW và audit. Client không gửi `userId`, kết quả đánh giá hoặc Entity lồng để quyết định quyền.

**Code nằm ở:** `dto/request/<module>` chứa trường được phép gửi và validation định dạng; Controller kích hoạt validation; Service kiểm tra tồn tại, trùng, quyền và trạng thái; `util/mapper` chọn trường trả ra. Entity ánh xạ dữ liệu lưu trữ.

**Kiểm chứng và giải thích:** thử ID không tồn tại, ID của người khác và request cố gán chủ sở hữu. Chỉ được tạo dữ liệu cho đúng người đăng nhập. Với nhập từ thủ công, thêm kiểm tra từ/nghĩa rỗng hoặc quá dài. Người viết chỉ được trường nào đến từ client, trường nào do backend quyết định.

### 5.2 Exception nghiệp vụ và transaction có quy tắc rõ

**Căn cứ:** `IdInvalidException` ở bản local kế thừa `Exception`. Với cấu hình mặc định, checked exception thoát khỏi phương thức transactional không tự làm rollback; `RuntimeException` và `Error` có quy tắc rollback mặc định. Tham chiếu [quy tắc rollback của Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html).

**Áp dụng:** exception nghiệp vụ cần hủy thao tác kế thừa `RuntimeException`. Tạo loại lỗi khi có nhu cầu cụ thể, ví dụ `ResourceNotFoundException`, `ConflictException`; dùng loại lỗi quyền phù hợp. `GlobalException` ánh xạ lỗi sang HTTP status và thông báo rõ ràng. Lỗi bất ngờ được ghi chi tiết ở log và trả thông báo phù hợp cho client.

**Code nằm ở:** exception và handler trong `exception`; ranh giới `@Transactional` ở phương thức nghiệp vụ của Spring bean trong `service`. Lỗi cần rollback phải đi ra khỏi ranh giới transaction; không bắt rồi bỏ qua lỗi. Không đặt lời gọi AI trong transaction DB kéo dài. Với cơ chế proxy mặc định, không dựa vào lời gọi nội bộ cùng class để kích hoạt `@Transactional`.

**Kiểm chứng và giải thích:** với use case lưu nhóm từ hoặc lưu toàn bộ báo cáo cuối bài, tạo lỗi ở bước ghi sau và kiểm tra không còn kết quả lưu dở từ cùng transaction. Phân biệt exception tạo ra lỗi nghiệp vụ, transaction quyết định lưu/hoàn tác, còn handler chuyển lỗi thành HTTP response. Báo cáo lỗi không rollback việc đóng phiên đã commit trước đó.

### 5.3 Phân định xác thực, vai trò và chủ sở hữu

**Căn cứ:** `ApplicationService` lặp việc lấy email đăng nhập và tìm User ở nhiều hàm; `UserRepository.findByEmail` ở bản local trả `User`, có thể là `null`.

**Áp dụng:** gom lấy tài khoản hiện tại vào `UserService.getCurrentUser()`; `findByEmail` trả `Optional<User>` và xử lý trường hợp không tìm thấy rõ ràng. Đây là helper nghiệp vụ, không tự thay thế cơ chế kiểm tra mọi request được bảo vệ.

| Cần kiểm tra | Nơi chịu trách nhiệm |
| --- | --- |
| Token và tài khoản còn hợp lệ | Luồng xác thực trong `security/config`; trạng thái tài khoản được kiểm tra trên mọi request được bảo vệ |
| Vai trò được phép thực hiện thao tác | Cấu hình phân quyền hoặc annotation đã cấu hình hoạt động; thống nhất tên authority từ token đến điều kiện kiểm tra |
| Từ cá nhân hoặc hội thoại thuộc người nào | Service của use case, lấy người hiện tại từ xác thực |
| Định dạng và trường bắt buộc | Request DTO và validation do Controller kích hoạt |
| Tồn tại, trùng và chuyển trạng thái hợp lệ | Service và ràng buộc DB tương ứng |

Hai vai trò USER/ADMIN dùng quy tắc cố định. Khi dùng `@PreAuthorize`, phải cấu hình method security; không chỉ thêm annotation rồi mặc định nó đã chạy. Tham chiếu [Spring Security về method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).

**Kiểm chứng và giải thích:** A sửa từ của B bị từ chối; USER gọi chức năng admin bị từ chối; tài khoản bị khóa vẫn bị từ chối ở request tiếp theo dù token chưa hết hạn. Khi lưu gợi ý, kiểm tra hội thoại nguồn thuộc người đó và tự gán saved_words.user_id. Khi chọn từ luyện lại, kiểm tra cả từ lẫn hội thoại thuộc người hiện tại.

### 5.4 Response phân trang có kiểu cụ thể

**Căn cứ:** `ResultPaginationResponse` ở bản local có `Object result`, nên đọc chữ ký hàm chưa biết danh sách chứa gì.

**Áp dụng:** dùng `ResultPaginationResponse<T>` với `MetaResponse meta` và `List<T> result`. Ví dụ Từ của tôi có kiểu `ResultPaginationResponse<FetchSavedWordResponse>`. `T` là loại phần tử của danh sách; đây là mục đích cụ thể của generic này.

**Code nằm ở:** `dto/response/ResultPaginationResponse`; nơi tạo phân trang và chữ ký Service/Controller dùng cùng kiểu cụ thể. Giữ các tên metadata quen thuộc như `page`, `pageSize`, `pages`, `total`.

**Kiểm chứng và giải thích:** trả danh sách có dữ liệu và danh sách rỗng; kiểm tra metadata cùng nội dung. Người viết giải thích được Entity được Mapper đổi sang DTO nào trước khi đặt vào `result`.

### 5.5 Response tự bọc và các đoạn code chạy gián tiếp phải nhìn thấy được trong luồng

**Căn cứ:** `FormatRestResponse` ở bản local dùng `ResponseBodyAdvice` để tự bọc kết quả Controller. Nếu chỉ đọc Controller sẽ chưa thấy nguồn gốc của `statusCode`, `message`, `data` trong JSON.

**Triển khai hiện tại (24/10/2026):** giữ định dạng `RestResponse`, nhưng Controller gọi trực tiếp `RestResponse.success(...)`; handler lỗi gọi `RestResponse.failure(...)`. Chưa tạo `FormatRestResponse`/`@ApiMessage`/ResponseBodyAdvice. Lựa chọn này giúp người phụ trách nhìn ngay được nơi tạo statusCode/message/data, giảm một bước chạy gián tiếp. Nếu thêm advice sau này phải cập nhật toàn bộ đường trả response và tránh bọc hai lần.

**Đường trả kết quả thành công:** `Service → Mapper → Controller tạo RestResponse → JSON`. Mapper chọn field dữ liệu; Controller chọn HTTP status/thông điệp. Không có advice tự bọc phía sau.

**Đường lỗi:** lỗi validation/nghiệp vụ trong MVC đi qua handler phù hợp ở `GlobalException`; lỗi token hoặc quyền xảy ra trước Controller cần handler của Security. Cùng thống nhất định dạng lỗi, đồng thời xác định đúng lớp thực sự xử lý mỗi trường hợp.

**Kiểm chứng và giải thích:** đặt breakpoint tại Controller, `RestResponse.success` và handler lỗi tương ứng; lần theo một request thành công, một lỗi validation và một token không hợp lệ. Response thành công chỉ có một lớp bọc. Trong bản đồ chức năng, ghi thêm auditing và transaction để thấy các phần Spring gọi gián tiếp.

### 5.6 Ranh giới nhỏ giữa nghiệp vụ và giao tiếp AI

**Áp dụng:** giữ Service CRUD là class cụ thể. Riêng `AiClient` là interface vì có nhu cầu thay lớp gọi nhà cung cấp bằng bản giả khi kiểm thử. Bản demo AI dùng tích hợp thật; dữ liệu giả được nhận diện rõ là phục vụ kiểm thử.

| Thành phần dự kiến | Trách nhiệm |
| --- | --- |
| `service/ConversationService` | Điều phối tạo/gửi/kết thúc/retry; gọi AI ngoài transaction kéo dài |
| `service/ConversationTransactionService` | Các thao tác transaction ngắn để khóa, kiểm tra quyền/token và ghi dữ liệu; gọi qua Spring bean riêng |
| `service/ai/AiClient` và lớp triển khai | `generateReply` chỉ đáp lời; `reviewConversation` chỉ gọi sau đóng phiên; xử lý HTTP/timeout, không ghi Repository |
| DTO phản hồi AI trong `service/ai/dto` | Lời đáp và báo cáo là hai kiểu riêng; khác response trả cho mobile |
| Service và Mapper | Service kiểm tra kết quả theo nghiệp vụ rồi lưu; Mapper chuyển dữ liệu sang response cho app |

**Kiểm chứng và giải thích:** dùng kết quả AI cố định để kiểm tra lưu hội thoại và góp ý; dùng lỗi giả lập để kiểm tra dữ liệu sau thất bại; kiểm tra riêng một lần tích hợp thật. Người viết chỉ được dữ liệu nào gửi AI, dữ liệu nào nhận lại, phần nào được backend xác nhận và nơi xử lý khi AI lỗi.

### 5.7 Thứ tự áp dụng và điều kiện hiểu trước khi chuyển bước

Áp dụng DTO, exception và kiểu response từ các chức năng đầu tiên. Kiểm tra tài khoản/quyền đi cùng phần auth và các use case có chủ sở hữu. Lần theo response tường minh bằng request thật; tách `AiClient` khi bắt đầu Speaking.

Giữ Mapper chỉ chuyển dữ liệu; giữ các tên audit quen thuộc và bỏ generic không sử dụng ở `AbstractAuditingEntity`. Chỉ tạo helper, class hoặc annotation mới khi chỉ ra được tác dụng trong một luồng cụ thể.

Mỗi phần được coi là đã hiểu khi người phụ trách tự tìm được file cần sửa, giải thích dữ liệu trước/sau request và tự thay một quy tắc nhỏ. Nếu chưa giải thích được, chia nhỏ ví dụ và chạy lại trước khi thêm phần mới. Các bảng/cột/quan hệ theo SQL hiện hành và tài liệu CSDL, chỉ thay đổi khi nghiệp vụ thực sự đòi hỏi.

## 6. Bản đồ file: ví dụ lưu từ sau bài

Các tên dưới đây là file dự kiến, chưa được tạo.

| Thứ tự | File/hàm | Tác dụng cần tự giải thích |
| --- | --- | --- |
| 1 | `config/SecurityConfiguration` và các class trong `security` | Kiểm tra token, trạng thái tài khoản và quyền để xác định request có được đi tiếp |
| 2 | `dto/request/savedword/SaveWordsFromSuggestionsDTO` | Danh sách suggestionIds và validation |
| 3 | `controller/SavedWordController.saveWordsFromSuggestions` | Nhận HTTP, kích hoạt validation, gọi Service, chọn HTTP status |
| 4 | `service/SavedWordService.saveWordsFromSuggestions` | Kiểm tra nguồn gợi ý, trùng từ; lưu nhóm trong transaction |
| 5 | `service/UserService.getCurrentUser` | Lấy tài khoản đã xác thực; xử lý tài khoản không còn hợp lệ |
| 6 | `repository/WordSuggestionRepository`, `SavedWordRepository` | Đọc nguồn thuộc người hiện tại, tìm mục đã có và lưu từ mới |
| 7 | `entity/WordSuggestion`, `SavedWord` | Ánh xạ gợi ý theo hội thoại và từ cá nhân theo User |
| 8 | `util/mapper/SavedWordMapper` | Chọn trường cần trả, chuyển Entity sang DTO |
| 9 | `dto/response/savedword/SaveWordsResponse` | Danh sách mục mới/mục đã có, không tự ghi đè nghĩa |
| 10 | `dto/response/RestResponse` do Controller gọi | Tạo lớp bọc kết quả đúng một lần, nhìn thấy ngay trong Controller |
| Khi lỗi trong MVC | `exception/GlobalException` | Chuyển lỗi validation/nghiệp vụ thành phản hồi thống nhất |
| Khi lỗi trước Controller | Handler của Security | Trả lỗi xác thực/quyền theo cùng định dạng đã thống nhất |

Validation có thể dừng request trước Service; lỗi xác thực có thể dừng trước Controller. Entity được dùng trong nhiều bước, không phải một bộ xử lý chạy riêng. Nhóm có ID sai quyền phải bị từ chối toàn bộ. Bản đồ các hàm hội thoại nằm tại [mục 7 của vòng đời AI](SpeakMate-vong-doi-hoi-thoai-AI.md).

Mỗi chức năng có một ghi chú tương tự: request mẫu → file/hàm → dữ liệu trước/sau → response → ca lỗi. Ghi cả phần chạy gián tiếp như security, auditing, transaction và exception handler để không bỏ sót code hỗ trợ.

## 7. Phạm vi tạo giá trị và độ khó vừa sức

Kịch bản học để demo: người học hội thoại về nhận phòng khách sạn, nhận một góp ý cụ thể, lưu cụm “check in”, ôn lại, chọn cụm đó làm mục tiêu ở phiên mới và xem câu mình đã sử dụng. Cần tạo cơ hội dùng mục tiêu hợp với chủ đề; thử trước các cặp chủ đề–mục tiêu để kiểm tra chất lượng.

| Phần đầu tư | Giá trị với người học | Phần kỹ thuật cần tự giải thích |
| --- | --- | --- |
| Hội thoại nhiều lượt, nhận xét sau kết thúc | Nói tự nhiên trước, sau đó biết ưu/nhược điểm và câu cần sửa | Hai nhiệm vụ AI, ngữ cảnh, đóng phiên, kiểm tra và lưu báo cáo |
| Luyện lại 1–3 từ/cụm từ | Có cơ hội sử dụng lại kiến thức vừa học | Quan hệ phiên–thẻ, snapshot, chọn mục tiêu, bằng chứng thuộc đúng phiên |
| Xử lý lỗi và gửi lại | Hội thoại được giữ và app có thể tiếp tục sau lỗi | `requestId`, unique constraint, transaction ngắn, trạng thái và phục hồi |

Tài khoản, quyền USER/ADMIN, quyền sở hữu, chủ đề, từ cá nhân, lịch sử và trang quản trị tối thiểu là nền để vòng học hoạt động. Android nhận giọng nói thành text và đọc lời AI; cả hai là phần bắt buộc của trải nghiệm chính. Backend quản lý nội dung hội thoại và phản hồi sau Kết thúc.

Phạm vi này dùng một ứng dụng Spring Boot và một MySQL. Chỉ bổ sung công nghệ hoặc tính năng khi có yêu cầu cụ thể, thời gian kiểm chứng và người phụ trách giải thích được. Các mục như hội thoại âm thanh liên tục, thuật toán ôn nâng cao, chia sẻ bộ thẻ hoặc quản trị quyền động được xét sau vòng học chính.

## 8. Cách triển khai để tự hiểu toàn bộ

Mỗi lượt làm việc hoàn thành một use case nhỏ:

1. Viết một ví dụ đầu vào, kết quả và lỗi có thể gặp.
2. Chốt quy tắc bằng lời; xác định bảng và field cần dùng.
3. Vẽ đường đi qua file/hàm và thứ tự xử lý.
4. Người phụ trách viết code; giải thích annotation hoặc kiến thức mới ngay ở nơi sử dụng.
5. Chạy ca thành công và những ca lỗi quan trọng; kiểm tra dữ liệu sau request.
6. Review rồi tự thay một yêu cầu nhỏ, chẳng hạn độ dài tên hoặc quy tắc trùng, và tìm đúng chỗ cần sửa.
7. Ghi ngắn lý do của quyết định, kết quả kiểm chứng và câu hỏi có thể gặp khi bảo vệ.

Thứ tự triển khai: nền backend/User/Topic → auth/chủ đề → thử AI nhỏ và text nhiều lượt có ngữ cảnh → giọng nói hai phía → kết thúc/nhận xét/lịch sử → tùy chọn lưu từ và ôn → luyện lại → demo. Kiểm tra lỗi, quyền, gửi lại và kết thúc khi AI còn chạy ngay trong luồng liên quan; không dồn việc giữ đúng dữ liệu đến cuối.

Ba bài kiểm tra hiểu cần đạt cho mỗi chức năng: tự chỉ đúng file cần sửa; giải thích dữ liệu trước/sau một request; dự đoán tác động khi thay một quy tắc. Với AI, nói rõ dữ liệu gửi đi, dữ liệu được giữ lại và phần nào backend có thể kiểm chứng.

Khi demo, trình bày vòng học hoàn chỉnh, một ca sai quyền và một ca AI/mạng lỗi có thể phục hồi. Thu phản hồi thật của người dùng thử về việc hoàn thành luồng và hiểu góp ý; tách phản hồi sử dụng ban đầu khỏi kết luận về hiệu quả học tập lâu dài.
