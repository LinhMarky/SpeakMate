# 03 — Hợp đồng API FE–BE 1.0.0

Đọc cùng [openapi.json](openapi.json) và [examples.json](examples.json). Có **29 operation: 24 P0, 5 P1**. Cập nhật 26/10/2026: **24 P0 đã có code backend**, 5 P1 hoãn. API học đã kiểm thử bằng MySQL tạm với AiClient thay bằng test stub; chưa xác minh Gemini thật hoặc tích hợp Android. Xem [cách đọc/chạy code](08-doc-code-va-chay-backend.md).

## 1. Quy ước chung

| Nội dung | Quy ước |
| --- | --- |
| Base URL | Máy tính `http://localhost:8081`; emulator `http://10.0.2.2:8081`; điện thoại dùng IP LAN; triển khai HTTPS |
| API prefix | `/api/v1`; URL trong OpenAPI đã có prefix, không ghép thêm lần nữa |
| Auth | `Authorization: Bearer <access_token>` cho endpoint được bảo vệ; không tự gắn token cũ vào login/Google/refresh/forgot/reset |
| JSON | UTF-8, Content-Type application/json; request chỉ nhận field trong schema |
| Tên field | camelCase; riêng cặp token giữ access_token/refresh_token/token_type/expires_in như code sẵn có |
| ID | Số dương Long; FE không gửi tên chủ đề thay ID; UUID chỉ dành cho mã yêu cầu |
| Thời gian | ISO 8601 UTC với Z, có thể có phần thập phân; FE chuyển giờ địa phương để hiển thị |
| Null/list | Giá trị chưa có dùng null; danh sách không có phần tử dùng []; không đổi sang chuỗi rỗng hoặc bỏ field tùy hứng |
| Lỗi | Dựa HTTP status và error; message để hiển thị, không parse chuỗi thông báo để điều khiển nghiệp vụ |
| Ownership | ID không tồn tại hoặc thuộc người khác đều 404 RESOURCE_NOT_FOUND; ADMIN không tự vượt quyền transcript cá nhân |
| Gửi lại | Giữ UUID và nội dung chuẩn hóa; không dùng retry làm lý do tạo bản ghi mới |

Response thành công/lỗi dùng đúng một lớp bọc:

```json
{"statusCode":200,"error":null,"message":"Thành công","data":{}}
```

```json
{"statusCode":409,"error":"TURN_PENDING","message":"Hãy xử lý lượt đang chờ trước.","data":null}
```

Danh sách dùng `data = {"meta":{"page":1,"pageSize":20,"pages":1,"total":5},"result":[...]}`. Query `page` bắt đầu 1; `size` mặc định 20, từ 1 đến 100. Danh sách rỗng có pages=0, total=0, result=[]; page vượt cuối trả 200 và result rỗng. BE chuyển sang PageRequest bằng page-1. Topics sắp tên tăng rồi ID tăng; lịch sử/từ cá nhân/users sắp ID giảm; admin topics sắp ID giảm. Chưa có sort động.

## 2. Auth đã có — chín operation

| Method/URL sau /api/v1 | operationId | Body | Thành công |
| --- | --- | --- | --- |
| POST /auth/register | registerAccount | name, email, password; level tùy chọn | 201 UserResponse; chưa tự login |
| POST /auth/login | loginWithPassword | email, password | 200 TokenResponse |
| POST /auth/google | loginWithGoogle | idToken | 200 TokenResponse; Google mới tự tạo tài khoản |
| POST /auth/refresh | refreshSession | refreshToken | 200 cặp token mới |
| GET /auth/me | getCurrentUser | Không | 200 UserResponse |
| POST /auth/logout | logoutAccount | Không | 200, data=null |
| POST /auth/google/link | linkGoogleAccount | idToken; cần Bearer | 200 UserResponse |
| POST /auth/password/forgot | requestPasswordReset | email | 202, data=null, thông báo chung |
| POST /auth/password/reset | resetPassword | email, code, newPassword | 200, data=null; về login |

UserResponse gồm id/name/email/level/role/active/passwordEnabled/googleLinked; không có hash hoặc refresh token. TokenResponse gồm access_token, refresh_token, token_type=Bearer, expires_in=900 và user. Các giá trị TTL là cấu hình mặc định, FE đọc expires_in thay vì tự tạo token.

Tên 1–100 ký tự sau trim; email tối đa 255, trim/lowercase. Mật khẩu mới 8–64 ký tự, tối đa 72 byte UTF-8, không trim. Login DTO nhận tối đa 256 ký tự nhưng mật khẩu không thể kiểm BCrypt hợp lệ vẫn trả lỗi đăng nhập chung. Mã reset sáu chữ số, hạn 10 phút, tối đa 5 lần sai; chờ 60 giây để xin lại. Thiếu Google/SMTP trả 503, không giả thành công.

