package com.eRez.tests.database.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Getter
@AllArgsConstructor
@Document(collection = "blacklisted_tokens")
public class BlacklistedToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    @Indexed(expireAfterSeconds = 0)
    private Date expiresAt;
}
