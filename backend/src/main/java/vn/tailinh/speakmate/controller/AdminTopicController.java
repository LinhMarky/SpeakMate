package vn.tailinh.speakmate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tailinh.speakmate.dto.request.topic.*;
import vn.tailinh.speakmate.dto.response.*;
import vn.tailinh.speakmate.dto.response.topic.AdminTopicResponse;
import vn.tailinh.speakmate.service.TopicService;

@RestController
@RequestMapping("/api/v1/admin/topics")
@RequiredArgsConstructor
public class AdminTopicController {

    private final TopicService topics;

    @GetMapping
    public RestResponse<PageResponse<AdminTopicResponse>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return RestResponse.success(200, "Quản lý chủ đề", topics.listAdmin(page, size));
    }

    @PostMapping
    public ResponseEntity<RestResponse<AdminTopicResponse>> create(@Valid @RequestBody TopicRequest request) {
        return ResponseEntity.status(201).body(RestResponse.success(201, "Đã tạo chủ đề", topics.create(request)));
    }

    @PutMapping("/{id}")
    public RestResponse<AdminTopicResponse> update(@PathVariable long id, @Valid @RequestBody TopicRequest request) {
        return RestResponse.success(200, "Đã cập nhật chủ đề", topics.update(id, request));
    }

    @PatchMapping("/{id}/active")
    public RestResponse<AdminTopicResponse> active(@PathVariable long id,
            @Valid @RequestBody TopicActiveRequest request) {
        return RestResponse.success(200, "Đã cập nhật trạng thái", topics.setActive(id, request.active()));
    }
}
