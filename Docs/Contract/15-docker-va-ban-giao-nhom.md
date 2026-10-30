# 15 — Docker, dữ liệu demo và kiểm tra trước push

Cập nhật **30/10/2026**, bắt đầu trên `dev` tại `7848017b411c0c57731035882666141eea38de07`. Trong lúc kiểm cuối, Git đã có commit mới `c56d1f3` (`feat: integrate SpeakMate API and add Docker setup`), cũng là ref `origin/dev` lúc đọc; đây là thay đổi bên ngoài các lệnh của tác vụ cleanup. Tác vụ này không chạy pull, đổi branch, commit hoặc push và giữ nguyên trạng thái mới. Thành viên mới làm theo **[README chính](../../README.md)**; [README Docker](../../README-Docker.md) có block PowerShell kiểm một lời đáp AI.

## 1. Kết quả đã có và giới hạn

| Kiểm tra | Kết quả / phạm vi |
| --- | --- |
| Dockerfile | Java 21, Wrapper 8.8, multi-stage; build stage chạy unit/mock và bootJar; runtime user không phải root. `docker compose up --build -d --no-deps backend` PASS trong cleanup; bước `test bootJar` dùng cache từ build đã đạt, không phải chạy mới bộ test. |
| Compose mặc định / đổi port | `config -q` PASS với host 8081 và 8082; Spring/container vẫn 8081. Không chiếm thử port 8081 đang có dịch vụ khác. |
| `.env.example` | Kiểm config với ba biến bắt buộc được cấp riêng trong process; mặc định dùng MySQL Compose, key AI trống. Không ghi secret vào mẫu. |
| DB override | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` đã đối chiếu với code và kiểm interpolation. Chỉ kiểm cấu hình, **không kết nối máy chủ remote**. |
| Runtime Docker hiện tại | Backend ở host **8082**; MySQL healthy, hostname nội bộ `mysql:3306`. Cấu hình DB/JWT/Gemini khớp container đang chạy. |
| MySQL thật | SQL chỉ đọc xác nhận 7 bảng, 5 chủ đề, giữ nguyên hai tài khoản/hai bài/ba tin của đợt thử Docker. |
| HTTP thật | `/api/v1/auth/me` chưa có token = 401; `/admin/index.html` = 200. Đợt trước đã register 201/login 200/topics 200/create 201. |
| Gemini thật | Đợt sửa key ngay trước cleanup: một POST gửi tin = **200**, 12,59 giây. Lời đáp khớp khi đọc lại API và SQL (`conversationId=2`). Không gọi thêm Gemini trong đợt chỉ sửa tài liệu/config. |
| Android hiện tại | Không sửa FE hoặc base URL local trong cleanup. FE mặc định 8081 không tự chuyển sang Docker 8082; xem README để cấu hình/build đúng URL. Chưa kiểm lại trọn vòng học sau pull. |
| Database/backend chung | **Chưa có địa chỉ/đơn vị quản lý được xác nhận**, chưa kiểm nhiều thiết bị dùng cùng dữ liệu. |
| Tài liệu/Git | Parse 18 block PowerShell không lỗi; 95 liên kết file tồn tại; `git diff --check` và kiểm staged PASS. Quét 336 file không thấy giá trị secret local hoặc mẫu key/private key đã kiểm; không có file cấu hình riêng được track. |

Một lời đáp thật không xác nhận review JSON, lưu từ, flashcard, lịch sử qua UI, Google/email hoặc mic/TTS. Kết quả Android PASS trong các báo cáo cũ thuộc mốc FE khác; file 14 mô tả phần nối sau pull. Không trình bày toàn bộ demo đã nghiệm thu chỉ vì image/API chat hoạt động.

Bằng chứng local bị ignore: `backend/build/docker-validation/gemini-key-result.json`, `smoke-result.json`, `compose-up.log`; `backend/build/prepush-validation/docker-result.json` ghi kiểm tra cấu hình, runtime và SQL mới. Đây là artifact trên máy tác giả, không phải file được yêu cầu push hoặc đường dẫn chắc chắn có trên máy clone mới.

## 2. Database dùng chung cần chốt gì?

Mỗi người chạy Compose riêng thì có MySQL/volume riêng; Git không đồng bộ dữ liệu. Không tìm thấy backend hoặc MySQL tập trung trong cấu hình hiện đang dùng.

Lựa chọn đơn giản cho demo: chọn một máy chạy bộ Compose hiện tại, mọi Android dùng IP LAN/hostname và port backend của máy đó. Database vẫn chỉ được backend truy cập trong Docker; port MySQL host hiện bind `127.0.0.1`, không cần mở cho cả lớp.

Nhóm còn cần cung cấp/chốt: máy và người quản lý; địa chỉ/port backend; mạng/firewall cho thiết bị demo; cách giữ volume và sao lưu; tài khoản/chủ đề; key AI trên máy chủ; URL Android và kiểm cùng dữ liệu từ hai thiết bị. Nếu ra ngoài LAN phát triển, cần HTTPS và quyền truy cập phù hợp.

Nếu có MySQL remote riêng, quản trị viên phải cấp `DB_URL` (JDBC đúng database/TLS), `DB_USERNAME`, `DB_PASSWORD`, schema v3 đã chuẩn bị, quyền từ IP backend và chứng chỉ nếu cần. Để các biến trống thì vẫn dùng local Compose. Override không import schema, không cấp quyền và không đồng bộ dữ liệu; Compose vẫn có MySQL local. Chưa có máy chủ thật nên chỉ xác minh cấu hình override, không tuyên bố remote đã chạy.

## 3. Dọn Git và bảo toàn công việc

- Bổ sung ignore cho log/tạm/IDE/OS metadata, build bytecode và ZIP. Các file local và `.recovery` tiếp tục bị ignore.
- `frontend.zip` là archive cũ gồm 934 entry dưới `frontend/`, không phải đầu vào build/runtime. Tác vụ dùng **`git rm --cached -- frontend.zip`** để ngừng theo dõi, ban đầu giữ file local. Chỉ việc bỏ ZIP được stage riêng; source không được tác vụ tự stage. Sau commit bên ngoài, ZIP không còn ở gốc; đã phục hồi đúng blob từ mốc Git ban đầu vào `.recovery/20261029-233912/frontend-before-cleanup.zip`, checksum khớp file local ban đầu. Không ghi đè file khác hoặc hoàn tác commit mới.
- Tác vụ không xóa file local, source, stash, bản ZIP khôi phục hoặc dữ liệu. Giữ toàn bộ file cũ trong `.recovery/20261029-233912/` và các bản sao khác, thêm bản ZIP trên. Cache/log/build vẫn ở máy nhưng không đưa vào commit; không xóa bằng lệnh dọn rộng.
- Snapshot checksum FE, backend source, ba file cấu hình riêng, stash và 18 file khôi phục được giữ tại `backend/build/prepush-validation/before.json` để kiểm sau thao tác. Các bản kê/báo cáo sinh ra cũng bị ignore.

## 4. Nhóm file cần tự review và commit

Đầu đợt backend/Docs/Docker còn untracked; commit bên ngoài `c56d1f3` đã đưa chúng vào Git. Danh sách bên dưới là các nhóm cần có trong bản bàn giao; trạng thái còn phải commit phải lấy từ `git status` hiện tại. Xem file thực tế bằng `git ls-files --cached --others --exclude-standard`; xem riêng untracked bằng `git ls-files --others --exclude-standard`. Không chỉ nhìn `git diff`.

| Nhóm | File cần review |
| --- | --- |
| Quy tắc chung | `.gitignore`, `.editorconfig`, `AGENTS.md` |
| Docker và cấu hình mẫu | `compose.yaml`, `.env.example`, `backend/Dockerfile`, `backend/.dockerignore`, `backend/.gitignore` |
| Build backend | `backend/build.gradle.kts`, `backend/settings.gradle.kts`, `backend/gradlew`, `backend/gradlew.bat`, `backend/gradle/wrapper/gradle-wrapper.jar`, `backend/gradle/wrapper/gradle-wrapper.properties` |
| Backend source | Toàn bộ `backend/src/main` và `backend/src/test`, bao gồm resource AI/static admin; không lấy từ build/bin |
| Local mẫu/test tooling | `backend/application-local.properties.example`, `backend/scripts/Test-MySql.ps1` |
| Database | `speakmate.sql`, `Docs/seeds/V1__topics.sql`, `Docs/migrations/V3__account_auth.sql` — giữ đúng giới hạn cài mới/migration |
| Hướng dẫn/contract | `README.md`, `README-Docker.md`, `backend/README.md`, tài liệu hiện có trong `Docs/`, OpenAPI/JSON mẫu/script kiểm schema |
| FE được bảo toàn | Review các file modified và untracked bên `frontend` trong `git status`, gồm api/auth/ui helper, instrumented tests, values-v27/v29 và các thay đổi giao diện đã có; cleanup không chỉnh chúng |
| Bỏ artifact đã track | `frontend.zip` đã bị bỏ khỏi repository trong commit bên ngoài; bản khôi phục chính xác nằm trong `.recovery` bị ignore |

Không đưa `.env`, `application-local.properties`, `frontend/local.properties`, key/token/password, `.recovery`, ZIP, cache/build/bin/log vào commit. Không có tài khoản admin/mật khẩu mặc định được chèn vào seed hoặc README.

Kiểm cuối phát hiện thêm `database/init/02-topics.sql` untracked, trùng byte với seed chuẩn và chưa được Compose tham chiếu. Giữ nguyên vì file xuất hiện trong lúc làm; bộ Docker dùng `Docs/seeds/V1__topics.sql`, không cần thêm bản trùng để chạy. Review riêng file mới này và `AGENTS.md`; không tự stage hoặc xóa công việc vừa xuất hiện.

Sau commit bên ngoài, diff còn lại của cleanup nằm ở `README.md`, `.env.example` và chính file bàn giao này. Không có file được stage ở lần kiểm cuối. Bản kê chi tiết nằm trong `backend/build/prepush-validation/commit-review-files.txt`; FE, 109 file backend source, ba file cấu hình riêng và 18 file khôi phục ban đầu đều giữ nguyên checksum, stash giữ nguyên.

## 5. Tự kiểm tra trước khi push

Tại gốc repo:

```powershell
git status --short --branch
git diff --check
git diff --stat
git diff
git diff --cached
git ls-files --others --exclude-standard
git ls-files -- .env backend/application-local.properties frontend/local.properties
docker compose config -q
docker compose ps
```

Lệnh kiểm ba file local phải không có kết quả. Rà từng nhóm trước khi tự stage, xem lại `git diff --cached --check` và `git diff --cached` sau khi stage. Chỉ push khi đã đủ backend/Wrapper/SQL/seed/Docs và không có artifact riêng tư. Không cần đưa toàn bộ `build` lên để chứng minh test chạy.

Phần còn phải làm trước **buổi demo**, khác với việc chuẩn bị push: chọn backend chung, cấu hình URL của thiết bị, kiểm lại vòng học đầy đủ trên FE hiện tại và nghiệm thu những dịch vụ/âm thanh sẽ trình bày. Đợt cleanup này không mở rộng chức năng hoặc tự triển khai server Internet.
