# 13 — Audit tích hợp cuối, 29/10/2026

> Đây là mốc audit trước khi nối lại UI. Tiến độ mới, test và lỗi Gemini hiện tại ở [14 — Nối UI Android](14-android-ui-integration.md). Các kết quả phía dưới được giữ nguyên làm bằng chứng tại thời điểm audit.

## A. Kết luận

**NOT READY — bản Android hiện tại chưa sẵn sàng demo vòng học AI thật.** Backend có đủ 24 operation P0; vòng học qua HTTP → MySQL/Gemini thật đã chạy thành công. Bản FE vừa pull từ `dev` chưa chứa lớp Retrofit, quản lý phiên hoặc các lời gọi API người học. Có 20 operation người học cần nối lại phía Android; bốn operation quản lý chủ đề đã có nơi gọi trong web admin.

Đây là audit bản FE bắt đầu từ commit `8ed8ef8`, cùng backend vừa khôi phục. Những kết quả Android E2E PASS trong file 11–12 thuộc bản FE **trước lần pull**, không chứng minh bản APK hiện tại đã tích hợp. Mã tích hợp cũ còn trong `.recovery/20261029/pre-pull-tracked-files.zip` và `pre-pull-untracked-files.zip`; chưa chép đè lên giao diện mới.

Đã sửa các lỗi nhỏ xác định được: hai lỗi lint AppCompat, thiếu quyền Internet/HTTP debug và lựa chọn trình độ C1/C2 không được backend hỗ trợ. Không viết thêm tính năng, sửa schema, nâng dependency hoặc thay provider/model. Giao diện mới và các chức năng offline được giữ lại. Việc nối toàn bộ phần Android đang thiếu cần một lượt tích hợp riêng; đó không phải một bản sửa nhỏ có thể coi là đã hoàn thành trong audit này.

### Bằng chứng chính

| Kiểm tra | Kết quả thực tế |
| --- | --- |
| Backend build/unit | PASS, 77 test, 0 failure/error/skipped |
| MySQL/API test riêng | PASS, 27 test; MySQL tạm thật, Gemini/Google/mail được thay bằng mock |
| MySQL đang dùng | PASS, `speakmate_v3`, Hibernate validate và JDBC chỉ đọc |
| Gemini thật qua backend 8081 | PASS vòng thứ hai: 3 reply + 1 review; không thay model/key |
| Vòng học HTTP thật | PASS, conversation 8/user thử 12, 52 request HTTP gồm kiểm lỗi/quyền; không phải Android E2E |
| JDBC đối chiếu vòng học | PASS: đúng 6 tin, nhận xét, 1 câu sửa, 2 từ gợi ý, 1 từ cá nhân MASTERED; không trùng |
| Android build/unit/lint cuối | PASS, 1 unit test mẫu; lint 0 lỗi, cảnh báo xem mục D |
| Instrumentation Android hiện có | PASS 1 test `useAppContext`; chỉ kiểm package, không kiểm nghiệp vụ |
| Thao tác APK trên emulator | FAIL tích hợp: login báo chưa nối server, chat và nhận xét dùng dữ liệu demo/local |
| SMTP | PASS xác thực STARTTLS thật; không gửi thư, chưa kiểm nhận mã/đổi mật khẩu |
| Google login thật | BLOCKED ở Android: chưa có nút/SDK/đường gọi trong bản FE này; backend đã từ chối token sai bằng 401 |
| Mic/TTS | Đã đọc code permission/lifecycle/lỗi; chưa nghe hoặc nói trên thiết bị demo |

## B. Bảng tương thích API FE–BE

Mọi URL bên dưới có prefix **`/api/v1`**. Success/error đều dùng envelope `{statusCode,error,message,data}`; `error` và `data` có thể null. Android hiện chưa có converter hoặc DTO mạng để đọc envelope, nên không thể đánh dấu việc parse JSON trên app là PASS. Không có Gson converter cần kiểm trong bản hiện tại.

**Ký hiệu kết quả:**

- **NOT IMPLEMENTED (Android):** endpoint BE đã có, đường gọi tương ứng trên Android hiện thiếu.
- **PASS — verified end to end (API):** đã gọi HTTP thật và kiểm dữ liệu dịch vụ/DB, không gồm UI Android.
- **PASS — contract/code-level verification only:** đọc cả nơi gọi và handler; có thể kèm MySQL test riêng, chưa thao tác UI thật.
- **BLOCKED:** phụ thuộc thao tác người dùng/dịch vụ hoặc công cụ chưa kiểm được.
- **FAIL:** có bằng chứng hành vi sai hoặc tích hợp đứt.

`JWT` là header `Authorization: Bearer <access token>`. `ADMIN` được kiểm trên BE, không lấy từ form. Lỗi chung cho request bảo vệ gồm 400 input/JSON, 401 thiếu/hết phiên, 403 thiếu quyền/tài khoản khóa; 404 được dùng để che tài nguyên không thuộc người gọi. Cột mã HTTP ghi thêm các nhánh quan trọng, không phải mọi lỗi hạ tầng có thể xảy ra.

