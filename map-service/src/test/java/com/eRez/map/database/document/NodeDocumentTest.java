package com.eRez.map.database.document;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NodeDocumentTest {

    @Test
    void onBeforeSave_newDocument_setsCreatedAtAndUpdatedAt() {
        NodeDocument doc = new NodeDocument();

        doc.onBeforeSave();

        assertThat(doc.getCreatedAt()).isNotNull();
        assertThat(doc.getUpdatedAt()).isNotNull();
    }

    @Test
    void onBeforeSave_existingDocument_preservesCreatedAtAndUpdatesUpdatedAt() {
        NodeDocument doc = new NodeDocument();
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        doc.setCreatedAt(past);
        doc.setUpdatedAt(past);

        doc.onBeforeSave();

        assertThat(doc.getCreatedAt()).isEqualTo(past);
        assertThat(doc.getUpdatedAt()).isAfter(past);
    }
}
