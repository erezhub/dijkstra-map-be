package com.eRez.map.database.repository;

import com.eRez.map.database.document.RouteDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends MongoRepository<RouteDocument, String> {

    @Query("{ $or: [ { nodeA: ?0, nodeB: ?1 }, { nodeA: ?1, nodeB: ?0 } ] }")
    Optional<RouteDocument> findRoute(String a, String b);

    @Query("{ $or: [ { nodeA: ?0, nodeB: ?1 }, { nodeA: ?1, nodeB: ?0 } ], createdBy: ?2 }")
    Optional<RouteDocument> findRouteByCreator(String a, String b, String createdBy);

    @Query("{ path: ?0 }")
    List<RouteDocument> findByPathContaining(String nodeName);

    List<RouteDocument> findByStaleTrue();

    List<RouteDocument> findByCreatedByContaining(String username);

    void deleteByNodeAOrNodeB(String nodeA, String nodeB);
}
