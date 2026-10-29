package vn.tailinh.speakmate.service.ai;

import org.springframework.http.HttpStatus;
import vn.tailinh.speakmate.exception.BusinessException;

public final class AiFailure {

    private AiFailure() {
    }

    public static BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE",
                "AI trả dữ liệu không hợp lệ. Bạn có thể thử lại");
    }

    public static BusinessException unavailable() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "AI_UNAVAILABLE",
                "Dịch vụ AI đang bận hoặc cấu hình chưa hợp lệ. Hãy thử lại sau");
    }

    public static BusinessException timeout() {
        return new BusinessException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT",
                "AI phản hồi quá chậm. Nội dung bài vẫn được giữ");
    }
}
