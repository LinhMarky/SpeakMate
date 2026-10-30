# SpeakMate

Ứng dụng Android luyện hội thoại tiếng Anh theo chủ đề và trình độ. Người học và AI nói chuyện theo lượt; khi kết thúc bài, backend sinh nhận xét và từ gợi ý để người học chọn lưu, ôn bằng flashcard. Android dùng Java/XML/Retrofit; backend dùng Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.8, MySQL và Gemini qua backend.

**Bắt đầu bằng Docker bên dưới.** Đây là hướng dẫn chính cho nhóm 1 FE + 1 BE. Hợp đồng request/response ở [bộ tài liệu FE–BE](Docs/Contract/README.md); không dùng dữ liệu mẫu làm bằng chứng API thật.

> Kiểm chứng gần nhất, 30/10/2026: đã hoàn thiện từ AI → lưu/ôn flashcard, C1/C2 và lịch sử server. Một vòng UI thật chọn C2, 3 reply + review Gemini, lưu/ôn từ và mở lại lịch sử đã PASS, dữ liệu khớp MySQL. Backend local **8081**, Docker host **8082**; chưa xác nhận âm thanh/Google/SMTP hoặc máy chủ demo chung. Xem [kết quả và hướng dẫn migration C1/C2](Docs/Contract/16-hoan-thien-tu-ai-cap-do-va-lich-su.md).

## 1. Môi trường cần có

- Windows, Git và Docker Desktop đang chạy **Linux containers**, Docker Compose v2 (`docker compose version`). Docker Desktop cần WSL 2/ảo hóa hoạt động theo cài đặt của máy.
- Có mạng để tải image, Gradle/dependency và gọi Gemini.
- Chạy backend bằng Docker **không cần cài Java, Gradle, Maven hoặc MySQL riêng**. Dockerfile dùng Wrapper trong repo, chạy unit test rồi build JAR.
- Nếu làm Android: cài Android Studio, SDK theo `frontend/app/build.gradle.kts` (hiện compile/target SDK 37), emulator hoặc điện thoại. Docker không build/chạy Android.

Mỗi máy local tự đặt hai mật khẩu MySQL Docker và JWT secret. Muốn dùng AI thật cần Gemini API key hợp lệ của thành viên hoặc key được người quản lý cấp riêng. Không lấy secret từ GitHub và không gửi secret vào chat/log nhóm.

## 2. Clone nhánh dev

Chạy trong PowerShell tại thư mục muốn chứa dự án:

```powershell
git clone https://github.com/LinhMarky/SpeakMate.git
cd SpeakMate
git switch dev
```

Nếu đã clone, kiểm tra trước:

```powershell
git status --short --branch
git branch --show-current
```

Chỉ chạy `git pull --ff-only` khi đang ở `dev`, working tree sạch và đã bảo toàn công việc local. Nếu có thay đổi, lưu bằng commit trên nhánh riêng hoặc bản sao được kiểm tra; file untracked cũng cần bảo toàn. Không reset/clean để ép pull. Khi bàn giao phải đưa đầy đủ source/backend/Docker/SQL lên `dev`; chỉ push Compose sẽ không đủ để đồng đội build.

## 3. Tạo và điền .env

Đứng tại thư mục gốc `SpeakMate`:

```powershell
if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
}
notepad .env
```

Giữ `.env` hiện có; không chép mẫu rỗng đè lên. `.env` bị Git bỏ qua. Backend Docker không đọc `backend/application-local.properties` trên host.

