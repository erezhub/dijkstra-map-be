package com.eRez.tests.database.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@Document(collection = "tokens")
public class TokenDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    private String userId;

    private boolean valid;

    @Indexed(expireAfterSeconds = 0)
    private Date expiresAt;
}