Một refresh token hiện hành/tài khoản. Mọi login mới thu hồi phiên trước; refresh chỉ xoay token cùng phiên. FE chỉ cho **một refresh đang chạy**, ghi lại token mới trước khi replay request cũ; chỉ replay request một lần. POST conversation/message phải giữ UUID/body cũ qua refresh. Refresh thất bại 401 thì xóa phiên và về login; lỗi mạng không tự xóa cặp token còn có thể dùng.

## 3. Topics và admin P0 — năm operation

| Method/URL | operationId | Body/response | Quyền |
| --- | --- | --- | --- |
| GET /topics | listTopics | TopicResponsePage: id/name/description/level | Đã đăng nhập; chỉ active |
| GET /admin/topics | adminListTopics | AdminTopicResponsePage, thêm scenario/active | ADMIN; gồm cả tắt |
| POST /admin/topics | adminCreateTopic | TopicRequest → 201 AdminTopicResponse | ADMIN |
| PUT /admin/topics/{id} | adminUpdateTopic | TopicRequest → 200 AdminTopicResponse | ADMIN |
| PATCH /admin/topics/{id}/active | adminSetTopicActive | {active:boolean} → 200 AdminTopicResponse | ADMIN |

TopicRequest: name bắt buộc 1–100 sau trim và UNIQUE; level bắt buộc A1/A2/B1/B2; scenario bắt buộc 1–4000; description tùy chọn, null hoặc tối đa 2000. POST mặc định active=true; không nhận active/role/id. PUT thay nội dung, description bị bỏ qua trong body thì thành null; không đổi active. Không có DELETE topic trong contract.

Mức gợi ý không dùng để cấm người học chọn mức khác. Khi tắt topic, GET learner không còn trả topic đó; tạo bài mới bằng ID cũ trả 409 TOPIC_DISABLED. Bài đã mở vẫn tiếp tục theo snapshot.

## 4. Conversation — tạo/đọc/gửi

| Method/URL | operationId | Thành công |
| --- | --- | --- |
| POST /conversations | createConversation | 201 mới; 200 nếu lặp cùng createRequestId và cùng dữ liệu |
| GET /conversations | listConversations | ConversationSummaryPage, chỉ dữ liệu của mình |
| GET /conversations/{id} | getConversation | ConversationDetail và transcript |
| POST /conversations/{id}/messages | sendConversationMessage | 200 TurnResponse; 202 nếu cùng lượt đang xử lý |

Tạo bài:

```json
{"topicId":201,"level":"B2","createRequestId":"10000000-0000-4000-8000-000000000001"}
```

Gửi lượt:

```json
{"content":"I have a reservation for two nights.","requestId":"20000000-0000-4000-8000-000000000001"}
```

UUID v4 chữ thường được tạo **trước** khi gọi mạng, một UUID cho một thao tác logic. BE trim content trước khi so trùng; content sau trim 1–1000 ký tự, tối đa 20 tin USER/bài. Giới hạn không chặn retry một lượt cũ hoặc Kết thúc.

ConversationDetail gồm id, topicId, topicName, scenario snapshot, profileLevelSnapshot, level, status=OPEN/CLOSED, reviewStatus, createdAt/endedAt, userTurnCount, pendingReply và messages. `status` tính từ endedAt; không thêm cột status trùng trong DB. messages sắp id tăng, tối đa 40 tin; MessageResponse có id/sender/content/requestId/createdAt, **không có field sửa lỗi**.

pendingReply là null nếu không có lượt chờ, hoặc `{requestId,userMessageId,status,pollAfterSeconds}`. status PROCESSING khi AI còn trong hạn; RETRYABLE khi lỗi/quá hạn chưa có lời AI. FE dùng nội dung USER tương ứng, không tự tạo tin khác để thay thế.

TurnResponse có conversationId/requestId/replyStatus/userMessage/aiMessage/pollAfterSeconds/replayed:

| replyStatus | HTTP | aiMessage | FE làm gì? |
| --- | --- | --- | --- |
| COMPLETED | 200 | Có | Hiện tin lưu được; chỉ tự đọc nếu lượt hiện tại, chưa đóng và chưa phát lần nào |
| PROCESSING | 202 | null | Chờ 3 giây rồi GET conversation; không tạo requestId khác |
| CANCELLED | 200 | null | Bài đã đóng trong lúc xử lý/khôi phục; tải trạng thái, không phát TTS |

`replayed=true` cho kết quả đã có/đang xử lý. Không dùng replay để tự đọc lại tất cả câu AI. Gặp 502/503/504 hoặc mất response: GET conversation trước, kiểm USER/AI đã lưu; nếu còn pending RETRYABLE thì POST lại **cùng requestId/content**. GET không tự gọi AI.