| Màn hình/chức năng | HTTP/endpoint | Controller.handler | Query/body mong đợi | Response BE thực tế / FE đang dùng | Quyền; HTTP quan trọng | Kết quả hiện tại |
| --- | --- | --- | --- | --- | --- | --- |
| Đăng ký | POST `/auth/register` | AuthController.register | `{name,email,password,level?}` | `UserResponse` / FE chỉ hiện thông báo chưa nối | Public; 201,400,409,429 | Android NOT IMPLEMENTED; BE PASS API thật |
| Đăng nhập | POST `/auth/login` | AuthController.login | `{email,password}` | `TokenResponse` / FE chưa gửi request | Public; 200,400,401,403,429 | Android FAIL trên emulator; BE PASS API thật |
| Google sign-in | POST `/auth/google` | AuthController.google | `{idToken}` | `TokenResponse` / thiếu nút và helper Google | Public; 200,401,409,503 | Android NOT IMPLEMENTED; Google thật BLOCKED; token sai thực tế 401 |
| Refresh phiên | POST `/auth/refresh` | AuthController.refresh | `{refreshToken}` | `TokenResponse` / thiếu session manager | Public; 200,400,401,403 | Android NOT IMPLEMENTED; BE PASS API thật, token cũ bị từ chối |
| Mở lại app/hồ sơ | GET `/auth/me` | AuthController.me | Không body | `UserResponse` / hồ sơ local | JWT; 200,401,403 | Android NOT IMPLEMENTED; BE PASS API thật |
| Đăng xuất | POST `/auth/logout` | AuthController.logout | Không body | `data=null` / chưa có phiên server để thu hồi | JWT; 200,401 | Android NOT IMPLEMENTED; BE PASS API thật, access/refresh sau logout bị từ chối |
| Liên kết Google | POST `/auth/google/link` | AuthController.linkGoogle | `{idToken}` | `UserResponse` / thiếu đường gọi | JWT; 200,401,409,503 | Android NOT IMPLEMENTED; BE PASS code/MySQL test mock; Google thật BLOCKED |
| Xin mã reset | POST `/auth/password/forgot` | PasswordResetController.forgotPassword | `{email}` | 202, `data=null` / nút báo đang cập nhật | Public; 202,400,429,503 | Android NOT IMPLEMENTED; SMTP auth PASS; giao thư BLOCKED |
| Đổi mật khẩu bằng mã | POST `/auth/password/reset` | PasswordResetController.resetPassword | `{email,code,newPassword}` | 200, `data=null` / thiếu form gọi API | Public; 200,400,429 | Android NOT IMPLEMENTED; BE PASS MySQL test với mail mock; nhận mã thật BLOCKED |
| Danh sách tình huống | GET `/topics` | TopicController.list | `page=1,size=20`; size 1–100 | `Page<TopicResponse>` / bốn tên cố định trong FE | JWT; 200,400 | Android NOT IMPLEMENTED; BE PASS API thật |
| Bắt đầu bài | POST `/conversations` | ConversationController.create | `{topicId,level,createRequestId}` | `ConversationDetail` / FE mở chat bằng level/tên chủ đề | JWT; 201 mới,200 replay,400,404,409 | Android NOT IMPLEMENTED; BE PASS API thật/replay cùng ID |
| Lịch sử | GET `/conversations` | ConversationController.list | `page,size` | `Page<ConversationSummary>` / SpeakingHistoryStore local | JWT; 200,400 | Android NOT IMPLEMENTED; BE PASS API thật |
| Transcript/khôi phục bài | GET `/conversations/{id}` | ConversationController.get | `id` là Long của BE | `ConversationDetail` / danh sách text trong Bundle/local | JWT + owner; 200,404 | Android NOT IMPLEMENTED; BE PASS API thật |
| Gửi lời người học | POST `/conversations/{id}/messages` | ConversationController.send | `{content,requestId}` | `TurnResponse` / `getDemoReply()` | JWT + owner; 200,202,400,404,409,502/503/504 | Android FAIL trên emulator; BE PASS Gemini thật, replay không tạo tin mới |
| Kết thúc bài | POST `/conversations/{id}/finish` | ReviewController.finish | Không body | `ReviewResponse` / tổng kết local | JWT + owner; 200,202,404,502/503/504 | Android NOT IMPLEMENTED; BE lần đầu 500, lần chạy tách JAR PASS; xem D |
| Đọc nhận xét | GET `/conversations/{id}/review` | ReviewController.get | Không body | `ReviewResponse` / placeholder theo số lượt nói | JWT + owner; 200,404 | Android FAIL tích hợp; BE PASS API/JDBC thật |
| Thử lại nhận xét | POST `/conversations/{id}/review/retry` | ReviewController.retry | Không body; theo `canRetry` của server | `ReviewResponse` / chưa có polling/retry server | JWT + owner; 200,202,404,409,502/503/504 | Android NOT IMPLEMENTED; BE PASS MySQL test có AI mock; ownership 404 thật |
| Từ đã lưu | GET `/saved-words` | SavedWordController.list | `page,size,status?=NEW/LEARNING/MASTERED` | `Page<SavedWordResponse>` / DemoFlashcardRepository/LocalDeckStore | JWT; 200,400 | Android NOT IMPLEMENTED; BE PASS API thật |
| Lưu từ trong bài | POST `/saved-words/from-suggestions` | SavedWordController.save | `{suggestionIds:[Long...]}`, 1–10 ID khác nhau | `{created:[{suggestionId,savedWord}],existing:[...]}` / FE tạo deck local từ từ mẫu | JWT + owner + review COMPLETED; 200,400,404,409 | Android NOT IMPLEMENTED; BE PASS API/JDBC thật, lưu lại không trùng |
| Tự đánh giá thẻ | PATCH `/saved-words/{id}/review` | SavedWordController.review | `{status:LEARNING hoặc MASTERED}` | `SavedWordResponse` / FlashcardProgressStore local | JWT + owner; 200,400,404 | Android NOT IMPLEMENTED; BE PASS API/JDBC thật |
| Web admin — list | GET `/admin/topics` | AdminTopicController.list | `page,size` | `Page<AdminTopicResponse>`; admin.js đọc `data.result/meta` đúng | ADMIN; 200,400,401,403 | PASS contract/code + MySQL test; thiếu token 401, USER 403 thật; UI ADMIN BLOCKED |
| Web admin — thêm | POST `/admin/topics` | AdminTopicController.create | `{name,description?,level,scenario}` | `AdminTopicResponse`; admin.js gửi đúng body | ADMIN; 201,400,401,403,409 | PASS contract/code + MySQL test; USER 403 thật; UI ADMIN BLOCKED |
| Web admin — sửa | PUT `/admin/topics/{id}` | AdminTopicController.update | Body như thêm; không gửi `active` ở đây | `AdminTopicResponse`; admin.js dùng PUT đúng | ADMIN; 200,400,401,403,404 | PASS contract/code + MySQL test; USER 403 thật; UI ADMIN BLOCKED |
| Web admin — bật/tắt | PATCH `/admin/topics/{id}/active` | AdminTopicController.active | `{active:boolean}` | `AdminTopicResponse`; admin.js đúng field | ADMIN; 200,400,401,403,404 | PASS contract/code + MySQL test; USER 403 thật; UI ADMIN BLOCKED |

