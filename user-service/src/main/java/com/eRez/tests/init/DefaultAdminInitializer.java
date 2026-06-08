package com.eRez.tests.init;

import com.eRez.tests.database.document.UserDocument;
import com.eRez.tests.database.repository.UserRepository;
import com.eRez.tests.dto.UserRole;
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
            UserDocument admin = new UserDocument(
                    null,
                    "admin",
                    null,
                    passwordEncoder.encode(defaultPassword),
                    UserRole.ADMIN);
            userRepository.save(admin);
            log.info("Default admin user created");
        }
    }
}
