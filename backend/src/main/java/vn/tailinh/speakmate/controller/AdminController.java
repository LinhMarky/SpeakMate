package vn.tailinh.speakmate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tailinh.speakmate.dto.request.AdminRequests;
import vn.tailinh.speakmate.dto.response.*;
import vn.tailinh.speakmate.dto.response.auth.UserResponse;
import vn.tailinh.speakmate.service.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService admin;
    private final SystemDeckService decks;

    @GetMapping("/statistics")
    public RestResponse<AdminService.Statistics> statistics() {
        return RestResponse.success(200, "Thống kê hệ thống", admin.statistics());
    }

    @GetMapping("/users")
    public RestResponse<PageResponse<UserResponse>> users(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean active) {
        return RestResponse.success(200, "Danh sách tài khoản", admin.list(page, size, search, active));
    }

    @PatchMapping("/users/{id}/active")
    public RestResponse<UserResponse> activeUser(@PathVariable long id, @Valid @RequestBody AdminRequests.Active request) {
        return RestResponse.success(200, "Đã cập nhật tài khoản", admin.setActive(id, request.active()));
    }

    @PutMapping("/password")
    public RestResponse<Void> password(@Valid @RequestBody AdminRequests.Password request) {
        admin.changePassword(request);
        return RestResponse.success(200, "Đã đổi mật khẩu. Hãy đăng nhập lại.", null);
    }

    @GetMapping("/system-decks")
    public RestResponse<PageResponse<SystemDeckResponse>> listDecks(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return RestResponse.success(200, "Bộ thẻ hệ thống", decks.list(page, size, true));
    }

    @PostMapping("/system-decks")
    public ResponseEntity<RestResponse<SystemDeckResponse>> create(@Valid @RequestBody AdminRequests.Deck request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RestResponse.success(201, "Đã tạo bộ thẻ", decks.save(0, request)));
    }

    @PutMapping("/system-decks/{id}")
    public RestResponse<SystemDeckResponse> update(@PathVariable long id, @Valid @RequestBody AdminRequests.Deck request) {
        return RestResponse.success(200, "Đã sửa bộ thẻ", decks.save(id, request));
    }

    @PatchMapping("/system-decks/{id}/active")
    public RestResponse<SystemDeckResponse> activeDeck(@PathVariable long id, @Valid @RequestBody AdminRequests.Active request) {
        return RestResponse.success(200, "Đã cập nhật bộ thẻ", decks.setActive(id, request.active()));
    }
}
