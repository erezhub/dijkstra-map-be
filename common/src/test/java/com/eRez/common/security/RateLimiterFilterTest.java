package com.eRez.common.security;

import com.eRez.common.dto.response.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterFilterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisScript<Long> rateLimitScript;
    @Mock private FilterChain filterChain;

    private RateLimiterFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        filter = new RateLimiterFilter(redisTemplate, rateLimitScript, objectMapper);
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 5);
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String identifier, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var auth = new UsernamePasswordAuthenticationToken(identifier, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ── GET requests are never limited ───────────────────────────────────────

    @Test
    void getRequest_passesThroughRegardlessOfCount() throws Exception {
        authenticateAs("user@x.com", "REGULAR");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/map");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verifyNoInteractions(redisTemplate);
    }

    // ── unauthenticated ───────────────────────────────────────────────────────

    @Test
    void unauthenticated_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/map/node");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verifyNoInteractions(redisTemplate);
    }

    // ── admin exemption ───────────────────────────────────────────────────────

    @Test
    void adminRole_passesThroughRegardlessOfCount() throws Exception {
        authenticateAs("admin", "ADMIN");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/map/node");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        verifyNoInteractions(redisTemplate);
    }

    // ── under limit ───────────────────────────────────────────────────────────

    @Test
    void nonAdminUnderLimit_passesThroughAndIncrementsCount() throws Exception {
        authenticateAs("user@x.com", "REGULAR");
        when(redisTemplate.execute(eq(rateLimitScript), eq(List.of("rate-limit:user@x.com"))))
                .thenReturn(3L);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/map/node");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    // ── over limit ────────────────────────────────────────────────────────────

    @Test
    void nonAdminOverLimit_returns429AndBlocksChain() throws Exception {
        authenticateAs("user@x.com", "REGULAR");
        when(redisTemplate.execute(eq(rateLimitScript), eq(List.of("rate-limit:user@x.com"))))
                .thenReturn(6L);
        when(redisTemplate.getExpire("rate-limit:user@x.com")).thenReturn(45L);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/map/node");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentType()).startsWith("application/json");
        assertThat(res.getHeader("Retry-After")).isEqualTo("45");
        ErrorResponse body = objectMapper.readValue(res.getContentAsString(), ErrorResponse.class);
        assertThat(body.getMessage()).isNotBlank();
        verify(filterChain, never()).doFilter(req, res);
    }

    // ── Retry-After falls back to the window length if TTL is unavailable ───

    @Test
    void nonAdminOverLimit_ttlLookupFails_retryAfterDefaultsToWindowLength() throws Exception {
        authenticateAs("user@x.com", "REGULAR");
        when(redisTemplate.execute(eq(rateLimitScript), eq(List.of("rate-limit:user@x.com"))))
                .thenReturn(6L);
        when(redisTemplate.getExpire("rate-limit:user@x.com"))
                .thenThrow(new RuntimeException("Redis connection failed"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/map/node");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
    }

    // ── boundary: count equals limit is still allowed ────────────────────────

    @Test
    void nonAdminExactlyAtBoundary_countEqualsLimit_isAllowed() throws Exception {
        authenticateAs("user@x.com", "REGULAR");
        when(redisTemplate.execute(eq(rateLimitScript), eq(List.of("rate-limit:user@x.com"))))
                .thenReturn(5L);

        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/map/node/1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    // ── key naming: no service prefix, proves global/shared quota ───────────

    @Test
    void keyNaming_usesAuthenticationNameNotServicePrefixed() throws Exception {
        authenticateAs("erez.huberman@gmail.com", "REGULAR");
        when(redisTemplate.execute(eq(rateLimitScript), eq(List.of("rate-limit:erez.huberman@gmail.com"))))
                .thenReturn(1L);

        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/map/node/1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(redisTemplate).execute(eq(rateLimitScript), eq(List.of("rate-limit:erez.huberman@gmail.com")));
    }

    // ── Redis failure: fail open ─────────────────────────────────────────────

    @Test
    void redisThrowsException_failsOpenAndLogsWarning() throws Exception {
        authenticateAs("user@x.com", "REGULAR");
        when(redisTemplate.execute(eq(rateLimitScript), eq(List.of("rate-limit:user@x.com"))))
                .thenThrow(new RuntimeException("Redis connection failed"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/map/node");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
