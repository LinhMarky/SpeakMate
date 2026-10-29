package vn.tailinh.speakmate.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;
import vn.tailinh.speakmate.dto.response.RestResponse;

@Slf4j
@RestControllerAdvice
public class GlobalException {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<RestResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(RestResponse.failure(exception.getStatus().value(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RestResponse<Void>> handleParameter(Exception exception) {
        return ResponseEntity.badRequest().body(RestResponse.failure(400, "VALIDATION_ERROR", "Tham số không hợp lệ"));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<RestResponse<Void>> handleAuth(AuthException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(RestResponse.failure(exception.getStatus().value(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).distinct().sorted()
                .reduce((left, right) -> left + "; " + right).orElse("Dữ liệu không hợp lệ");
        return ResponseEntity.badRequest().body(RestResponse.failure(400, "VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RestResponse<Void>> handleUnreadableJson(HttpMessageNotReadableException exception) {
        // Lỗi parse có thể chứa mật khẩu/token đã gửi, không trả hoặc ghi log nội dung lỗi gốc.
        return ResponseEntity.badRequest().body(RestResponse.failure(400, "INVALID_JSON",
                "JSON không hợp lệ, có trường không được phép hoặc sai kiểu dữ liệu"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RestResponse<Void>> handleConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(RestResponse.failure(409, "DATA_CONFLICT",
                "Dữ liệu bị trùng hoặc không phù hợp. Hãy kiểm tra và thử lại"));
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<RestResponse<Void>> handleNotFound(Exception exception) {
        return ResponseEntity.status(404)
                .body(RestResponse.failure(404, "NOT_FOUND", "Không tìm thấy đường dẫn được yêu cầu"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RestResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(405).headers(exception.getHeaders()).body(RestResponse.failure(405,
                "METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ tại đường dẫn này"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RestResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(415).body(RestResponse.failure(415, "UNSUPPORTED_MEDIA_TYPE",
                "Kiểu nội dung không được hỗ trợ; API yêu cầu JSON"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(403)
                .body(RestResponse.failure(403, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Void>> handleUnexpected(Exception exception) {
        // Chỉ log loại lỗi; thông báo, stack và payload có thể chứa thông tin bí mật.
        log.error("Unhandled request exception type={}", exception.getClass().getName());
        return ResponseEntity.internalServerError()
                .body(RestResponse.failure(500, "INTERNAL_ERROR", "Không thể xử lý yêu cầu lúc này"));
    }
}
