package com.eRez.tests.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "identifier cannot be blank")
    private String identifier;

    @NotBlank(message = "password cannot be blank")
    private String password;
}
