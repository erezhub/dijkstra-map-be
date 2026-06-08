package com.eRez.tests.controller;

import com.eRez.tests.exception.GlobalExceptionHandler;
import com.eRez.tests.exception.UserException;
import com.eRez.tests.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── POST /auth/login ──────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        when(authService.login(any())).thenReturn("test-token-uuid");

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier": "m@x.com", "password": "pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-token-uuid"));
    }

    @Test
    void login_blankIdentifier_returns409() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier": "", "password": "pass"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void login_blankPassword_returns409() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier": "m@x.com", "password": ""}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void login_invalidCredentials_returns409WithMessage() throws Exception {
        doThrow(new UserException("Invalid credentials")).when(authService).login(any());

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"identifier": "nobody@x.com", "password": "wrong"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    // ── POST /auth/logout ─────────────────────────────────────────────────────

    @Test
    void logout_validToken_returns204() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer test-token-uuid"))
                .andExpect(status().isNoContent());

        verify(authService).logout("test-token-uuid");
    }
}
