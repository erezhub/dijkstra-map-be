package com.eRez.tests.services;

import com.eRez.tests.database.document.TokenDocument;
import com.eRez.tests.database.document.UserDocument;
import com.eRez.tests.database.repository.TokenRepository;
import com.eRez.tests.database.repository.UserRepository;
import com.eRez.tests.dto.request.LoginRequest;
import com.eRez.tests.exception.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
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
                .or(() -> userRepository.findByUsername(request.getIdentifier()))
                .orElseThrow(() -> new UserException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UserException("Invalid credentials");
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