### Các kiểu response đã đối chiếu DTO và JSON thật

| Kiểu | Field/kiểu/nullable cần giữ |
| --- | --- |
| `UserResponse` | `id:Long,name,email,level,role,active:boolean,passwordEnabled:boolean,googleLinked:boolean`; không có hash/token reset |
| `TokenResponse` | `access_token,refresh_token,token_type,expires_in,user`; token dùng snake_case, request refresh dùng **refreshToken** |
| `Page<T>` | `{meta:{page,pageSize,pages,total},result:[T]}`; page từ **1**, không phải 0; giới hạn 100 |
| `TopicResponse` | `id,name,description?,level`; không có scenario/active; topic đã tắt không nằm trong list người học |
| `AdminTopicResponse` | `id,name,description?,level,scenario,active` |
| `ConversationSummary` | `id,topicId,topicName,profileLevelSnapshot,level,status,reviewStatus,createdAt,endedAt?` |
| `ConversationDetail` | Summary và `scenario,userTurnCount,pendingReply?,messages:[]`; không đọc lỗi ngữ pháp trong khi chat |
| `MessageResponse` | `id,sender:USER/AI,content,requestId,createdAt` |
| `TurnResponse` | `conversationId,requestId,replyStatus,userMessage,aiMessage?,pollAfterSeconds,replayed` |
| `ReviewResponse` | `conversationId,reviewStatus,summary?,strengths?,weaknesses?,recommendedLevel?,levelFeedback?,grammarCorrections:[],suggestedWords:[],canRetry,pollAfterSeconds` |
| `GrammarCorrection` | `messageId:Long,originalContent,correctedContent,feedback`; originalContent lấy DB |
| `WordSuggestionResponse` | `id:Long,word,meaning,example?,savedWordId?` |
| `SavedWordResponse` | `id,word,meaning,example?,status,lastReviewedAt?,createdAt`; trạng thái NEW chưa có thời điểm ôn |

`description/example/endedAt/...` nullable phải được xử lý khi bind UI. `Instant` ở BE trả chuỗi ISO-8601 UTC; DB dùng DATETIME(6). Không so nanosecond tuyệt đối với dữ liệu đã ghi qua MySQL. Các list rỗng vẫn là list, không tự dựng nội dung để lấp UI.

### Mismatch và phần thiếu đã xác nhận