| Biến trong .env | Bắt buộc / mặc định | Điền thế nào? |
| --- | --- | --- |
| `COMPOSE_PROJECT_NAME` | Mẫu: `speakmate` | Tên project Docker, quyết định tên volume. Giữ ổn định để dùng lại dữ liệu. |
| `BACKEND_HOST_PORT` | `8081` | Port host; đổi `8082` nếu 8081 bận. **Tên biến là BACKEND_HOST_PORT**, không phải BACKEND_PORT. |
| `MYSQL_HOST_PORT` | `3307` | MySQL chỉ được publish vào `127.0.0.1` của host. Backend kết nối nội bộ `mysql:3306`. |
| `MYSQL_USER` | `speakmate` | Tài khoản ứng dụng cho MySQL Docker; không đặt `root`. |
| `MYSQL_PASSWORD` | **Bắt buộc** | Tự đặt mật khẩu tài khoản ứng dụng Docker. |
| `MYSQL_ROOT_PASSWORD` | **Bắt buộc** | Tự đặt mật khẩu quản trị MySQL Docker, khác mật khẩu ứng dụng. |
| `JWT_SECRET` | **Bắt buộc** | Base64 của ít nhất 32 byte ngẫu nhiên, tạo một lần như bên dưới. |
| `GEMINI_API_KEY` | Cần cho AI thật | Điền key hợp lệ. Trống vẫn khởi động, nhưng chat/nhận xét AI không chạy. |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Giữ model backend đang dùng; không đổi để né lỗi key. |
| `GOOGLE_CLIENT_ID` | Tùy chọn | Google OAuth **Web** client ID cho backend xác minh ID token. Không phải client secret. |
| `MAIL_FROM` | Tùy chọn | Email người gửi khi cấu hình quên mật khẩu. |
| `SMTP_HOST`, `SMTP_PORT` | Tùy chọn, port `587` | Máy chủ SMTP hỗ trợ STARTTLS; Gmail dùng `smtp.gmail.com`. |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Tùy chọn | Tài khoản gửi thư; Gmail dùng mật khẩu ứng dụng, không phải mật khẩu đăng nhập. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Tùy chọn, để trống | Ghi đè đích database của backend khi có MySQL tập trung đã chuẩn bị. Xem mục 6; local không cần điền. |

Tạo JWT secret trên máy, rồi dán vào `JWT_SECRET` trong `.env` và không chia sẻ output:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtBytes)
$jwtRandom.Dispose()
[Convert]::ToBase64String($jwtBytes)
```

Đây là thao tác tạo cấu hình **mới**; không thay JWT secret hiện có khi chỉ pull code/restart. Giá trị `.env` có `$` hoặc `#` cần đặt trong dấu nháy đơn để Compose đọc nguyên văn. Biến cùng tên đã đặt trong PowerShell có thể ghi đè `.env`; tránh dùng cấu hình từ project khác. Không dùng `docker compose config` không có `-q` khi chia sẻ output vì lệnh đó có thể in secret.

## 4. Chạy backend bằng Docker

Chạy tại gốc repo:

```powershell
docker compose config -q
docker compose up --build -d
docker compose ps
docker compose logs --tail 100 backend
```

`config -q` kiểm cú pháp/biến bắt buộc. `up --build -d` build image và chạy nền. `ps` xem container/port; MySQL cần `healthy`. Log backend cần có khởi động thành công, Hikari kết nối và không lỗi Hibernate validate. Build image, container chạy và API thành công là các kiểm tra khác nhau.

Kiểm tra endpoint **có thật**; chưa đăng nhập thì `/auth/me` phải trả **401**:

```powershell
$backendPort = 8081 # Đổi 8082 nếu .env của bạn đang dùng 8082.
try {
    $response = Invoke-WebRequest -UseBasicParsing "http://localhost:$backendPort/api/v1/auth/me"
    [int]$response.StatusCode
} catch {
    if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode }
    else { throw }
}
```

`/` không phải màn hình app; web admin ở `/admin/index.html`, cần tài khoản ADMIN được chuẩn bị riêng, không có mật khẩu mặc định.

Các lệnh vận hành từ gốc repo:

```powershell
# Xem lỗi backend/MySQL, Ctrl+C chỉ dừng xem log.
docker compose logs -f --tail 100 backend mysql
# Restart riêng backend khi KHÔNG đổi cấu hình.
docker compose restart backend
# Đổi .env: tạo lại riêng backend để nhận biến mới.
docker compose up -d --no-deps backend
# Sau khi pull source backend mới: build lại và cập nhật backend.
docker compose up --build -d --no-deps backend
# Dừng/chạy lại cả hai service, giữ dữ liệu.
docker compose stop
docker compose start
```

Lệnh `--no-deps` dùng khi MySQL hiện đã chạy/healthy. Lần chạy đầu dùng `up --build -d` cho cả hai service. Không dùng `down -v` hoặc xóa volume để sửa lỗi cấu hình. Không tự đổi `COMPOSE_PROJECT_NAME` để “tìm lại” dữ liệu vì tên project khác tạo volume khác.

