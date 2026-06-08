package com.eRez.tests.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank(message = "username cannot be blank")
    private String username;

    @NotBlank(message = "email cannot be blank")
    @Email(message = "email must be a valid email address")
    private String email;

    @NotBlank(message = "password cannot be blank")
    private String password;
}
