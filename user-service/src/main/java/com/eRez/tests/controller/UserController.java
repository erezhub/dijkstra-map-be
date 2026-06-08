package com.eRez.tests.controller;

import com.eRez.tests.dto.request.CreateUserRequest;
import com.eRez.tests.dto.request.UpdateUserRequest;
import com.eRez.tests.dto.response.UserResponse;
import com.eRez.tests.services.UserService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> getUsers(@AuthenticationPrincipal UserDetails caller) {
        return userService.getUsers(caller.getUsername());
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@AuthenticationPrincipal UserDetails caller,
                                                    @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(caller.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@AuthenticationPrincipal UserDetails caller,
                                    @PathVariable String id) {
        return userService.getUserById(caller.getUsername(), id);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@AuthenticationPrincipal UserDetails caller,
                                   @PathVariable String id,
                                   @RequestBody UpdateUserRequest request) {
        return userService.updateUser(caller.getUsername(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal UserDetails caller,
                                           @PathVariable String id) {
        userService.deleteUser(caller.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse getSelf(@AuthenticationPrincipal UserDetails caller) {
        return userService.getSelf(caller.getUsername());
    }

    @PutMapping("/me")
    public UserResponse updateSelf(@AuthenticationPrincipal UserDetails caller,
                                   @RequestBody UpdateUserRequest request) {
        return userService.updateSelf(caller.getUsername(), request);
    }
}
