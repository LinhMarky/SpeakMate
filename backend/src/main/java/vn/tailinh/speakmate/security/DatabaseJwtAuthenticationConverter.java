package vn.tailinh.speakmate.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long id;
        try {
            id = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw invalid();
        }
        User user = userRepository.findById(id).orElseThrow(this::invalid);
        Object version = jwt.getClaim("auth_version");
        if (!user.isActive() || !(version instanceof Number number) || number.longValue() != user.getAuthVersion()) {
            throw invalid();
        }
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getEmail());
    }

    private OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }
}