1. **Không có transport/session:** không tìm thấy Retrofit interface, auth service, token interceptor/manager, DTO mạng hoặc lời gọi HTTP trong `frontend/app/src`. Dependency Retrofit/Credential Manager cũng chưa có trong Gradle hiện tại. Không có request để đối chiếu Gson hoặc bearer header trên Android.
2. **Tên đăng nhập:** `LoginFragment` nhận `username`; BE yêu cầu `email`. `RegisterFragment` có username bắt buộc nhưng BE không có field này và từ chối field lạ. Khi nối phải bind email/name đúng DTO, không thêm cột username chỉ để hợp form.
3. **Chủ đề và bài:** FE truyền tên chủ đề/String và tạo UUID local cho lịch sử; BE cần topicId Long lấy từ GET topics, createRequestId UUID v4, sau đó dùng conversationId Long BE trả về. Không suy ra ID từ thứ tự radio button/tên chủ đề.
4. **Nguồn dữ liệu:** chat gọi `getDemoReply()`, review dùng nội dung chuẩn bị sẵn; lưu từ/history/thẻ đi vào local stores. Những thao tác này không đồng bộ MySQL và không chứng minh ownership theo tài khoản.
5. **Trình độ:** BE enum/SQL chỉ A1,A2,B1,B2. Đã giới hạn UI luyện nói/hồ sơ/form chủ đề cho khớp, fallback lựa chọn C1/C2 cũ về A2. Level cho các deck offline không được tự gửi vào API P0.
6. **URL:** backend thật ở 8081; URL emulator trong file local đúng `http://10.0.2.2:8081/`, Web client ID giữa hai file local khớp nhau. Tuy nhiên Gradle FE chưa sinh `API_BASE_URL`, APK không có URL này; local properties chưa được build script đọc vào. Vì chưa có Retrofit, không thể báo URL app đã cấu hình đúng chỉ dựa vào file local. Khi nối lại, Retrofit dùng base `http://10.0.2.2:8081/` và thêm `api/v1/` đúng một lần.
7. **Network permission:** bản đầu thiếu INTERNET, debug chưa cho HTTP. Đã bổ sung quyền và cho HTTP riêng debug; thao tác này chưa tạo ra bất kỳ kết nối API nào.

Không thiếu endpoint lấy danh sách level: enum bốn mức là contract hiện hành. Không thêm API decks/generate-deck/admin users/statistics chỉ vì FE có màn preview; năm operation P1 trong OpenAPI và các tính năng ngoài P0 vẫn hoãn.

## C. Kết quả theo vòng đời

### Backend và dịch vụ thật

Vòng API thành công dùng tài khoản thử mới, không đổi người dùng cũ:

1. Register 201 → login 200 → me 200; profile A1, chọn topic cà phê và mức B2.
2. Create 201, gửi lại cùng createRequestId nhận 200 và cùng conversationId **8**.
3. Ba câu USER nhận ba lời AI thật nối tiếp ngữ cảnh; transcript sáu tin USER/AI đúng thứ tự. Gửi lại câu đầu với cùng requestId nhận cùng message IDs, `replayed=true`, không thêm tin.
4. Trước finish chưa có corrections/feedback. Finish 200 → COMPLETED; GET review trả cùng dữ liệu.
5. Correction gắn message **45**, câu gốc đúng transcript: `Yesterday I go` được sửa thành `Yesterday I went`; phần còn lại của câu được giữ. Giải thích thì quá khứ phù hợp ví dụ này. Hai từ gợi ý `take away` và `receipt` có trong hội thoại. Đây là đánh giá thủ công **một mẫu**, không xác nhận mọi đầu ra AI đúng hoặc trình độ CEFR đã được kiểm định.
6. Lưu suggestion 18 → saved word **6**; lưu lại trả existing. PATCH LEARNING rồi MASTERED; GET lại giữ đúng dữ liệu/thời gian ôn.
7. Reopen history và review giữ đúng bài, transcript và nhận xét. Profile vẫn A1, không bị tự đổi theo recommendation A2.
8. Tài khoản thử thứ hai không đọc/ghi bài/từ của người thứ nhất: 404; list history/vocabulary rỗng. USER gọi cả bốn method quản lý topic nhận 403. Thiếu token nhận 401.
9. Refresh đổi cặp token; dùng lại refresh cũ nhận 401. Logout thu hồi phiên; access và refresh sau logout đều bị từ chối.
10. JDBC chỉ đọc đối chiếu exact message/review/correction/suggestions/word. Chênh timestamp ôn **300 ns**, nằm trong độ chính xác microsecond của MySQL.

Bằng chứng: `backend/build/final-audit/live-api-stable.json`, file cùng tên đuôi `.sql-check.txt`, `run_live_api.py`, `backend-stable.log`. Có **52 HTTP checks**, không đồng nghĩa 52 lần gọi Gemini. Vòng thành công có 3 reply và 1 review; replay câu đầu khoảng 0,04 giây. Reply mới khoảng 6,16 / 5,83 / 8,13 giây; finish khoảng 7,28 giây.

