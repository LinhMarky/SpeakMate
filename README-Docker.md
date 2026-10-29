# SpeakMate — Kiểm tra và vận hành Docker

Hướng dẫn cài đặt, clone `dev`, toàn bộ biến `.env`, port, URL Android, database local/demo chung và lỗi thường gặp ở **[README chính](README.md)**. File này bổ sung cách kiểm tra API. Chạy PowerShell từ gốc `SpeakMate`, chỉ dùng tài khoản/dữ liệu thử.

## Cấu hình được container sử dụng

| Nguồn .env | Container / property Spring |
| --- | --- |
| `BACKEND_HOST_PORT` | Publish host → container 8081; `PORT=8081` → `server.port` |
| `MYSQL_USER`, `MYSQL_PASSWORD` | Mặc định `DB_USERNAME`, `DB_PASSWORD` → datasource backend |
| `MYSQL_ROOT_PASSWORD` | Chỉ vào service MySQL, không truyền cho backend |
| `DB_URL` | Ghi đè URL JDBC; để trống dùng `mysql:3306/speakmate` |
| `DB_USERNAME`, `DB_PASSWORD` | Ghi đè tài khoản ứng dụng nếu đã cấp database khác |
| `JWT_SECRET` | `app.auth.jwt-secret` |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | `app.ai.api-key`, `app.ai.model` |
| `GOOGLE_CLIENT_ID` | `app.auth.google-client-id` |
| `MAIL_FROM` | `app.mail.from` |
| `SMTP_HOST/PORT/USERNAME/PASSWORD` | `SPRING_MAIL_*` → `spring.mail.*` |

Dockerfile chỉ copy Wrapper/build/settings/source, không copy `.env`, cấu hình local, build cũ hoặc `.recovery`. Runtime Java 21 chạy bằng user `speakmate`, không phải root. Schema/seed chỉ được MySQL init trên volume rỗng. Thay DB_* không import hoặc đồng bộ database từ xa; service MySQL local vẫn tồn tại.

## Kiểm tra một tin nhắn AI thật

Cần backend/MySQL đang chạy, key hợp lệ. Block sau tạo **một tài khoản thử, một bài và một tin nhắn** qua API; mật khẩu/token chỉ giữ trong biến, không in ra. Mỗi lần chạy tạo thêm dữ liệu thử; không chạy lặp để kiểm log. Chỉ gọi Gemini một lần, không tự gửi lại khi lỗi. Đây là kiểm backend qua HTTP, không phải kiểm giao diện Android hoặc toàn bộ vòng học.

Đổi `$backendPort` cho đúng `.env` của máy. Sau đó copy toàn bộ block vào PowerShell:

