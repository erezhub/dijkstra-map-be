package com.eRez.tests.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {

    private String username;

    @Email(message = "email must be a valid email address")
    private String email;

    private String password;
}
