package com.eRez.user.dto.event;

import com.eRez.user.database.document.UserDocument;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserCreatedEvent {
    private String id;
    private String username;
    private String email;
    private String role;

    public UserCreatedEvent(UserDocument user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.role = user.getRole().name();
    }
}
