package com.eRez.user.services;

import com.eRez.user.database.document.TokenDocument;
import com.eRez.user.database.document.UserDocument;
import com.eRez.user.database.repository.TokenRepository;
import com.eRez.user.database.repository.UserRepository;
import com.eRez.user.dto.UserRole;
import com.eRez.user.dto.request.LoginRequest;
import com.eRez.user.exception.UserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "expirationMs", 86400000L);
    }

    private UserDocument user(String id, String email, String username, String password, UserRole role) {
        return new UserDocument(id,email,username,password,role);
    }

    private LoginRequest loginRequest(String identifier, String password) {
        return new LoginRequest(identifier, password);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_withEmail_savesTokenAndReturnsIt() {
        UserDocument u = user("u1", "m@x.com", "Manager", "hashed", UserRole.MANAGER);
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String token = authService.login(loginRequest("m@x.com", "pass"));

        assertThat(token).isNotBlank();
        ArgumentCaptor<TokenDocument> captor = ArgumentCaptor.forClass(TokenDocument.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().isValid()).isTrue();
        assertThat(captor.getValue().getUserId()).isEqualTo("u1");
    }

    @Test
    void login_withUsername_fallsBackToUsernameSearch() {
        UserDocument admin = user("a1", null, "admin", "hashed", UserRole.ADMIN);
        when(userRepository.findByEmail("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("admin", "hashed")).thenReturn(true);
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String token = authService.login(loginRequest("admin", "admin"));

        assertThat(token).isNotBlank();
    }

    @Test
    void login_unknownIdentifier_throws() {
        when(userRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("nobody@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("nobody@x.com", "pass")))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_wrongPassword_throws() {
        UserDocument u = user("u1", "m@x.com", "Manager", "hashed", UserRole.MANAGER);
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("m@x.com", "wrong")))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_setsExpiresAtToNowPlusExpiration() {
        UserDocument u = user("u1", "m@x.com", "Manager", "hashed", UserRole.MANAGER);
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        long before = System.currentTimeMillis();
        authService.login(loginRequest("m@x.com", "pass"));
        long after = System.currentTimeMillis();

        ArgumentCaptor<TokenDocument> captor = ArgumentCaptor.forClass(TokenDocument.class);
        verify(tokenRepository).save(captor.capture());
        long expiresAt = captor.getValue().getExpiresAt().getTime();
        assertThat(expiresAt).isBetween(before + 86400000L, after + 86400000L);
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_validToken_setsValidFalseAndSaves() {
        TokenDocument doc = new TokenDocument("t1", "tok", "u1", true, null);
        when(tokenRepository.findByToken("tok")).thenReturn(Optional.of(doc));

        authService.logout("tok");

        assertThat(doc.isValid()).isFalse();
        verify(tokenRepository).save(doc);
    }

    @Test
    void logout_tokenNotFound_doesNothing() {
        when(tokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        authService.logout("unknown");

        verify(tokenRepository, never()).save(any());
    }
}
