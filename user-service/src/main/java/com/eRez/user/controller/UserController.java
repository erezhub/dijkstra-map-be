package com.eRez.user.controller;

import com.eRez.user.dto.request.CreateUserRequest;
import com.eRez.user.dto.request.UpdateUserRequest;
import com.eRez.user.dto.response.UserResponse;
import com.eRez.user.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<UserResponse> getUsers(@AuthenticationPrincipal UserDetails caller) {
        return userService.getUsers(caller.getUsername());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@AuthenticationPrincipal UserDetails caller,
                                                    @Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(caller.getUsername(), request);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse getUserById(@AuthenticationPrincipal UserDetails caller,
                                    @PathVariable String id) {
        return userService.getUserById(caller.getUsername(), id);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse updateUser(@AuthenticationPrincipal UserDetails caller,
                                   @PathVariable String id,
                                   @RequestBody UpdateUserRequest request) {
        return userService.updateUser(caller.getUsername(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@AuthenticationPrincipal UserDetails caller,
                                           @PathVariable String id) {
        userService.deleteUser(caller.getUsername(), id);
    }

    @PostMapping("/{id}/resend-temp-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendTempPassword(@AuthenticationPrincipal UserDetails caller,
                                   @PathVariable String id) {
        userService.resendTempPassword(caller.getUsername(), id);
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse getSelf(@AuthenticationPrincipal UserDetails caller) {
        return userService.getSelf(caller.getUsername());
    }

    @PutMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public UserResponse updateSelf(@AuthenticationPrincipal UserDetails caller,
                                   @RequestBody UpdateUserRequest request) {
        return userService.updateSelf(caller.getUsername(), request);
    }
}
