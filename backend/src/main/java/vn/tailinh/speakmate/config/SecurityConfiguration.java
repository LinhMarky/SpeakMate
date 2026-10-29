package vn.tailinh.speakmate.config;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import vn.tailinh.speakmate.security.*;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    SecretKey jwtKey(AuthProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.getJwtSecret());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.auth.jwt-secret must be Base64.");
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("Set app.auth.jwt-secret to at least 32 random bytes encoded as Base64.");
        }
        if (properties.getAccessTokenTtl().isNegative() || properties.getAccessTokenTtl().isZero()
                || properties.getRefreshTokenTtl().compareTo(properties.getAccessTokenTtl()) <= 0
                || properties.getResetCodeTtl().isNegative() || properties.getResetCodeTtl().isZero()
                || properties.getResetResendCooldown().isNegative() || properties.getResetMaxAttempts() < 1
                || properties.getResetMaxAttempts() > 5) {
            throw new IllegalStateException("Invalid auth lifetime or reset-attempt configuration (1..5).");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey, AuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamp.setClock(clock);
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean valid = jwt.getAudience() != null && jwt.getAudience().contains(properties.getAudience())
                    && "access".equals(jwt.getClaimAsString("token_type")) && jwt.getExpiresAt() != null
                    && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(30))
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt()) && jwt.getSubject() != null
                    && jwt.getSubject().matches("[1-9][0-9]{0,18}") && jwt.getClaim("auth_version") instanceof Number;
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult
                            .failure(new OAuth2Error("invalid_token", "Invalid access token claims", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator(properties.getIssuer()), claims));
        return decoder;
    }

    @Bean("passwordResetExecutor")
    TaskExecutor passwordResetExecutor() {
        // Gửi email ở nền với hàng đợi giới hạn để không giữ yêu cầu HTTP chờ SMTP.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("password-reset-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, DatabaseJwtAuthenticationConverter converter,
            SecurityErrorHandler errors, com.fasterxml.jackson.databind.ObjectMapper objectMapper, Clock clock)
            throws Exception {
        // JWT cho từng request; trang admin công khai nhưng API quản trị vẫn yêu cầu ADMIN.
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable()).formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/error", "/admin/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/google", "/api/v1/auth/refresh", "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset")
                        .permitAll().requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .addFilterBefore(new AuthRateLimitFilter(objectMapper, clock), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
