package com.eRez.user.services;

import com.eRez.user.database.document.UserDocument;
import com.eRez.user.database.repository.UserRepository;
import com.eRez.user.dto.UserRole;
import com.eRez.user.dto.request.CreateUserRequest;
import com.eRez.user.dto.request.UpdateUserRequest;
import com.eRez.user.dto.response.UserResponse;
import com.eRez.user.exception.UserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    private UserDocument admin;
    private UserDocument manager;
    private UserDocument regular;

    private static final String PASSWORD = "pass";
    private static final String HASHED_PASSWORD = "hashed";

    @BeforeEach
    void setUp() {
        admin   = user("id-admin",   null,      "admin",   UserRole.ADMIN);
        manager = user("id-manager", "m@x.com", "Manager", UserRole.MANAGER);
        regular = user("id-regular", "r@x.com", "Regular", UserRole.REGULAR);
    }

    private UserDocument user(String id, String email, String username, UserRole role) {
        return new UserDocument(id, username, email,HASHED_PASSWORD, role);
    }

    private CreateUserRequest createUserRequest(String username, String email, String password) {
        CreateUserRequest r = new CreateUserRequest();
        r.setUsername(username);
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    // ── getUsers ──────────────────────────────────────────────────────────────

    @Test
    void getUsers_asAdmin_returnsManagers() {
        when(userRepository.findByEmail("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findByRole(UserRole.MANAGER)).thenReturn(List.of(manager));

        List<UserResponse> result = userService.getUsers("admin");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(UserRole.MANAGER);
    }

    @Test
    void getUsers_asManager_returnsRegulars() {
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(manager));
        when(userRepository.findByRole(UserRole.REGULAR)).thenReturn(List.of(regular));

        List<UserResponse> result = userService.getUsers("m@x.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(UserRole.REGULAR);
    }

    @Test
    void getUsers_asRegular_throws() {
        when(userRepository.findByEmail("r@x.com")).thenReturn(Optional.of(regular));

        assertThatThrownBy(() -> userService.getUsers("r@x.com"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Access denied");
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Test
    void createUser_asAdmin_createsManager() {
        when(userRepository.findByEmail("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.existsByEmail("new@x.com")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PASSWORD);

        UserResponse result = userService.createUser("admin", createUserRequest("NewManager", "new@x.com", PASSWORD));

        assertThat(result.getRole()).isEqualTo(UserRole.MANAGER);
        assertThat(result.getEmail()).isEqualTo("new@x.com");
        verify(userRepository).save(any());
    }

    @Test
    void createUser_asManager_createsRegular() {
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail("new@x.com")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED_PASSWORD);

        UserResponse result = userService.createUser("m@x.com", createUserRequest("NewUser", "new@x.com", PASSWORD));

        assertThat(result.getRole()).isEqualTo(UserRole.REGULAR);
    }

    @Test
    void createUser_duplicateEmail_throwsWithoutSaving() {
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail("r@x.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("m@x.com", createUserRequest("User", "r@x.com", PASSWORD)))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Email already in use");

        verify(userRepository, never()).save(any());
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    void getUserById_adminGetsManager_returnsResponse() {
        when(userRepository.findByEmail("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById("id-manager")).thenReturn(Optional.of(manager));

        UserResponse result = userService.getUserById("admin", "id-manager");

        assertThat(result.getId()).isEqualTo("id-manager");
    }

    @Test
    void getUserById_adminGetsRegular_throws() {
        when(userRepository.findByEmail("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById("id-regular")).thenReturn(Optional.of(regular));

        assertThatThrownBy(() -> userService.getUserById("admin", "id-regular"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void getUserById_notFound_throws() {
        when(userRepository.findByEmail("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById("admin", "ghost"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("User not found");
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Test
    void updateUser_changeOwnPassword_updatesAndSaves() {
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(manager));
        when(userRepository.findById("id-manager")).thenReturn(Optional.of(manager));
        when(passwordEncoder.encode("newpass")).thenReturn("newhashed");

        UpdateUserRequest req = new UpdateUserRequest();
        req.setPassword("newpass");
        userService.updateUser("m@x.com", "id-manager", req);

        assertThat(manager.getPassword()).isEqualTo("newhashed");
        verify(userRepository).save(manager);
    }

    @Test
    void updateUser_changeOtherPassword_throws() {
        when(userRepository.findByEmail("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById("id-manager")).thenReturn(Optional.of(manager));

        UpdateUserRequest req = new UpdateUserRequest();
        req.setPassword("newpass");

        assertThatThrownBy(() -> userService.updateUser("admin", "id-manager", req))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Cannot change another user's password");
    }

    @Test
    void updateUser_emailAlreadyInUse_throws() {
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(manager));
        when(userRepository.findById("id-manager")).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail("taken@x.com")).thenReturn(true);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setEmail("taken@x.com");

        assertThatThrownBy(() -> userService.updateUser("m@x.com", "id-manager", req))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Email already in use");
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Test
    void deleteUser_asAdmin_deletesManager() {
        when(userRepository.findByEmail("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById("id-manager")).thenReturn(Optional.of(manager));

        userService.deleteUser("admin", "id-manager");

        verify(userRepository).delete(manager);
    }

    @Test
    void deleteUser_asManager_deletesRegular() {
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(manager));
        when(userRepository.findById("id-regular")).thenReturn(Optional.of(regular));

        userService.deleteUser("m@x.com", "id-regular");

        verify(userRepository).delete(regular);
    }

    @Test
    void deleteUser_accessDenied_throws() {
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(manager));
        when(userRepository.findById("id-admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.deleteUser("m@x.com", "id-admin"))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Access denied");
    }

    // ── getSelf / updateSelf ──────────────────────────────────────────────────

    @Test
    void getSelf_returnsCallerData() {
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(manager));

        UserResponse result = userService.getSelf("m@x.com");

        assertThat(result.getId()).isEqualTo("id-manager");
        assertThat(result.getEmail()).isEqualTo("m@x.com");
    }

    @Test
    void updateSelf_updatesUsernameAndEmail() {
        when(userRepository.findByEmail("m@x.com")).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail("new@x.com")).thenReturn(false);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setUsername("NewName");
        req.setEmail("new@x.com");
        userService.updateSelf("m@x.com", req);

        assertThat(manager.getUsername()).isEqualTo("NewName");
        assertThat(manager.getEmail()).isEqualTo("new@x.com");
        verify(userRepository).save(manager);
    }
}
