package vn.tailinh.speakmate.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.Clock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class AuthRateLimitFilterTest {

    @Test
    void blocksExcessRequestsAndAllowsAgainAfterWindowExpires() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1000L);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(new ObjectMapper(), clock);
        for (int i = 0; i < 30; i++) {
            assertThat(filter.allow("192.0.2.1")).isTrue();
        }
        assertThat(filter.allow("192.0.2.1")).isFalse();
        assertThat(filter.allow("192.0.2.2")).isTrue();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        when(clock.millis()).thenReturn(61000L);
        assertThat(filter.allow("192.0.2.1")).isTrue();
    }
}
