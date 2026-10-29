package vn.tailinh.speakmate.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionTest {

    private final GlobalException handler = new GlobalException();

    @Test
    void missingRouteReturns404WithoutEchoingTheRequestedPath() {
        var response = handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "sensitive-path"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).doesNotContain("sensitive-path");
    }

    @Test
    void wrongMethodReturns405AndAdvertisesTheSupportedMethod() {
        var response = handler
                .handleMethodNotAllowed(new HttpRequestMethodNotSupportedException("GET", java.util.List.of("POST")));
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
    }
}