Quy tắc BE theo thứ tự: kiểm ownership → tìm requestId cũ → so nội dung → trả kết quả cũ nếu có → nếu chưa có kết quả và bài còn mở thì nhận lại lượt hợp lệ → chỉ sau đó mới xét lượt mới/giới hạn. Vì vậy bài đã đóng vẫn có thể đọc lại cặp tin của UUID cũ; không được thêm tin mới sau đóng.

## 5. Review — chỉ sau khi đóng

| Method/URL | operationId | Body | Kết quả |
| --- | --- | --- | --- |
| POST /conversations/{id}/finish | finishConversation | Không | Đóng một lần; 200 terminal hoặc 202 PROCESSING |
| GET /conversations/{id}/review | getConversationReview | Không | 200 trạng thái/đánh giá hiện có; không gọi AI |
| POST /conversations/{id}/review/retry | retryConversationReview | Không | Retry FAILED hoặc PROCESSING quá hạn; 200/202 |

Lần gọi mới đến send/finish/retry **đợi AI đồng bộ trên request thread, ngoài transaction DB**. Không cần queue AI/WebSocket trong ba ngày. HTTP 202 dùng khi một thao tác cùng loại đã chạy, không phải mọi POST mới đều được chuyển thành background job.

ReviewResponse gồm conversationId, reviewStatus, summary/strengths/weaknesses, recommendedLevel/levelFeedback, grammarCorrections, suggestedWords, canRetry, pollAfterSeconds.

| reviewStatus | Các trường nội dung | canRetry | pollAfterSeconds |
| --- | --- | --- | --- |
| NOT_STARTED | Chuỗi/mức null, hai list rỗng; bài đang mở | false | 0 |
| PROCESSING, chưa quá hạn | Chuỗi/mức null, hai list rỗng | false | 3 |
| PROCESSING, đã quá hạn 90 giây | Chuỗi/mức null, hai list rỗng | true | 0 |
| FAILED | Chuỗi/mức null, hai list rỗng | true | 0 |
| COMPLETED | Năm nội dung bắt buộc khác rỗng, mức hợp lệ; list có thể rỗng | false | 0 |
| INSUFFICIENT_DATA | Chỉ summary giải thích; trường khác null/list rỗng | false | 0 |

GrammarCorrection: messageId, originalContent, correctedContent, feedback. originalContent lấy từ DB; correctedContent tối đa 4000, feedback tối đa 2000; mỗi messageId USER xuất hiện tối đa một lần, cùng conversation. WordSuggestionResponse: id, word (100), meaning (255), example (null/1000), savedWordId (null hoặc ID mục đã lưu của người hiện tại). savedWordId được truy vấn, không tạo cột is_saved.

Nhận xét mỗi đoạn tối đa 3000 ký tự; tối đa 20 câu sửa và 10 từ gợi ý. ID từ do DB cấp, không nhận ID do AI tự tạo. Đầu ra sai cấu trúc/tham chiếu không được lưu dở.

Gọi finish lần nữa không sửa endedAt, không phân tích lại COMPLETED/INSUFFICIENT_DATA. Đang xử lý còn hạn → 202. FAILED/quá hạn → FE dùng nút retry. Nhấn retry khi đã có kết quả terminal chỉ nhận lại kết quả đó; không tạo lượt AI có phí vô hạn.

## 6. Từ cá nhân — ba operation P0

| Method/URL | operationId | Body/kết quả |
| --- | --- | --- |
| GET /saved-words | listSavedWords | Page, tùy chọn status=NEW/LEARNING/MASTERED |
| POST /saved-words/from-suggestions | saveSuggestedWords | {suggestionIds:[601,602]} → {created:[...],existing:[...]} |
| PATCH /saved-words/{id}/review | reviewSavedWord | {status:LEARNING hoặc MASTERED} → SavedWordResponse |

Mỗi created/existing entry có suggestionId và savedWord. SavedWordResponse: id/word/meaning/example/status/lastReviewedAt/createdAt. NEW có lastReviewedAt=null; LEARNING/MASTERED có thời điểm. Không nhận userId/lastReviewedAt từ FE.

Một lần lưu 1–10 ID khác nhau. Chỉ cần một ID không tồn tại/sai ownership/báo cáo chưa hoàn tất là cả nhóm thất bại; không lưu một nửa. Khác ownership dùng 404; trạng thái chưa phù hợp dùng 409 REVIEW_NOT_COMPLETED. Xử lý ID tăng dần; cùng từ bỏ qua tạo trùng và giữ nghĩa cũ. Bản hiện tại khóa user trước mọi lần lưu nên request cùng user được xếp lần lượt trước khi kiểm trùng; UNIQUE(user_id,word) bảo vệ thêm trong DB. Không bắt conflict rồi query tiếp trong transaction đã rollback.

