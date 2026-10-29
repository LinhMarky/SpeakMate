-- Optional content seed for MySQL schema v3. No accounts/passwords, no schema changes.
-- Run after speakmate.sql, against the intended database. Re-running preserves admin edits.
SET NAMES utf8mb4;
SET SESSION time_zone = '+00:00';
START TRANSACTION;
INSERT INTO topics(name, description, level, scenario)
SELECT 'Nhận phòng khách sạn', 'Bạn là khách đến nhận phòng. Mục tiêu: nhận phòng đã đặt; hỏi giờ ăn sáng; hỏi gửi hành lý sau trả phòng.', 'A1',
       'You are a friendly hotel receptionist. Help the guest check in, ask about breakfast and leave luggage. Use fictional booking details. The learner speaks first.'
WHERE NOT EXISTS (SELECT 1 FROM topics WHERE name = 'Nhận phòng khách sạn');
INSERT INTO topics(name, description, level, scenario)
SELECT 'Gọi món tại quán cà phê', 'Bạn đang gọi đồ uống. Mục tiêu: chọn món và kích cỡ; nói một yêu cầu riêng như ít đường; hỏi giá hoặc hóa đơn.', 'A1',
       'You are a friendly barista. Help the customer order a drink and size, express a preference such as less sugar, and ask about the price or receipt. Offer natural opportunities without quizzing or listing goals. The learner speaks first.'
WHERE NOT EXISTS (SELECT 1 FROM topics WHERE name = 'Gọi món tại quán cà phê');
INSERT INTO topics(name, description, level, scenario)
SELECT 'Giới thiệu ở lớp học', 'Bạn làm quen một bạn học mới. Mục tiêu: giới thiệu bản thân bằng thông tin giả định; trao đổi sở thích; hỏi một câu để tiếp tục trò chuyện.', 'A2',
       'You are a new classmate. Get to know the learner through a relaxed conversation about interests and studying. The learner speaks first.'
WHERE NOT EXISTS (SELECT 1 FROM topics WHERE name = 'Giới thiệu ở lớp học');
INSERT INTO topics(name, description, level, scenario)
SELECT 'Lên kế hoạch du lịch', 'Bạn cùng một người bạn lên kế hoạch đi cuối tuần. Mục tiêu: đề xuất địa điểm; giải thích lựa chọn; thống nhất lịch trình và ngân sách dự kiến.', 'B1',
       'You are a travel companion planning a weekend trip with the learner. Discuss a realistic itinerary, budget and activities. The learner speaks first.'
WHERE NOT EXISTS (SELECT 1 FROM topics WHERE name = 'Lên kế hoạch du lịch');
INSERT INTO topics(name, description, level, scenario)
SELECT 'Thảo luận cách học hiệu quả', 'Bạn trao đổi về cách học hiệu quả. Mục tiêu: nêu quan điểm; đưa lý do hoặc ví dụ; phản hồi lịch sự khi người đối thoại có ý kiến khác.', 'B2',
       'You are a fellow student discussing effective study habits. Exchange opinions and reasons, compare options and ask natural follow-up questions. The learner speaks first.'
WHERE NOT EXISTS (SELECT 1 FROM topics WHERE name = 'Thảo luận cách học hiệu quả');
COMMIT;