### Android hiện tại — kiểm trên emulator thật

Đã cài APK, thao tác Cá nhân → Đăng nhập → điền form → bấm đăng nhập; UI hiện **“Chức năng đăng nhập chưa kết nối máy chủ.”**

Sau đó mở lại Home → Bắt đầu → chọn bài → chat. App mở được khi chưa có phiên server, hiển thị **“Chế độ demo · Phản hồi mẫu, chưa kết nối AI”**. Câu thử `Yesterday I go to a cafe.` nhận câu mẫu `Would you like something to drink?`. Kết thúc mở màn “Chưa có đánh giá”, nội dung nói rõ chưa nối phân tích. Đây là UI demo đã được gắn nhãn, không phải Gemini.

Bằng chứng trước/sau sửa nằm trong `backend/build/final-audit/android-ui-audit.json`, các XML màn hình, và thư mục `ui-final/`. Mật khẩu nhập thử tự sinh chỉ ở bộ nhớ; node password được che trước khi lưu XML. Không dùng hoặc thay mật khẩu tài khoản người phát triển.

| Luồng yêu cầu | Android hiện tại | Backend/kiểm khác |
| --- | --- | --- |
| Register/login/restore/logout | NOT IMPLEMENTED qua API | PASS HTTP thật cho auth local/refresh/logout; MySQL tests thêm concurrency/session |
| Google sign-in/link | NOT IMPLEMENTED; không có UI/SDK của bản tích hợp cũ | BLOCKED Google thật; token sai 401; test chữ ký dùng khóa test, không phải Google thật |
| Forgot/reset | Nút placeholder, thiếu luồng gọi server | SMTP auth thật PASS; reset transaction/MySQL test dùng mail mock; chưa gửi/nhận mã thật |
| Topic → create → send → Gemini | FAIL tích hợp, đang dùng dữ liệu local | PASS API/Gemini/MySQL thật |
| Finish → feedback → save vocabulary | FAIL tích hợp | PASS vòng API thứ hai; giữ bằng chứng lần đầu lỗi ở D |
| Study flashcards → history | Chỉ lưu/đọc local; chưa lấy dữ liệu tài khoản | PASS API/JDBC thật cho status/persistence/history/ownership |
| Mic permission/STT/TTS | PASS code-level cho permission/lỗi/lifecycle; audio BLOCKED | Chưa kiểm nói/nghe trên máy demo; emulator audit chạy `-no-audio` |
| Admin topic management | App admin preview chưa nối; web admin có fetch thật | PASS source/DTO và MySQL test riêng; web CRUD bằng ADMIN thật BLOCKED thao tác thủ công |

### Các ca lỗi quan trọng

| Ca | Đã kiểm ở đâu | Kết luận/phạm vi |
| --- | --- | --- |
| AI JSON sai/truncated/empty/unknown field | GeminiAiClientTest, ReviewValidatorTest | PASS mock/unit; chưa cố tình ép Gemini thật trả dữ liệu lỗi |
| Provider timeout | GeminiAiClientTest: timeout future/HTTP, cancel request | PASS mock/unit; reply 20s/review 45s. Android chưa có network timeout/recovery để nghiệm thu |
| AI lỗi giữ câu người học | StudyIntegrationTest + lần live đầu | PASS DB test; live lỗi review vẫn giữ 6 tin và trạng thái FAILED |
| Double submit/replay | StudyIntegrationTest + live API | PASS BE/DB thật; Android chưa có PendingRequests/UUID mạng |
| Finish khi đang chờ reply, late response, lease hết hạn | StudyIntegrationTest | PASS trên MySQL tạm với AiClient mock; chưa có network Android để kiểm navigation tương ứng |
| Rời màn hình khi chờ | Đọc Chat/HoldToSpeak/DeckSpeech; native UI local | Có stop/cancel giọng nói; **NOT IMPLEMENTED** cho API in-flight vì FE hiện không gửi API |
| Đọc/sửa dữ liệu người khác | Live HTTP + JDBC + StudyIntegrationTest | PASS BE: 404 và không lẫn dữ liệu; local stores không thay thế kiểm ownership |
| Thiếu token/USER gọi admin | Live HTTP | PASS 401/403; không dùng UI role làm bằng chứng cấp quyền |
| Input/pagination | Live HTTP `page=0`, DTO/test Jackson | PASS 400; field lạ bị từ chối; không trả mật khẩu/hash trong UserResponse |

### Database và bảo mật

