package vn.tailinh.speakmate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tailinh.speakmate.dto.request.conversation.*;
import vn.tailinh.speakmate.dto.response.*;
import vn.tailinh.speakmate.dto.response.conversation.*;
import vn.tailinh.speakmate.service.*;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationTransactionService transactions;

    private final ConversationService conversations;

    @PostMapping
    public ResponseEntity<RestResponse<ConversationDetail>> create(
            @Valid @RequestBody CreateConversationRequest request) {
        var result = transactions.create(request);
        int status = result.replayed() ? 200 : 201;
        return ResponseEntity.status(status).body(RestResponse.success(status, "Bài hội thoại", result.detail()));
    }

    @GetMapping
    public RestResponse<PageResponse<ConversationSummary>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return RestResponse.success(200, "Lịch sử hội thoại", transactions.list(page, size));
    }

    @GetMapping("/{id}")
    public RestResponse<ConversationDetail> get(@PathVariable long id) {
        return RestResponse.success(200, "Chi tiết hội thoại", transactions.get(id));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<RestResponse<TurnResponse>> send(@PathVariable long id,
            @Valid @RequestBody SendMessageRequest request) {
        TurnResponse result = conversations.send(id, request);
        boolean pending = result.replyStatus().equals("PROCESSING");
        var response = ResponseEntity.status(pending ? 202 : 200);
        if (pending) {
            response.header("Retry-After", "3");
        }
        return response.body(RestResponse.success(pending ? 202 : 200, "Lượt hội thoại", result));
    }
}
