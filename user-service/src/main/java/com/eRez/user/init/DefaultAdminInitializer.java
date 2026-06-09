package com.eRez.user.init;

import com.eRez.user.database.document.UserDocument;
import com.eRez.user.database.repository.UserRepository;
import com.eRez.user.dto.UserRole;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAdminInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default.password}")
    private String defaultPassword;

    @PostConstruct
    public void init() {
        if (userRepository.findByRole(UserRole.ADMIN).isEmpty()) {
            userRepository.save(new UserDocument(null, "admin", null, passwordEncoder.encode(defaultPassword), UserRole.ADMIN));
            log.info("Default admin user created");
        }
    }
}
