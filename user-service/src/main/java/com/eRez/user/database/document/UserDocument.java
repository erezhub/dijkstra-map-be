package com.eRez.user.database.document;

import com.eRez.user.dto.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@AllArgsConstructor
@Document(collection = "users")
public class UserDocument {

    @Id
    private String id;

    private String username;

    @Indexed(unique = true, sparse = true)
    private String email;

    private String password;

    private UserRole role;
}
