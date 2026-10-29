package vn.tailinh.speakmate.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import vn.tailinh.speakmate.exception.BusinessException;

public record PageResponse<T>(
    Meta meta,
    List<T> result) {

    public record Meta(
        int page,
        int pageSize,
        int pages,
        long total) {
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                new Meta(page.getNumber() + 1, page.getSize(), page.getTotalPages(), page.getTotalElements()),
                page.getContent());
    }

    // API đánh số trang từ 1; Spring Data đánh số từ 0.
    public static PageRequest request(int page, int size, Sort sort) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "page >= 1; size từ 1 đến 100");
        }
        return PageRequest.of(page - 1, size, sort);
    }
}
