package com.eRez.common.security;

import com.eRez.common.dto.response.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@Slf4j
@Component("rateLimiterFilter")
public class RateLimiterFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "rate-limit:";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final Set<String> LIMITED_METHODS = Set.of("POST", "PUT", "DELETE");
    private static final long WINDOW_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final ObjectMapper objectMapper;

    @Value("${rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    public RateLimiterFilter(StringRedisTemplate redisTemplate,
                              RedisScript<Long> rateLimitScript,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (!LIMITED_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
        if (isAdmin) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = KEY_PREFIX + auth.getName();

        Long count;
        try {
            count = redisTemplate.execute(rateLimitScript, Collections.singletonList(key));
        } catch (Exception ex) {
            log.warn("Rate limiter Redis call failed for key {}; failing open", key, ex);
            filterChain.doFilter(request, response);
            return;
        }

        if (count != null && count > requestsPerMinute) {
            long retryAfterSeconds = WINDOW_SECONDS;
            try {
                Long ttl = redisTemplate.getExpire(key);
                if (ttl != null && ttl > 0) {
                    retryAfterSeconds = ttl;
                }
            } catch (Exception ex) {
                log.warn("Failed to read TTL for key {}; defaulting Retry-After to {}s", key, retryAfterSeconds, ex);
            }

            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                    objectMapper.writeValueAsString(new ErrorResponse("Rate limit exceeded. Try again later.")));
            log.warn("Rate limit exceeded for key {}; returning 429", key);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
