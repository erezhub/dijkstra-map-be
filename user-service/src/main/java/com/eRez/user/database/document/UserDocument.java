package com.eRez.user.database.document;

import com.eRez.common.data.Auditable;
import com.eRez.user.dto.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "users")
public class UserDocument implements Auditable {

    @Id
    private String id;

    private String username;

    @Indexed(unique = true, sparse = true)
    private String email;

    private String password;

    private UserRole role;

    private boolean passwordChangeRequired = false;

    private LocalDateTime tempPasswordExpiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public UserDocument(String id, String username, String email, String password, UserRole role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    @Override
    public void onBeforeSave() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
}
