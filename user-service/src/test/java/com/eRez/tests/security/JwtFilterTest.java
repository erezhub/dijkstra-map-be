package com.eRez.tests.security;

import com.eRez.tests.database.document.TokenDocument;
import com.eRez.tests.database.document.UserDocument;
import com.eRez.tests.database.repository.TokenRepository;
import com.eRez.tests.database.repository.UserRepository;
import com.eRez.tests.dto.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock private TokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private FilterChain filterChain;
    @InjectMocks private JwtFilter jwtFilter;

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private TokenDocument validToken(String userId) {
        return new TokenDocument("t1", "tok", userId, true,
                new Date(System.currentTimeMillis() + 60_000));
    }

    private TokenDocument expiredToken(String userId) {
        return new TokenDocument("t1", "tok", userId, true,
                new Date(System.currentTimeMillis() - 1_000));
    }

    private TokenDocument invalidatedToken(String userId) {
        return new TokenDocument("t1", "tok", userId, false,
                new Date(System.currentTimeMillis() + 60_000));
    }

    private UserDocument manager() {
        UserDocument u = new UserDocument("u1", "Manager", "m@x.com", "hashed", UserRole.MANAGER);
        return u;
    }

    // ── no Authorization header ───────────────────────────────────────────────

    @Test
    void noHeader_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── valid token ───────────────────────────────────────────────────────────

    @Test
    void validToken_setsAuthentication() throws Exception {
        when(tokenRepository.findByToken("tok")).thenReturn(Optional.of(validToken("u1")));
        when(userRepository.findById("u1")).thenReturn(Optional.of(manager()));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    }

    // ── expired token ─────────────────────────────────────────────────────────

    @Test
    void expiredToken_returns401() throws Exception {
        when(tokenRepository.findByToken("tok")).thenReturn(Optional.of(expiredToken("u1")));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── invalidated token ─────────────────────────────────────────────────────

    @Test
    void invalidatedToken_returns401() throws Exception {
        when(tokenRepository.findByToken("tok")).thenReturn(Optional.of(invalidatedToken("u1")));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(req, res);
    }

    // ── token not in DB ───────────────────────────────────────────────────────

    @Test
    void unknownToken_returns401() throws Exception {
        when(tokenRepository.findByToken("ghost")).thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer ghost");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(req, res);
    }
}