- Kết nối thật `speakmate_v3`; không import lại `speakmate.sql`, reset/drop database hoặc xóa dữ liệu. Test live chỉ tạo dữ liệu thử mới.
- Metadata thực: **7 bảng InnoDB**, collation `utf8mb4_0900_as_ci`, **7 PK, 8 FK, 9 UNIQUE, 48 CHECK**. Hibernate validate thành công cho sáu Entity nghiệp vụ; bảng `word_practices` còn hoãn theo phạm vi đã chốt.
- FK: conversation → user/topic; message/suggestion → conversation; saved_word → user; word_practice → conversation/saved_word/evidence_message. Mapping User/Topic/Conversation/Message/WordSuggestion/SavedWord được kiểm bằng schema validate và test database.
- Quyền lấy từ JWT/DB. Các query đọc/ghi tài nguyên có owner; transaction ghi khóa user trước conversation và kiểm lại authVersion. Gọi AI ngoài transaction, chỉ attempt token hiện hành được commit. Review/corrections/suggestions lưu cùng transaction; lưu batch vocabulary kiểm toàn bộ trước khi ghi.
- Token reset/refresh được hash, mật khẩu dùng BCrypt. Error handler trả envelope chung, không trả SQL, stack trace hoặc payload provider. Đã đọc đường xử lý, không coi UI validation là kiểm quyền.
- Quét nguồn/tracked và DEX APK, thêm log của đợt audit: không thấy giá trị Gemini/SMTP/JWT local hoặc pattern Google secret được kiểm. Số file/lần quét cuối tại `verification-summary.json`. Local BE/Android và recovery đều bị Git bỏ qua. Đây là phép kiểm cụ thể theo các giá trị/pattern đã nêu, không phải chứng nhận bảo mật toàn diện.

## D. Build, test và các lần thất bại

PowerShell tại đúng thư mục dự án; Gradle cache hiện có trên máy, không thay dependency/toolchain của nhóm.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:GRADLE_USER_HOME = 'C:\Users\PC\.gradle'

# Trong backend — ép biên dịch/test lại, không dùng kết quả cache làm bằng chứng mới.
.\gradlew.bat build --rerun-tasks --offline --no-daemon `
    '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-21'
& .\scripts\Test-MySql.ps1

# Trong frontend — giữ Gradle 9.6/AGP 9.4.1/SDK 37 của bản FE hiện tại.
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug `
    --no-daemon --console=plain `
    '-Dorg.gradle.java.installations.paths=C:\Users\PC\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2,C:\Program Files\Java\jdk-21'
```

- BE build ép chạy lại: **SUCCESSFUL**, 77 unit, 0 fail/error/skipped. Log `backend/build/final-audit/backend-build.log`.
- `Test-MySql.ps1`: **SUCCESSFUL**, 27 test, 0 fail/error/skipped, lần chuẩn chạy khoảng 1 phút 1 giây. Script tạo datadir TEMP và MySQL 13317 riêng, kiểm datadir trước import/shutdown/cleanup; không dùng MySQL 3306 để chạy fixture.
- Android sau sửa lint: SUCCESSFUL 2 phút 39 giây. Sau sửa manifest: SUCCESSFUL 29 giây. Sau sửa level cuối: **SUCCESSFUL 54 giây**, 22 task chạy / 54 up-to-date; **0 lint errors, 588 warnings**. Log cuối `android-final-levels-build.log`.
- Android unit hiện có chỉ 1 `ExampleUnitTest`. Instrumentation chạy bằng ADB, lớp `com.example.speakmate.ExampleInstrumentedTest`, **1 test PASS** trước và sau bản cuối; chỉ assert package context. Các test auth/learning fixture/live cũ không còn trong source FE hiện tại, chưa được chạy như thể vẫn tồn tại.
- Cảnh báo đã có: Java test dùng unchecked operation, JVM class-data-sharing, Hibernate setting incubating; Android Java dùng deprecated API, nhiều lint warning. Không tắt lint hoặc tạo baseline để giấu lỗi.
- APK cuối: `frontend/app/build/outputs/apk/debug/app-debug.apk`; không đưa key vào APK. Lint/report/mã băm cuối được ghi ở `backend/build/final-audit/verification-summary.json`.

### Lỗi đã gặp và cách phân biệt

