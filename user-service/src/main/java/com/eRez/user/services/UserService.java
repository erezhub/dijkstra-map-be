package com.eRez.user.services;

import com.eRez.user.database.document.UserDocument;
import com.eRez.user.database.repository.UserRepository;
import com.eRez.user.dto.UserRole;
import com.eRez.user.dto.request.CreateUserRequest;
import com.eRez.user.dto.request.UpdateUserRequest;
import com.eRez.user.dto.response.UserResponse;
import com.eRez.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> getUsers(String callerIdentifier) {
        UserDocument caller = loadCaller(callerIdentifier);
        UserRole targetRole = managedRole(caller.getRole());
        return userRepository.findByRole(targetRole).stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse createUser(String callerIdentifier, CreateUserRequest request) {
        UserDocument caller = loadCaller(callerIdentifier);
        UserRole targetRole = managedRole(caller.getRole());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserException("Email already in use: " + request.getEmail());
        }

        UserDocument user = new UserDocument(
            UUID.randomUUID().toString(),
            request.getUsername(),
            request.getEmail(),
            passwordEncoder.encode(request.getPassword()),
            targetRole);
        userRepository.save(user);
        log.info("User '{}' created as {} by '{}'", request.getEmail(), targetRole, callerIdentifier);
        return toResponse(user);
    }

    public UserResponse getUserById(String callerIdentifier, String targetId) {
        UserDocument caller = loadCaller(callerIdentifier);
        UserDocument target = loadById(targetId);
        assertCanManage(caller, target);
        return toResponse(target);
    }

    public UserResponse updateUser(String callerIdentifier, String targetId, UpdateUserRequest request) {
        UserDocument caller = loadCaller(callerIdentifier);
        UserDocument target = loadById(targetId);
        assertCanManage(caller, target);

        if (request.getPassword() != null && !target.getId().equals(caller.getId())) {
            throw new UserException("Cannot change another user's password");
        }

        applyUpdate(target, request, caller.getId().equals(target.getId()));
        userRepository.save(target);
        log.info("User '{}' updated by '{}'", targetId, callerIdentifier);
        return toResponse(target);
    }

    public void deleteUser(String callerIdentifier, String targetId) {
        UserDocument caller = loadCaller(callerIdentifier);
        UserDocument target = loadById(targetId);
        assertCanManage(caller, target);
        userRepository.delete(target);
        log.info("User '{}' deleted by '{}'", targetId, callerIdentifier);
    }

    public UserResponse getSelf(String callerIdentifier) {
        return toResponse(loadCaller(callerIdentifier));
    }

    public UserResponse updateSelf(String callerIdentifier, UpdateUserRequest request) {
        UserDocument caller = loadCaller(callerIdentifier);
        applyUpdate(caller, request, true);
        userRepository.save(caller);
        log.info("User '{}' updated themselves", callerIdentifier);
        return toResponse(caller);
    }

    private UserDocument loadCaller(String identifier) {
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new UserException("Caller not found: " + identifier));
    }

    private UserDocument loadById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserException("User not found: " + id));
    }

    private void assertCanManage(UserDocument caller, UserDocument target) {
        UserRole callerRole = caller.getRole();
        UserRole targetRole = target.getRole();

        boolean isSelf = caller.getId().equals(target.getId());
        boolean canManage = (callerRole == UserRole.ADMIN && targetRole == UserRole.MANAGER)
                || (callerRole == UserRole.MANAGER && targetRole == UserRole.REGULAR)
                || isSelf;

        if (!canManage) {
            throw new UserException("Access denied");
        }
    }

    private UserRole managedRole(UserRole callerRole) {
        return switch (callerRole) {
            case ADMIN -> UserRole.MANAGER;
            case MANAGER -> UserRole.REGULAR;
            case REGULAR -> throw new UserException("Access denied");
        };
    }

    private void applyUpdate(UserDocument target, UpdateUserRequest request, boolean isSelf) {
        if (request.getUsername() != null) {
            target.setUsername(request.getUsername());
        }
        if (request.getEmail() != null) {
            if (userRepository.existsByEmail(request.getEmail())
                    && !request.getEmail().equals(target.getEmail())) {
                throw new UserException("Email already in use: " + request.getEmail());
            }
            target.setEmail(request.getEmail());
        }
        if (request.getPassword() != null) {
            if (!isSelf) {
                throw new UserException("Cannot change another user's password");
            }
            target.setPassword(passwordEncoder.encode(request.getPassword()));
        }
    }

    private UserResponse toResponse(UserDocument doc) {
        return new UserResponse(doc.getId(), doc.getUsername(), doc.getRole(), doc.getEmail());
    }
}