```powershell
$backendPort = 8081 # Máy tác giả hiện dùng 8082.
$apiBase = "http://localhost:$backendPort/api/v1/"

function Invoke-SpeakMateApi {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = '')
    $arguments = @{
        Method = $Method
        Uri = $apiBase + $Path
        ContentType = 'application/json; charset=utf-8'
        TimeoutSec = 65
        ErrorAction = 'Stop'
    }
    if ($Token) { $arguments.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $arguments.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 6))
    }
    try {
        Invoke-RestMethod @arguments
    } catch {
        $httpStatus = 'network/timeout'
        if ($_.Exception.Response) { $httpStatus = [int]$_.Exception.Response.StatusCode }
        throw "API $Method $Path failed: $httpStatus. Check backend logs; no automatic retry."
    }
}

$verificationId = [guid]::NewGuid().ToString('N')
$verifyEmail = "docker-check-$verificationId@example.test"
$verifyPassword = [guid]::NewGuid().ToString('N') + 'aA1!'
$accessToken = ''
try {
    Invoke-SpeakMateApi 'POST' 'auth/register' @{
        name = 'Docker Verification'; email = $verifyEmail; password = $verifyPassword
    } | Out-Null
    $login = Invoke-SpeakMateApi 'POST' 'auth/login' @{
        email = $verifyEmail; password = $verifyPassword
    }
    $accessToken = $login.data.access_token
    if (-not $accessToken) { throw 'Login returned no access token.' }
    $topics = Invoke-SpeakMateApi 'GET' 'topics?page=1&size=20' -Token $accessToken
    $topic = $topics.data.result | Select-Object -First 1
    if (-not $topic) { throw 'No topic; check MySQL init/seed.' }
    $created = Invoke-SpeakMateApi 'POST' 'conversations' @{
        topicId = $topic.id; level = 'A1'; createRequestId = [guid]::NewGuid().ToString()
    } $accessToken
    $conversationId = $created.data.id
    $messageRequestId = [guid]::NewGuid().ToString()
    $reply = Invoke-SpeakMateApi 'POST' "conversations/$conversationId/messages" @{
        content = 'Hello! I would like to practise English. Can we start with a simple question?'
        requestId = $messageRequestId
    } $accessToken
    if (-not $reply.data.aiMessage.content) {
        throw 'No completed AI reply. Reopen the conversation before considering a retry.'
    }
    $history = Invoke-SpeakMateApi 'GET' "conversations/$conversationId" -Token $accessToken
    $savedReply = @($history.data.messages | Where-Object {
        $_.sender -eq 'AI' -and $_.content -ceq $reply.data.aiMessage.content
    })
    if ($savedReply.Count -ne 1) { throw 'AI reply readback did not match.' }
    [pscustomobject]@{
        ConversationId = $conversationId
        RealAiReplyReceived = $true
        SavedReplyReadBack = $true
        MessageCount = @($history.data.messages).Count
    }
} finally {
    if ($accessToken) {
        try { Invoke-SpeakMateApi 'POST' 'auth/logout' @{} $accessToken | Out-Null }
        catch { Write-Warning 'Could not confirm logout; check backend availability.' }
    }
    Remove-Variable verifyPassword, accessToken, login -ErrorAction SilentlyContinue
}
```

Kết quả đúng: `RealAiReplyReceived=True`, `SavedReplyReadBack=True`, hai tin USER/AI và conversationId cụ thể. Đối chiếu MySQL chỉ đọc nếu cần bằng công cụ quản trị với tài khoản đã cấp; không xuất password/token ra báo cáo.

Nếu script dừng ở API gửi tin, ghi HTTP và conversationId/requestId; xem `docker compose logs --tail 100 backend`, tải lại bài trước khi thử lại. `502 AI_UNAVAILABLE` không tự phân biệt key sai/quota/provider; backend không ghi body lỗi Google hoặc key vào log. Xem key/quota của đúng project trên Google AI Studio, không chia sẻ key. Một lời đáp thành công không xác minh phần kết thúc/JSON nhận xét/từ/flashcard/âm thanh.

## Kiểm tra sau khi thay cấu hình hoặc pull code

Từ gốc repo, sau khi MySQL đang healthy:

```powershell
docker compose config -q
docker compose up --build -d --no-deps backend
docker compose ps
docker compose logs --tail 100 backend
```

Không cần gọi lại AI nếu chỉ sửa README/ignore và đã có bằng chứng trên cùng image/key. Kiểm `auth/me` chưa đăng nhập = 401 theo README chính; API có dữ liệu cần token và database thật. Khởi động lại image không tự chứng minh Google, SMTP, âm thanh hay nhiều thiết bị dùng chung đã hoạt động.

## Kiểm tra Git trước khi tự commit

```powershell
git status --short --branch
git diff --check
git diff --stat
git diff
git diff --cached
git ls-files --others --exclude-standard
git ls-files -- .env backend/application-local.properties frontend/local.properties
git check-ignore -v -- .env backend/application-local.properties frontend/local.properties .recovery/
```

`git ls-files` cho ba file local phải không có kết quả. File đã track không được bảo vệ bởi `.gitignore`; phải xử lý rõ trong diff. Danh sách nhóm file cần review ở [báo cáo bàn giao](Docs/Contract/15-docker-va-ban-giao-nhom.md), bản kê chi tiết máy tác giả ở `backend/build/prepush-validation/` (bị ignore).
