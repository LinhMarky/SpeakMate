# 19 — Sửa số liệu và tiến độ flashcard

Ngày 30/10/2026. Phạm vi: lật thẻ **Đã nhớ / Chưa thuộc**, Trang chủ, Cá nhân, thư viện và chi tiết bộ thẻ. Không đổi backend/schema hoặc gọi Gemini.

Nguyên nhân: Trang chủ/Cá nhân đếm bộ mẫu từ DemoFlashcardRepository; LocalDeckStore luôn trả learnedCards bằng 0. Thẻ AI có ID server lưu MASTERED/LEARNING thật nhưng danh sách bộ có tên không đọc lại trạng thái đó.

- `FlashcardLibrary`: nguồn tính chung cho số liệu thật. Bộ thủ công đọc FlashcardProgressStore; bộ liên kết từ AI đọc trạng thái mới từ `GET /saved-words`.
- `HomeFragment`, `ProfileFragment`, `FlashcardsFragment`, `DeckDetailFragment` đọc lại số liệu khi mở/quay về. Lỗi mạng hiển thị chưa tải được và cho thử lại, không giả số 0.
- `LocalDeckStore.getDecks()` đọc tiến độ thực tế thay số 0 cố định. `StudyFragment` cập nhật bản nhớ trên máy sau khi server đã xác nhận đúng ID/status; PATCH lỗi không tăng tiến độ.

**Quy tắc đếm:** tổng bộ là các bộ có tên trên máy cộng “Từ của tôi” khi có từ tài khoản. Từ cùng savedWordId nằm ở nhiều bộ chỉ tính một lần trong tổng từ. “Bộ đã thuộc” tăng khi bộ không rỗng và mọi thẻ đều Đã nhớ/MASTERED. Chọn Chưa thuộc/LEARNING không tính là đã thuộc; học lại không tạo thêm bộ hoặc cộng trùng từ.

**Kiểm chứng:** `ProgressStatisticsTest` chạy trên emulator với HTTP fixture riêng: **3 test PASS, 33,818 giây**. Kiểm chờ PATCH xác nhận, học xong cập nhật thư viện/Trang chủ/Cá nhân, học lại không tăng giả, PATCH lỗi giữ nguyên thẻ và số đếm, trạng thái MASTERED mới cập nhật các bộ liên kết. Test giữ và khôi phục dữ liệu bộ thẻ/progress/session trên máy; không ghi database thật. Bằng chứng local: `backend/build/progress-check/android-progress-fixture.log` bị Git ignore.

Bản cuối `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`: PASS, 1 unit test không lỗi. APK đã cài lại bằng `adb install -r`, trỏ backend thật 8081 và giữ dữ liệu app.

Chưa chạy lại toàn bộ E2E bằng tài khoản thật trong đợt sửa số liệu. Khi dùng bản mới, mở bộ thẻ → Đã nhớ → quay lại chi tiết/thư viện/Trang chủ để xác nhận trên dữ liệu của bạn. Backend vẫn 8081, emulator dùng `http://10.0.2.2:8081/`.
