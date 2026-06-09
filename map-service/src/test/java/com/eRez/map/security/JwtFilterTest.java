package com.eRez.map.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock private MongoTemplate usersMongoTemplate;
    @Mock private FilterChain filterChain;

    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() {
        jwtFilter = new JwtFilter(usersMongoTemplate);
        SecurityContextHolder.clearContext();
    }

    private Document tokenDoc(boolean valid, long expiresAtOffset) {
        Document doc = new Document();
        doc.put("token", "tok");
        doc.put("valid", valid);
        doc.put("expiresAt", new Date(System.currentTimeMillis() + expiresAtOffset));
        doc.put("userId", "u1");
        return doc;
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
        when(usersMongoTemplate.findOne(any(), eq(Document.class), eq("tokens")))
                .thenReturn(tokenDoc(true, 60_000));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("u1");
    }

    // ── expired token ─────────────────────────────────────────────────────────

    @Test
    void expiredToken_returns401() throws Exception {
        when(usersMongoTemplate.findOne(any(), eq(Document.class), eq("tokens")))
                .thenReturn(tokenDoc(true, -1_000));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(req, res);
    }

    // ── invalidated token ─────────────────────────────────────────────────────

    @Test
    void invalidatedToken_returns401() throws Exception {
        when(usersMongoTemplate.findOne(any(), eq(Document.class), eq("tokens")))
                .thenReturn(tokenDoc(false, 60_000));

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
        when(usersMongoTemplate.findOne(any(), eq(Document.class), eq("tokens")))
                .thenReturn(null);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer ghost");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(req, res);
    }
}
