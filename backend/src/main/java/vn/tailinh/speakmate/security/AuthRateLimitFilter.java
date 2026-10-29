package vn.tailinh.speakmate.security;

import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tailinh.speakmate.dto.response.RestResponse;

// Giới hạn theo IP trong một tiến trình; chạy nhiều instance cần bộ đếm dùng chung.
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    private final Clock clock;

    private final Map<String, Window> windows = new HashMap<>();

    public AuthRateLimitFilter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getServletPath().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (allow(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), RestResponse.failure(429, "TOO_MANY_REQUESTS",
                "Bạn thao tác quá nhanh. Vui lòng thử lại sau một phút."));
    }

    synchronized boolean allow(String ip) {
        long now = clock.millis();
        windows.values().removeIf(window -> now - window.startedAt >= 60_000);
        Window window = windows.get(ip);
        if (window == null) {
            if (windows.size() >= 10_000) {
                return false;
            }
            window = new Window(now);
            windows.put(ip, window);
        }
        return ++window.count <= 30;
    }

    private static class Window {

        final long startedAt;

        int count;

        Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
