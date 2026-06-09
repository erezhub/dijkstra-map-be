package com.eRez.common.security;

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
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock private MongoTemplate tokenValidationMongoTemplate;
    @Mock private FilterChain filterChain;

    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() {
        jwtFilter = new JwtFilter(tokenValidationMongoTemplate);
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

    private Document userDoc() {
        Document doc = new Document();
        doc.put("_id", "u1");
        doc.put("username", "Manager");
        doc.put("email", "m@x.com");
        doc.put("role", "MANAGER");
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
        when(tokenValidationMongoTemplate.findOne(any(), eq(Document.class), eq("tokens")))
                .thenReturn(tokenDoc(true, 60_000));
        when(tokenValidationMongoTemplate.findOne(any(), eq(Document.class), eq("users")))
                .thenReturn(userDoc());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.getUsername()).isEqualTo("m@x.com");
        assertThat(principal.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
    }

    // ── expired token ─────────────────────────────────────────────────────────

    @Test
    void expiredToken_returns401() throws Exception {
        when(tokenValidationMongoTemplate.findOne(any(), eq(Document.class), eq("tokens")))
                .thenReturn(tokenDoc(true, -1_000));

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
        when(tokenValidationMongoTemplate.findOne(any(), eq(Document.class), eq("tokens")))
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
        when(tokenValidationMongoTemplate.findOne(any(), eq(Document.class), eq("tokens")))
                .thenReturn(null);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer ghost");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(req, res);
    }

    // ── user not found ────────────────────────────────────────────────────────

    @Test
    void userNotFound_returns401() throws Exception {
        when(tokenValidationMongoTemplate.findOne(any(), eq(Document.class), eq("tokens")))
                .thenReturn(tokenDoc(true, 60_000));
        when(tokenValidationMongoTemplate.findOne(any(), eq(Document.class), eq("users")))
                .thenReturn(null);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(req, res);
    }
}