1. **Lint ban đầu:** 2 `RestrictedApi`, 588 warnings. `DeckDetailFragment` gọi `setSupportBackgroundTintList` của AppCompat ngoài phạm vi cho phép. Đã dùng API public `setBackgroundTintList` (minSdk 26) và lint chạy lại không còn lỗi.
2. **Live vòng đầu:** `POST /conversations/7/finish` trả **500 INTERNAL_ERROR**, log chỉ có `IllegalStateException`. Request đi qua ReviewController → ReviewService → claim/review/complete; DB sau lỗi vẫn CLOSED/FAILED, processing token đã xóa, đủ 6 tin. Không có kết quả feedback thành công để ghi nhận cho lần này.
3. Lần đầu đã chạy build trong lúc backend đọc JAR cùng đường dẫn. JAR có mtime 18:55:42, lỗi ghi lúc 18:55:41. Đã dừng tiến trình đó và dùng bản copy `build/final-audit/speakmate-runtime.jar` độc lập với output build; **cùng source BE**, vòng mới conversation 8 PASS. Đây là dấu hiệu liên quan việc thay JAR khi chạy; **không đủ stack trace để khẳng định nguyên nhân gốc của IllegalStateException**. Không ghi là đã vá một bug BE. Khi trình bày, build xong rồi mới chạy backend; cần điều tra tiếp nếu 500 tái diễn trên JAR không bị thay.
4. **Lần gọi script MySQL có redirect `*>`:** PowerShell coi cảnh báo JVM từ native stderr là lỗi khi script bật Stop. Đã chạy lại lệnh chuẩn không bọc redirect và cả 27 test PASS. Không quy lỗi này cho schema hoặc mật khẩu.
5. **Công cụ JS:** `node --check .../admin.js` không chạy do Node CLI không có trong PATH; Node REPL thay thế báo lỗi metadata sandbox `missing field sandboxPolicy`. Chưa kiểm cú pháp JS bằng công cụ ở đợt này. HTTP GET web admin và admin.js đều 200, nội dung khớp source; điều đó chưa chứng minh CRUD bằng UI.
6. **Emulator:** lần dump đầu thấy “Pixel Launcher isn't responding”, đã đóng hộp lỗi hệ thống trên emulator riêng và mở lại SpeakMate thành công. Sau đó thao tác UI và instrumentation chạy được; không sửa app để che lỗi launcher. Log backend còn ghi clock-leap/thread-starvation khi máy bị tạm dừng lâu; không đánh dấu đó là lỗi logic ứng dụng.

## E. File thay đổi và lý do

| File | Thay đổi nhỏ |
| --- | --- |
| `frontend/app/src/main/java/com/example/speakmate/DeckDetailFragment.java` | Hai lệnh tint đổi sang public API, sửa 2 lint errors |
| `frontend/app/src/main/AndroidManifest.xml` | Bổ sung INTERNET còn thiếu |
| `frontend/app/src/debug/AndroidManifest.xml` | Cho HTTP 8081 riêng debug; không mở cleartext cho release |
| `frontend/app/src/main/java/com/example/speakmate/SpeakingFragment.java` | Chỉ nhận A1–B2, lựa chọn cũ không hợp lệ quay về A2 |
| `frontend/app/src/main/res/layout/fragment_speaking.xml` | Ẩn C1/C2, mô tả bốn mức được hỗ trợ |
| `frontend/app/src/main/java/com/example/speakmate/ProfileFragment.java` | Danh sách mức phù hợp contract |
| `frontend/app/src/main/java/com/example/speakmate/HomeFragment.java` | Không hiển thị practice level C1/C2 như mức đang hỗ trợ |
| `frontend/app/src/main/java/com/example/speakmate/AdminTopicFormFragment.java` | Form chủ đề dùng bốn mức của TopicRequest/SQL |
| File 13 và ghi chú đầu file 04/11/12/README contract | Tách trạng thái FE hiện tại khỏi bằng chứng E2E lịch sử |

Không thay Controller/Service/DTO/Entity BE, endpoint, schema, local credentials, provider/model hoặc phiên bản dependency. Diff `.gitignore` và các file BE/Docs untracked đã có từ lần phục hồi trước audit, được giữ nguyên. Helper/evidence mới nằm trong `backend/build/final-audit/`, được ignore. Bản FE trước audit được lưu thêm ở `.recovery/20261029/final-audit-frontend-before.zip`.

## F. Điểm chặn và hành động tiếp theo

| Mức | Còn thiếu | Hành động cụ thể |
| --- | --- | --- |
| P0 | 20 kết nối API người học chưa có trên bản Android hiện tại | Dùng lại `api/*`, `auth/*`, helper network/pending từ backup làm nền; bind vào giao diện mới, không chép đè cả FE. Nối theo thứ tự dưới đây |
| P0 | Chưa có native Android → BE → Gemini/MySQL E2E của APK này | Sau khi nối, cài APK đúng URL, chạy vòng text trọn vẹn và đối chiếu DB; không dùng test mẫu package context thay thế |
| P1 cần theo dõi | 500 review của lần chạy JAR bị build đồng thời | Giữ log/lần thất bại; build trước khi start, không ghi lại JAR đang chạy. Nếu tái diễn trên runtime ổn định, ghi stack frame an toàn để tìm chính xác nơi ném IllegalStateException |
| Chặn phần demo Google/email | Google UI/link/reset form chưa nối; chưa nhận Google ID token thật hoặc mã email thật | Nối lại UI trước, rồi kiểm Cloud client/package/SHA-1 và hộp thư. Web client ID/SMTP đã có, không cần gửi secret qua chat |
| Chặn tuyên bố voice hoàn tất | Chưa nói/nghe trên thiết bị demo | Thực hiện checklist giọng nói dưới đây |
| Admin web chưa nghiệm thu UI | Chưa login/CRUD bằng ADMIN thật trong đợt này | Thao tác một topic thử riêng, không sửa chủ đề/dữ liệu đang dùng; kiểm list người học bằng API |

### Thứ tự tích hợp để cứu bản demo

