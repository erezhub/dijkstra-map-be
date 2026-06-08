package com.eRez.tests.database.repository;

import com.eRez.tests.database.document.NodeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NodeRepository extends MongoRepository<NodeDocument, String> {

    Optional<NodeDocument> findByName(String name);
}
