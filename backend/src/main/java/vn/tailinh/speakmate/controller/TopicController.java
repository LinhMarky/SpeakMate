package vn.tailinh.speakmate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.tailinh.speakmate.dto.response.*;
import vn.tailinh.speakmate.dto.response.topic.TopicResponse;
import vn.tailinh.speakmate.service.TopicService;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topics;

    @GetMapping
    public RestResponse<PageResponse<TopicResponse>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return RestResponse.success(200, "Danh sách chủ đề", topics.list(page, size));
    }
}
