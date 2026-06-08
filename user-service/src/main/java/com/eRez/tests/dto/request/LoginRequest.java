package com.eRez.tests.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "identifier cannot be blank")
    private String identifier;

    @NotBlank(message = "password cannot be blank")
    private String password;
}
