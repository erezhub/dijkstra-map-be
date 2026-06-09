package com.eRez.common.data;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;

public class AuditingMongoRepository<T, ID> extends SimpleMongoRepository<T, ID> {

    public AuditingMongoRepository(MongoEntityInformation<T, ID> metadata, MongoOperations mongoOperations) {
        super(metadata, mongoOperations);
    }

    @Override
    public <S extends T> S save(S entity) {
        if (entity instanceof Auditable a) a.onBeforeSave();
        return super.save(entity);
    }
}
