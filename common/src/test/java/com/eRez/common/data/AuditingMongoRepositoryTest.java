package com.eRez.common.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuditingMongoRepositoryTest {

    @Mock MongoEntityInformation<TestEntity, String> entityInfo;
    @Mock MongoOperations mongoOperations;

    AuditingMongoRepository<TestEntity, String> repository;

    @BeforeEach
    void setUp() {
        lenient().when(entityInfo.isNew(any())).thenReturn(true);
        lenient().when(entityInfo.getCollectionName()).thenReturn("test");
        lenient().doAnswer(inv -> inv.getArgument(0)).when(mongoOperations).insert(any(), any(String.class));
        lenient().doAnswer(inv -> inv.getArgument(0)).when(mongoOperations).save(any(), any(String.class));
        repository = new AuditingMongoRepository<>(entityInfo, mongoOperations);
    }

    static class TestEntity implements Auditable {
        LocalDateTime createdAt;
        LocalDateTime updatedAt;

        @Override
        public void onBeforeSave() {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            if (createdAt == null) createdAt = now;
            updatedAt = now;
        }
    }

    @Test
    void save_newAuditable_setsCreatedAtAndUpdatedAt() {
        TestEntity entity = new TestEntity();

        repository.save(entity);

        assertThat(entity.createdAt).isNotNull();
        assertThat(entity.updatedAt).isNotNull();
    }

    @Test
    void save_existingAuditable_preservesCreatedAtAndUpdatesUpdatedAt() {
        TestEntity entity = new TestEntity();
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        entity.createdAt = past;
        entity.updatedAt = past;

        repository.save(entity);

        assertThat(entity.createdAt).isEqualTo(past);
        assertThat(entity.updatedAt).isAfter(past);
    }
}
