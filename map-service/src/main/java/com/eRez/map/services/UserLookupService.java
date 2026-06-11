package com.eRez.map.services;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
public class UserLookupService {

    private final MongoTemplate tokenValidationMongoTemplate;

    public UserLookupService(@Qualifier("tokenValidationMongoTemplate") MongoTemplate tokenValidationMongoTemplate) {
        this.tokenValidationMongoTemplate = tokenValidationMongoTemplate;
    }

    public List<String> resolveRecipients(List<String> createdBy) {
        List<String> ownerEmails = createdBy == null ? List.of() :
                createdBy.stream().filter(u -> !u.equals("admin")).toList();

        List<Document> managers = tokenValidationMongoTemplate.find(
                new Query(Criteria.where("role").is("MANAGER")),
                Document.class, "users");
        List<String> managerEmails = managers.stream()
                .map(d -> d.getString("email"))
                .filter(Objects::nonNull)
                .toList();

        return Stream.concat(ownerEmails.stream(), managerEmails.stream())
                .distinct()
                .toList();
    }
}
