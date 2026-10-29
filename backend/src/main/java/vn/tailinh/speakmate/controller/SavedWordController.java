package vn.tailinh.speakmate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.tailinh.speakmate.dto.request.word.*;
import vn.tailinh.speakmate.dto.response.*;
import vn.tailinh.speakmate.dto.response.word.*;
import vn.tailinh.speakmate.service.SavedWordService;
import vn.tailinh.speakmate.util.constant.SavedWordStatus;

@RestController
@RequestMapping("/api/v1/saved-words")
@RequiredArgsConstructor
public class SavedWordController {

    private final SavedWordService words;

    @GetMapping
    public RestResponse<PageResponse<SavedWordResponse>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) SavedWordStatus status) {
        return RestResponse.success(200, "Từ của tôi", words.list(page, size, status));
    }

    @PostMapping("/from-suggestions")
    public RestResponse<SaveSuggestionsResponse> save(@Valid @RequestBody SaveSuggestionsRequest request) {
        return RestResponse.success(200, "Đã lưu các từ được chọn", words.save(request));
    }

    @PatchMapping("/{id}/review")
    public RestResponse<SavedWordResponse> review(@PathVariable long id,
            @Valid @RequestBody ReviewWordRequest request) {
        return RestResponse.success(200, "Đã ghi nhận lần ôn", words.review(id, request));
    }
}
