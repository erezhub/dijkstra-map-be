package com.eRez.notification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserCreatedEvent {
    private String id;
    private String username;
    private String email;
    private String role;
    private String tempPassword;
}