## 5. Port, Android và địa chỉ truy cập

| Nơi gọi | Backend trên host 8081 | Backend trên host 8082 |
| --- | --- | --- |
| API từ máy chạy Docker | `http://localhost:8081/api/v1/` | `http://localhost:8082/api/v1/` |
| Android Emulator trên cùng máy | `http://10.0.2.2:8081/` | `http://10.0.2.2:8082/` |
| Web admin từ host | `http://localhost:8081/admin/index.html` | `http://localhost:8082/admin/index.html` |

Nếu 8081 bị chiếm, sửa riêng `BACKEND_HOST_PORT=8082` trong `.env`, sau đó chạy `docker compose up -d --no-deps backend`. Spring Boot và container **vẫn dùng 8081**. Không dừng tiến trình/container không liên quan.

Android hiện đọc `speakmate.api.baseUrl` từ Gradle property hoặc `frontend/local.properties`. Tại file local đã có `sdk.dir`, chỉ thêm/sửa đúng dòng:

```properties
speakmate.api.baseUrl=http://10.0.2.2:8081/
```

Nếu Docker host dùng 8082 thì đổi dòng trên sang `http://10.0.2.2:8082/`. Sync/build lại Android; không thêm `api/v1/` vào base URL vì `ApiClient` tự nối phần này. Có thể build debug với URL một lần từ **gốc repo**:

```powershell
Push-Location frontend
.\gradlew.bat :app:assembleDebug '-Pspeakmate.api.baseUrl=http://10.0.2.2:8082/'
Pop-Location
```

Điện thoại thật dùng IP LAN của máy chạy backend và đúng port host; hai máy cùng mạng, firewall chỉ mở cho nguồn cần thiết. `localhost` trên điện thoại là chính điện thoại. `10.0.2.2` chỉ tới host của emulator đó, **không tới máy BE khác**. Khi dùng backend demo chung, mọi Android phải dùng URL backend chung được nhóm xác nhận. HTTP dùng cho debug trong LAN; build release hiện yêu cầu HTTPS.

## 6. Database local và demo dùng chung

### Local development — mỗi máy có dữ liệu riêng

Compose tạo MySQL 8.4, database **speakmate**, tài khoản ứng dụng và named volume `<COMPOSE_PROJECT_NAME>_mysql_data`. Backend dùng hostname `mysql:3306`, không dùng `localhost` bên trong container. Healthcheck đợi schema sẵn sàng; Hibernate dùng `ddl-auto=validate`, không tự tạo/sửa bảng.

Lần đầu **volume rỗng**, MySQL chạy hai file mount chỉ đọc, theo thứ tự:

1. [speakmate.sql](speakmate.sql): schema v3, 7 bảng, tên database cố định `speakmate`.
2. [V1__topics.sql](Docs/seeds/V1__topics.sql): thêm 5 chủ đề, không tạo tài khoản/mật khẩu mẫu.

Không cần import thủ công khi cài mới bằng Compose. Restart/recreate không import lại. Không chạy script cài mới lên database có dữ liệu; khi đổi schema phải xem phiên bản, sao lưu và dùng migration phù hợp. [Migration auth v3](Docs/migrations/V3__account_auth.sql) chỉ dành cho schema v2 tương thích, không phải script sửa mọi database cũ.

Mỗi máy có volume riêng. GitHub, việc pull source và Docker volume **không đồng bộ dữ liệu giữa thành viên**. MySQL host chỉ lắng nghe `127.0.0.1:3307` mặc định, không mở công khai. Đổi mật khẩu `.env` không tự đổi mật khẩu đã lưu trong volume; khi 1045 cần khớp lại thông tin, không xóa dữ liệu.

### Demo chung — cùng một backend và database

**Chưa tìm thấy cấu hình/địa chỉ máy chủ chung đã được xác nhận trong repo hoặc cấu hình đang chạy.** Docker local đang hoạt động không có nghĩa các thành viên đã dùng chung database.

Cách đơn giản cho buổi demo LAN: nhóm chọn **một máy** chạy bộ Compose đã có, giữ volume trên máy đó; mọi Android gọi IP LAN/hostname và port backend của máy đó. MySQL vẫn ở network Docker, không cần mở port MySQL cho điện thoại hoặc máy FE.

Trước khi coi demo chung sẵn sàng, nhóm cần:

