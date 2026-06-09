package com.eRez.user.database.document;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TokenDocumentTest {

    @Test
    void onBeforeSave_newToken_setsCreatedAt() {
        TokenDocument doc = new TokenDocument();

        doc.onBeforeSave();

        assertThat(doc.getCreatedAt()).isNotNull();
    }

    @Test
    void onBeforeSave_existingToken_preservesCreatedAt() {
        TokenDocument doc = new TokenDocument();
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        doc.setCreatedAt(past);

        doc.onBeforeSave();

        assertThat(doc.getCreatedAt()).isEqualTo(past);
    }
}