## 7. Năm operation P1 — không chặn nghiệm thu P0

| Method/URL | operationId | Quy tắc |
| --- | --- | --- |
| PATCH /users/me | updateMyProfile | Chỉ name/level; ít nhất một field, không nhận null/id/email/role; không đổi lịch sử |
| DELETE /saved-words/{id} | deleteSavedWord | Xóa của mình; 200 data=null; đã mất/sai chủ sở hữu 404 |
| GET /admin/users | adminListUsers | Phân trang UserResponse an toàn |
| PATCH /admin/users/{id}/active | adminSetUserActive | Chỉ USER; không khóa tài khoản ADMIN, không đổi role; khóa thu hồi phiên |
| GET /admin/statistics | adminGetStatistics | totalUsers, activeTopics, totalConversations, closedConversations, reviewedConversations |

totalUsers chỉ đếm USER; closedConversations có endedAt; reviewedConversations chỉ đếm COMPLETED. Không lấy summary != null làm điều kiện có báo cáo đầy đủ.

## 8. Lỗi và timeout để FE không phải đoán

| HTTP / error | Trạng thái dữ liệu và hành động FE |
| --- | --- |
| 400 VALIDATION_ERROR/INVALID_JSON | Sửa form/body; không lặp tự động |
| 401 UNAUTHORIZED/INVALID_SESSION/INVALID_REFRESH_TOKEN/INVALID_CREDENTIALS | Trong login báo lỗi; trong request bảo vệ refresh một lần; refresh bị từ chối thì về login |
| 403 FORBIDDEN/ACCOUNT_DISABLED | Không cho thao tác; không cố refresh liên tục để xin quyền admin |
| 404 RESOURCE_NOT_FOUND | Tài nguyên không có hoặc không thuộc mình; không lộ thông tin người khác |
| 409 TOPIC_DISABLED | Tải lại danh sách; không tạo bài bằng dữ liệu cũ |
| 409 REQUEST_CONFLICT | Cùng UUID khác dữ liệu; sửa lỗi quản lý requestId, không tự cấp mã khác để che lỗi |
| 409 TURN_PENDING | Khôi phục lượt chưa xong, retry đúng mã hoặc Kết thúc |
| 409 CONVERSATION_CLOSED | Tải lại bài/kết quả; không tiếp tục chat trên bài này |
| 409 TURN_LIMIT_REACHED | Đã đủ 20 USER mới; cho Kết thúc và xem kết quả |
| 409 REVIEW_NOT_COMPLETED | Chưa được lưu các gợi ý không có báo cáo hoàn chỉnh |
| 409 GOOGLE_LINK_REQUIRED/GOOGLE_EMAIL_MISMATCH | Login hiện có rồi liên kết đúng email trong Cá nhân |
| 409 EMAIL_ALREADY_USED/GOOGLE_ALREADY_LINKED/DATA_CONFLICT | Hiển thị lỗi trùng/liên kết; không tự sửa email, đổi chủ sở hữu hoặc tăng quyền |
| 429 TOO_MANY_REQUESTS | Theo Retry-After; auth hiện giới hạn 30 POST/phút/IP |
| 502 AI_UNAVAILABLE/AI_INVALID_RESPONSE | GET trạng thái; giữ transcript; báo cáo lỗi phải FAILED và không lưu dở |
| 503 AI_NOT_CONFIGURED/GOOGLE_NOT_CONFIGURED/PASSWORD_RESET_UNAVAILABLE | Lỗi cấu hình/dịch vụ, báo phù hợp; không giả response thành công |
| 504 AI_TIMEOUT | Như lỗi AI; GET trước khi retry vì client không biết mọi bước đã commit hay chưa |
| 500 INTERNAL_ERROR | Hiện lỗi chung, giữ thông tin khôi phục; không lộ SQL/khóa/token trong response |

Timeout khởi đầu: BE gọi reply tối đa 20 giây, review tối đa 45 giây; mỗi attempt gọi provider một lần. FE read timeout cho send tối thiểu 30 giây, finish/retry tối thiểu 60 giây; không dùng mặc định 15 giây của AuthApi cho hội thoại. Dấu xử lý server hết hạn sau 90 giây. `pollAfterSeconds=3` là nhịp GET, **không phải** quyền chạy một attempt AI mới sau 3 giây.

FE ngừng polling khi rời màn hình; khi trở lại GET trạng thái. Sau khoảng 90 giây chưa xong, hiển thị trạng thái/chờ hoặc Retry theo canRetry/pendingReply; không tự đổi bài về FAILED. Server có quyền quyết định trạng thái.
