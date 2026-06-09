package com.eRez.user.database.document;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UserDocumentTest {

    @Test
    void onBeforeSave_newDocument_setsCreatedAtAndUpdatedAt() {
        UserDocument doc = new UserDocument();

        doc.onBeforeSave();

        assertThat(doc.getCreatedAt()).isNotNull();
        assertThat(doc.getUpdatedAt()).isNotNull();
    }

    @Test
    void onBeforeSave_existingDocument_preservesCreatedAtAndUpdatesUpdatedAt() {
        UserDocument doc = new UserDocument();
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        doc.setCreatedAt(past);
        doc.setUpdatedAt(past);

        doc.onBeforeSave();

        assertThat(doc.getCreatedAt()).isEqualTo(past);
        assertThat(doc.getUpdatedAt()).isAfter(past);
    }
}
