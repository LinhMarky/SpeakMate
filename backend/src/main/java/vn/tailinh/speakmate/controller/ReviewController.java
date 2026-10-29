package vn.tailinh.speakmate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tailinh.speakmate.dto.response.RestResponse;
import vn.tailinh.speakmate.dto.response.conversation.ReviewResponse;
import vn.tailinh.speakmate.service.*;

@RestController
@RequestMapping("/api/v1/conversations/{id}")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviews;

    private final ReviewTransactionService transactions;

    @PostMapping("/finish")
    public ResponseEntity<RestResponse<ReviewResponse>> finish(@PathVariable long id) {
        return result(reviews.finish(id, false));
    }

    @GetMapping("/review")
    public RestResponse<ReviewResponse> get(@PathVariable long id) {
        return RestResponse.success(200, "Nhận xét cuối bài", transactions.get(id));
    }

    @PostMapping("/review/retry")
    public ResponseEntity<RestResponse<ReviewResponse>> retry(@PathVariable long id) {
        return result(reviews.finish(id, true));
    }

    private ResponseEntity<RestResponse<ReviewResponse>> result(ReviewResponse result) {
        boolean pending = result.pollAfterSeconds() > 0;
        var builder = ResponseEntity.status(pending ? 202 : 200);
        if (pending) {
            builder.header("Retry-After", "3");
        }
        return builder.body(RestResponse.success(pending ? 202 : 200, "Nhận xét cuối bài", result));
    }
}
