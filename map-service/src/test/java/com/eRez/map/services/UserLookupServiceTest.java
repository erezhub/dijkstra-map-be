package com.eRez.map.services;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

    @Mock private MongoTemplate mongoTemplate;

    private UserLookupService userLookupService;

    @BeforeEach
    void setUp() {
        userLookupService = new UserLookupService(mongoTemplate);
    }

    private Document managerDoc(String email) {
        Document doc = new Document();
        doc.put("email", email);
        doc.put("role", "MANAGER");
        return doc;
    }

    @Test
    void resolveRecipients_includesOwnerEmailsAndManagerEmails() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of(managerDoc("mgr@x.com")));

        List<String> result = userLookupService.resolveRecipients(List.of("owner@x.com"));

        assertThat(result).containsExactlyInAnyOrder("owner@x.com", "mgr@x.com");
    }

    @Test
    void resolveRecipients_filtersOutAdmin() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of());

        List<String> result = userLookupService.resolveRecipients(List.of("admin", "user@x.com"));

        assertThat(result).containsExactly("user@x.com");
        assertThat(result).doesNotContain("admin");
    }

    @Test
    void resolveRecipients_deduplicatesOwnerWhoIsAlsoManager() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of(managerDoc("mgr@x.com")));

        // mgr@x.com is both in createdBy and in the managers list
        List<String> result = userLookupService.resolveRecipients(List.of("mgr@x.com", "user@x.com"));

        assertThat(result).containsExactlyInAnyOrder("mgr@x.com", "user@x.com");
        assertThat(result).hasSize(2);
    }

    @Test
    void resolveRecipients_nullCreatedBy_returnsOnlyManagers() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of(managerDoc("mgr@x.com")));

        List<String> result = userLookupService.resolveRecipients(null);

        assertThat(result).containsExactly("mgr@x.com");
    }

    @Test
    void resolveRecipients_noManagersNoOwners_returnsEmpty() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of());

        List<String> result = userLookupService.resolveRecipients(List.of());

        assertThat(result).isEmpty();
    }
}