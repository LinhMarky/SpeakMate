package vn.tailinh.speakmate.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String jwtSecret = "";

    private String issuer = "speakmate";

    private String audience = "speakmate-android";

    private Duration accessTokenTtl = Duration.ofMinutes(15);

    private Duration refreshTokenTtl = Duration.ofDays(7);

    private String googleClientId = "";

    private Duration resetCodeTtl = Duration.ofMinutes(10);

    private Duration resetResendCooldown = Duration.ofSeconds(60);

    private int resetMaxAttempts = 5;
}