1. Khôi phục lớp Retrofit/session từ backup, đọc URL public từ local properties vào BuildConfig; emulator `http://10.0.2.2:8081/`. Không đưa khóa Gemini/SMTP/JWT vào Android. Không tạo thêm một lớp refresh song song.
2. Bind LoginFragment/RegisterFragment bằng email/name đúng DTO; lưu phiên, GET me khi mở lại, một lần refresh khi 401, logout thu hồi server và xóa phiên local.
3. SpeakingFragment GET topics và giữ topicId; POST create giữ UUID/body trước mạng; ChatFragment dùng conversationId, gửi UUID ổn định và hiển thị messages server. Bỏ phản hồi mẫu khỏi đường P0.
4. Finish → GET review/poll/retry theo trạng thái server; bind feedback/corrections đúng messageId, save suggestion IDs. Không sinh nhận xét/từ theo chủ đề local để thay dữ liệu server.
5. Từ của tôi lấy saved-words, PATCH trạng thái ôn; history lấy conversations. Các deck offline giữ nhãn offline, không xem là dữ liệu riêng của user server.
6. Chạy lại native E2E thật, rồi mic/TTS, Google/reset và web admin. Các API P1/admin dashboard/users/decks tiếp tục hoãn.

### Bước thủ công cụ thể sau khi nối UI

- **Auth:** đăng ký email mình sở hữu, login, đóng/mở app, gọi màn bảo vệ; logout rồi Back không được mở lại dữ liệu tài khoản. Đăng nhập tài khoản khác để kiểm history/vocabulary riêng.
- **Google:** dùng Web client ID đã cấu hình làm audience; Android OAuth client đúng package `com.example.speakmate` và SHA-1 từ `gradlew.bat signingReport`. Dùng thiết bị có Play services/test user, bấm Google, kiểm `/auth/me`. Email đã có tài khoản mật khẩu phải login và link cùng email trước. Backend nhận Google ID token, không nhận Google access token/client secret. Hướng dẫn Cloud tại `SpeakMate-luong-tai-khoan.md`.
- **Email:** dùng Postman hoặc UI đã nối: POST `/auth/password/forgot` với `{email:<email tài khoản mật khẩu mình sở hữu>}`; kiểm inbox/spam; POST `/auth/password/reset` với `{email,code,newPassword}`; mật khẩu cũ bị từ chối, mật khẩu mới login được, mã dùng lại bị từ chối. Không chia sẻ code/password/token hoặc xuất chúng vào report. HTTP 202 chưa chứng minh thư đến.
- **Voice:** trên thiết bị demo, từ chối RECORD_AUDIO rồi thử nhập text; cấp lại quyền, giữ mic nói tiếng Anh, thả để nhận dạng; kiểm transcript; bật tự đọc AI, nghe phản hồi, nói lượt tiếp; Back/ra nền/kết thúc phải dừng mic/TTS và không chèn callback cũ. Thử khi dịch vụ nhận dạng/TTS không sẵn sàng. Chưa có bước này PASS thì trình bày là STT/text/TTS đang cần nghiệm thu.
- **Admin:** mở `http://localhost:8081/admin/index.html`, dùng ADMIN riêng; thêm topic tên riêng cho demo, sửa scenario/description, tắt rồi GET `/api/v1/topics` không thấy topic đó, bật lại thì thấy. Kiểm bằng USER phải 403. Hiện Android có danh sách tĩnh nên chưa thể chứng minh thay đổi admin hiển thị trên app.
- **Mạng/lỗi:** sau khi nối, gửi một câu rồi ra nền/quay lại, gửi lặp nhanh, ngắt mạng trước khi nhận response, GET trạng thái rồi retry cùng UUID; không cấp UUID mới cho cùng thao tác. Review lỗi phải giữ transcript và có retry theo canRetry. Không cố ép public Gemini lỗi nhiều lần chỉ để trình diễn.

### Có thể trình bày trung thực ngay bây giờ

1. Giới thiệu UI Android là **bản demo giao diện/offline**, chỉ ra nhãn phản hồi mẫu; không gọi đó là hội thoại Gemini.
2. Trình bày vòng backend thật đã kiểm chứng: đăng nhập → topic/level → chat → feedback → từ → trạng thái ôn → history bằng API và dữ liệu thử conversation 8. Chỉ ra câu sửa và DB persistence. Đây là demo backend, chưa phải sản phẩm Android tích hợp.
3. Trình bày code ownership, UUID/replay, transaction khi gọi AI và vòng đời FAILED/retry; phân biệt 77 unit, 27 MySQL test mock AI với bằng chứng Gemini thật.
4. Chỉ chuyển sang demo sản phẩm trọn vòng sau khi bước tích hợp P0 và native E2E của **APK hiện tại** đã thực sự PASS. Không lấy kết quả lịch sử của APK khác để thay thế.

Không commit, push, merge hoặc thay cấu hình GitHub trong đợt audit.
