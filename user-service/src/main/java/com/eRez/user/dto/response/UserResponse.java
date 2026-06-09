package com.eRez.user.dto.response;

import com.eRez.user.dto.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String username;
    private UserRole role;
    private String email;
}
