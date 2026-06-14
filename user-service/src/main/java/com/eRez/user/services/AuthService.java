package com.eRez.user.services;

import com.eRez.user.database.document.TokenDocument;
import com.eRez.user.database.document.UserDocument;
import com.eRez.user.database.repository.TokenRepository;
import com.eRez.user.database.repository.UserRepository;
import com.eRez.user.dto.request.LoginRequest;
import com.eRez.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${token.expiration-ms}")
    private long expirationMs;

    public String login(LoginRequest request) {
        UserDocument user = userRepository.findByEmail(request.getIdentifier())
                .or(() -> "admin".equals(request.getIdentifier())
                        ? userRepository.findByUsername("admin")
                        : Optional.empty())
                .orElseThrow(() -> new UserException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UserException("Invalid credentials");
        }

        if (user.isPasswordChangeRequired()) {
            LocalDateTime expiry = user.getTempPasswordExpiresAt();
            if (expiry == null || LocalDateTime.now(ZoneOffset.UTC).isAfter(expiry)) {
                throw new UserException("Temporary password has expired. Please contact your administrator.");
            }
            user.setTempPasswordExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
            userRepository.save(user);
        }

        String token = UUID.randomUUID().toString();
        Date expiresAt = new Date(System.currentTimeMillis() + expirationMs);
        tokenRepository.save(new TokenDocument(null, token, user.getId(), true, expiresAt));

        log.info("User '{}' logged in", user.getEmail() != null ? user.getEmail() : user.getUsername());
        return token;
    }

    public void logout(String token) {
        tokenRepository.findByToken(token).ifPresent(doc -> {
            doc.setValid(false);
            tokenRepository.save(doc);
            log.info("Token invalidated for user '{}'", doc.getUserId());
        });
    }
}
