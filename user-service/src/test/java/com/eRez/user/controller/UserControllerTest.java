package com.eRez.user.controller;

import com.eRez.user.dto.UserRole;
import com.eRez.user.dto.response.UserResponse;
import com.eRez.common.exception.GlobalExceptionHandler;
import com.eRez.user.exception.UserException;
import com.eRez.user.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var userDetails = User.withUsername("admin").password("").authorities(authorities).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));

        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserResponse response(String id, String username, UserRole role, String email) {
        return new UserResponse(id, username, role, email, false);
    }

    // ── GET /users ────────────────────────────────────────────────────────────

    @Test
    void getUsers_returns200WithList() throws Exception {
        when(userService.getUsers("admin")).thenReturn(List.of(
                response("id-1", "Manager One", UserRole.MANAGER, "m@x.com")
        ));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("Manager One"))
                .andExpect(jsonPath("$[0].role").value("MANAGER"));
    }

    @Test
    void getUsers_serviceThrows_returns409() throws Exception {
        doThrow(new UserException("Access denied")).when(userService).getUsers("admin");

        mockMvc.perform(get("/users"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    // ── POST /users ───────────────────────────────────────────────────────────

    @Test
    void createUser_validBody_returns201() throws Exception {
        when(userService.createUser(eq("admin"), any())).thenReturn(
                response("id-new", "New Manager", UserRole.MANAGER, "new@x.com"));

        mockMvc.perform(post("/users")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username": "New Manager", "email": "new@x.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("id-new"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void createUser_blankUsername_returns409() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username": "", "email": "new@x.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void createUser_invalidEmail_returns409() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username": "User", "email": "not-an-email"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void createUser_duplicateEmail_returns409() throws Exception {
        doThrow(new UserException("Email already in use: r@x.com"))
                .when(userService).createUser(eq("admin"), any());

        mockMvc.perform(post("/users")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username": "User", "email": "r@x.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already in use: r@x.com"));
    }

    // ── POST /users/{id}/resend-temp-password ─────────────────────────────────

    @Test
    void resendTempPassword_returns204() throws Exception {
        mockMvc.perform(post("/users/id-1/resend-temp-password"))
                .andExpect(status().isNoContent());

        verify(userService).resendTempPassword("admin", "id-1");
    }

    @Test
    void resendTempPassword_alreadyPermanent_returns409() throws Exception {
        doThrow(new UserException("User already has a permanent password"))
                .when(userService).resendTempPassword("admin", "id-1");

        mockMvc.perform(post("/users/id-1/resend-temp-password"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User already has a permanent password"));
    }

    // ── GET /users/{id} ───────────────────────────────────────────────────────

    @Test
    void getUserById_returns200() throws Exception {
        when(userService.getUserById("admin", "id-1")).thenReturn(
                response("id-1", "Manager One", UserRole.MANAGER, "m@x.com"));

        mockMvc.perform(get("/users/id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("id-1"));
    }

    @Test
    void getUserById_notFound_returns409() throws Exception {
        doThrow(new UserException("User not found: ghost")).when(userService).getUserById("admin", "ghost");

        mockMvc.perform(get("/users/ghost"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User not found: ghost"));
    }

    // ── PUT /users/{id} ───────────────────────────────────────────────────────

    @Test
    void updateUser_returns200() throws Exception {
        when(userService.updateUser(eq("admin"), eq("id-1"), any())).thenReturn(
                response("id-1", "Updated", UserRole.MANAGER, "m@x.com"));

        mockMvc.perform(put("/users/id-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username": "Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Updated"));
    }

    @Test
    void updateUser_serviceThrows_returns409() throws Exception {
        doThrow(new UserException("Cannot change another user's password"))
                .when(userService).updateUser(eq("admin"), eq("id-1"), any());

        mockMvc.perform(put("/users/id-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"password": "newpass"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot change another user's password"));
    }

    // ── DELETE /users/{id} ────────────────────────────────────────────────────

    @Test
    void deleteUser_returns204() throws Exception {
        mockMvc.perform(delete("/users/id-1"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser("admin", "id-1");
    }

    // ── GET /users/me ─────────────────────────────────────────────────────────

    @Test
    void getSelf_returns200() throws Exception {
        when(userService.getSelf("admin")).thenReturn(
                response("id-admin", "admin", UserRole.ADMIN, null));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    // ── PUT /users/me ─────────────────────────────────────────────────────────

    @Test
    void updateSelf_returns200() throws Exception {
        when(userService.updateSelf(eq("admin"), any())).thenReturn(
                response("id-admin", "NewName", UserRole.ADMIN, null));

        mockMvc.perform(put("/users/me")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username": "NewName"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("NewName"));
    }
}
