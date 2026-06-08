package com.eRez.tests.database.repository;

import com.eRez.tests.database.document.BlacklistedToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BlacklistRepository extends MongoRepository<BlacklistedToken, String> {
    boolean existsByToken(String token);
}
