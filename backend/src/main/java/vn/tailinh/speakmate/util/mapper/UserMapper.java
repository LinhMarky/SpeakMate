package vn.tailinh.speakmate.util.mapper;

import vn.tailinh.speakmate.dto.response.auth.UserResponse;
import vn.tailinh.speakmate.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setLevel(user.getLevel());
        response.setRole(user.getRole());
        response.setActive(user.isActive());
        response.setPasswordEnabled(user.getPassword() != null);
        response.setGoogleLinked(user.getGoogleSubject() != null);
        return response;
    }
}
