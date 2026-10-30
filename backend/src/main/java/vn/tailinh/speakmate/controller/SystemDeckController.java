package vn.tailinh.speakmate.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.tailinh.speakmate.dto.response.*;
import vn.tailinh.speakmate.dto.response.word.SavedWordResponse;
import vn.tailinh.speakmate.service.SystemDeckService;

@RestController
@RequestMapping("/api/v1/system-decks")
@RequiredArgsConstructor
public class SystemDeckController {
    private final SystemDeckService decks;

    @GetMapping
    public RestResponse<PageResponse<SystemDeckResponse>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return RestResponse.success(200, "Bộ thẻ đang phát hành", decks.list(page, size, false));
    }

    @PostMapping("/{id}/save")
    public RestResponse<List<SavedWordResponse>> save(@PathVariable long id) {
        return RestResponse.success(200, "Đã lưu từ vào tài khoản", decks.saveForLearner(id));
    }
}
