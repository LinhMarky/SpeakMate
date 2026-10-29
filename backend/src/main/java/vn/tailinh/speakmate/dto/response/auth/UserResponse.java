package vn.tailinh.speakmate.dto.response.auth;

import lombok.Getter;
import lombok.Setter;
import vn.tailinh.speakmate.util.constant.EnglishLevel;
import vn.tailinh.speakmate.util.constant.Role;

@Getter
@Setter
public class UserResponse {

    private Long id;

    private String name;

    private String email;

    private EnglishLevel level;

    private Role role;

    private boolean active;

    private boolean passwordEnabled;

    private boolean googleLinked;
}
