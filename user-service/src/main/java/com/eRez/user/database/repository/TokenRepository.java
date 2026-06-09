package com.eRez.user.database.repository;

import com.eRez.user.database.document.TokenDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TokenRepository extends MongoRepository<TokenDocument, String> {
    Optional<TokenDocument> findByToken(String token);
}
