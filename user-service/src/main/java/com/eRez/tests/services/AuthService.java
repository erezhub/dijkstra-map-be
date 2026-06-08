package com.eRez.tests.services;

import com.eRez.tests.database.document.BlacklistedToken;
import com.eRez.tests.database.document.UserDocument;
import com.eRez.tests.database.repository.BlacklistRepository;
import com.eRez.tests.database.repository.UserRepository;
import com.eRez.tests.dto.request.LoginRequest;
import com.eRez.tests.exception.UserException;
import com.eRez.tests.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BlacklistRepository blacklistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public String login(LoginRequest request) {
        UserDocument user = userRepository.findByEmail(request.getIdentifier())
                .or(() -> userRepository.findByUsername(request.getIdentifier()))
                .orElseThrow(() -> new UserException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UserException("Invalid credentials");
        }

        String subject = user.getEmail() != null ? user.getEmail() : user.getUsername();
        log.info("User '{}' logged in", subject);
        return jwtUtil.generateToken(subject);
    }

    public void logout(String token) {
        Date expiresAt = new Date(System.currentTimeMillis() + expirationMs);
        blacklistRepository.save(new BlacklistedToken(null, token, expiresAt));
        log.info("Token blacklisted");
    }
}