1. Chọn máy/người quản lý backend và volume, địa chỉ LAN/hostname ổn định, port và mạng dùng khi bảo vệ.
2. Cấp quyền firewall cho backend từ thiết bị demo; dùng HTTPS nếu triển khai ngoài mạng phát triển.
3. Cấu hình Gemini/JWT và dịch vụ cần demo trên máy đó; chuẩn bị tài khoản USER/ADMIN, chủ đề và kế hoạch sao lưu.
4. Đổi base URL Android của **cả nhóm**, build/cài lại và kiểm chứng cùng dữ liệu từ các thiết bị.

Nếu dùng MySQL tập trung có sẵn ở nơi khác, quản trị viên phải cấp URL JDBC, tài khoản ứng dụng, mật khẩu, schema v3 phù hợp, quyền truy cập từ backend và yêu cầu TLS/chứng chỉ. Điền riêng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` trong `.env`. Chúng ánh xạ đúng các biến backend đã hỗ trợ; để trống giữ MySQL Compose. Không dùng cấu hình `useSSL=false` của network Docker làm mẫu bảo mật cho MySQL qua Internet.

Các override chỉ đổi đích kết nối backend, **không tạo/import database từ xa**. Compose này vẫn có service MySQL local; nó không tự triển khai hoặc đồng bộ MySQL tập trung. Chưa có máy chủ được cấp nên kết nối remote **chưa kiểm chứng**. Không mở MySQL công khai và không thay URL của database đang có dữ liệu để thử tùy tiện.

## 7. Gemini và kiểm tra request thật

`GEMINI_API_KEY` trong `.env` được Compose truyền vào container; `application.properties` ánh xạ tới **`app.ai.api-key`**, `AiProperties` cấp cho `GeminiAiClient`. Key chỉ ở backend, không nhúng Android hoặc source. Nếu key đã có trong file local trên máy, giữ nguyên giá trị đó khi cấu hình Docker; không chép cả file local vào image.

- Key trống: backend vẫn khởi động; thao tác AI trả `503 AI_NOT_CONFIGURED`.
- Key/quyền/quota hoặc provider lỗi có thể cùng được backend ánh xạ thành `502 AI_UNAVAILABLE`; chỉ mã 502 chưa phân biệt được nguyên nhân upstream. Google HTTP 503 là tình trạng khác với `AI_NOT_CONFIGURED`.
- Chờ quá thời hạn AI: `504 AI_TIMEOUT`. Không tạo lại requestId tùy tiện; tải lại bài và dùng cơ chế retry hiện có để tránh trùng USER.

Sau cấu hình, chạy `docker compose up -d --no-deps backend`, xem log backend. Để xác minh AI thật: đăng ký/đăng nhập, lấy topic, tạo bài rồi gửi **một** tin bằng API hiện có; phải nhận `aiMessage.content`, đọc lại hội thoại và đối chiếu dữ liệu. [README Docker](README-Docker.md#kiểm-tra-một-tin-nhắn-ai-thật) có block PowerShell đầy đủ, không in token/password/key. Backend khởi động hoặc test mock PASS chưa chứng minh Gemini thật.

Google/SMTP là cấu hình riêng, tùy chọn khi chạy luồng email/password và chat. Cách tạo OAuth Web/Android client và SMTP ở [luồng tài khoản](Docs/SpeakMate-luong-tai-khoan.md). Bản FE hiện tại còn mục chưa nối/kiểm lại; đọc [audit UI](Docs/Contract/14-android-ui-integration.md) trước khi chọn kịch bản demo.

## 8. Lỗi thường gặp

| Triệu chứng | Kiểm tra / cách xử lý |
| --- | --- |
| Không kết nối Docker Engine | Mở Docker Desktop, đợi Engine sẵn sàng, chọn Linux containers; chạy `docker version` và `docker compose version`. |
| Port 8081/3307 bị chiếm | Xem `docker compose ps`, `Get-NetTCPConnection -LocalPort 8081`; đổi đúng `BACKEND_HOST_PORT`/`MYSQL_HOST_PORT` trong `.env`, giữ dữ liệu và tiến trình khác. |
| Compose báo thiếu biến | Điền các mục bắt buộc trong `.env` tại **gốc repo**; không chép mẫu đè lên file đã có. Kiểm `docker compose config -q`. |
| MySQL chưa healthy | Xem `docker compose logs --tail 100 mysql`, chờ lần init đầu. Nếu SQL/schema không khớp, ghi lỗi và xác định phiên bản; không xóa volume để né lỗi. |
| MySQL 1045 | Thông tin ứng dụng phải khớp tài khoản đã tạo trong volume; chỉnh `.env` không tự đổi mật khẩu DB. Không thử đoán mật khẩu. |
| Backend không kết nối database | Local Docker dùng `mysql:3306`; kiểm MySQL healthy, DB_* override và tài khoản. Với remote kiểm DNS/network/TLS/quyền/schema cùng quản trị viên. |
| Hibernate validate thất bại | Xác định schema hiện có, so với Entity/SQL; không chuyển `ddl-auto` sang create/update để lách lỗi hoặc chạy cài mới lên DB cũ. |
| Gemini 503 AI_NOT_CONFIGURED | Điền key backend, tạo lại backend; `restart` đơn thuần không nhận `.env` mới. |
| Gemini 502/504 | Kiểm đúng key/model, quyền/quota trong Google AI Studio, kết nối và tình trạng provider; tải lại hội thoại trước khi thử lại. Không đổi provider/model hoặc tạo key mới tùy tiện. |
| Emulator không gọi được backend | Dùng `10.0.2.2` và **port host**, build lại base URL, không thêm `api/v1/`; kiểm backend bằng HTTP trên host trước. Với backend ở máy khác dùng LAN/hostname của máy đó. |
| Dữ liệu “biến mất” sau đổi cấu hình | Kiểm project name, volume, URL/backend/port thực tế. Không tạo lại volume hoặc import schema trước khi xác định đang truy cập DB nào. |

## 9. Chạy backend không dùng Docker (tùy chọn)

Cần **JDK 21**, MySQL phù hợp schema, mạng tải dependency. Không cần cài Gradle riêng. Từ gốc repo, với **database mới riêng**, khởi tạo schema/seed đã đọc ở mục 6 rồi tạo local config:

```powershell
Set-Location backend
if (-not (Test-Path application-local.properties)) {
    Copy-Item application-local.properties.example application-local.properties
}
notepad application-local.properties
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' # Sửa theo vị trí JDK 21 thực tế.
.\gradlew.bat test bootJar '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-21'
.\gradlew.bat bootRun '-Dorg.gradle.java.home=C:\Program Files\Java\jdk-21'
```

Điền `spring.datasource.url/username/password`, `app.auth.jwt-secret`, `app.ai.api-key` nếu dùng AI. Chạy từ **backend** để `spring.config.import=optional:file:./application-local.properties` tìm đúng file; không cần profile đặc biệt. Mặc định port 8081 (`PORT` có thể ghi đè); không chạy đồng thời backend local và Docker trên cùng port.

`test` chạy unit/mock, không gọi dịch vụ AI thật; `mysqlTest` là task riêng cho MySQL thử dùng một lần, xem [README backend](backend/README.md). Không chạy test ghi/xóa vào DB demo/production.

## 10. Tài liệu và kiểm tra trước khi tự push

- [README Docker: kiểm tra API thật và vận hành](README-Docker.md)
- [Bàn giao Docker, kết quả kiểm tra và phạm vi commit](Docs/Contract/15-docker-va-ban-giao-nhom.md)
- [Hợp đồng API](Docs/Contract/03-hop-dong-api.md), [OpenAPI](Docs/Contract/openapi.json), [JSON mẫu](Docs/Contract/examples.json)
- [Bản đồ code](Docs/Contract/04-kien-truc-va-ban-do-code.md), [vòng đời AI](Docs/SpeakMate-vong-doi-hoi-thoai-AI.md), [thiết kế CSDL](Docs/SpeakMate-thiet-ke-co-so-du-lieu.md)
- [Toàn bộ tài liệu](Docs/README.md)

Trước commit, xem cả tracked diff **và file untracked**, kiểm đủ backend/Docker/Docs thay vì chỉ thêm Compose. Không stage `.env`, file local, `.recovery`, backup/ZIP, cache/build/log. Không dùng `git add .` thay cho review từng nhóm. Công cụ hỗ trợ không tự commit, push hoặc đổi branch trong đợt cleanup này.
