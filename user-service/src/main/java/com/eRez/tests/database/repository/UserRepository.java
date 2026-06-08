package com.eRez.tests.database.repository;

import com.eRez.tests.database.document.UserDocument;
import com.eRez.tests.dto.UserRole;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<UserDocument, String> {
    Optional<UserDocument> findByEmail(String email);
    Optional<UserDocument> findByUsername(String username);
    List<UserDocument> findByRole(UserRole role);
    boolean existsByEmail(String email);
}
