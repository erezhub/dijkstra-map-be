package com.eRez.map.database.repository;

import com.eRez.map.database.document.NodeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NodeRepository extends MongoRepository<NodeDocument, String> {

    Optional<NodeDocument> findByName(String name);
}
