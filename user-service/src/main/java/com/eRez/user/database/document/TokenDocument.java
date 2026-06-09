package com.eRez.user.database.document;

import com.eRez.common.data.Auditable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "tokens")
public class TokenDocument implements Auditable {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    private String userId;

    private boolean valid;

    @Indexed(expireAfter = "0s")
    private Date expiresAt;

    private LocalDateTime createdAt;

    public TokenDocument(String id, String token, String userId, boolean valid, Date expiresAt) {
        this.id = id;
        this.token = token;
        this.userId = userId;
        this.valid = valid;
        this.expiresAt = expiresAt;
    }

    @Override
    public void onBeforeSave() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
