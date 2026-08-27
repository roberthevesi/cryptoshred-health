package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.AdminUserRequest;
import com.roberthevesi.cryptoshred_health.dto.AdminUserResponse;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import com.roberthevesi.cryptoshred_health.util.TemporaryPasswordGenerator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin-only endpoint for provisioning staff accounts (DOCTOR, AUDITOR, ADMIN).
 * Public self-registration is disabled; patient accounts are provisioned via clinical registration.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** List all registered staff users (excluding PATIENT accounts). */
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> listUsers() {
        List<AdminUserResponse> users = userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.PATIENT)
                .map(u -> AdminUserResponse.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .createdAt(u.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /** Create a new staff user with role (DOCTOR, AUDITOR, ADMIN). */
    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(@Valid @RequestBody AdminUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        String rawPassword = request.getPassword();
        String temporaryPassword = null;
        if (rawPassword == null || rawPassword.trim().isBlank()) {
            rawPassword = TemporaryPasswordGenerator.generate();
            temporaryPassword = rawPassword;
        }

        User user = new User();
        user.setEmail(request.getEmail().trim());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(request.getRole());
        User saved = userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                AdminUserResponse.builder()
                        .id(saved.getId())
                        .email(saved.getEmail())
                        .role(saved.getRole())
                        .temporaryPassword(temporaryPassword)
                        .createdAt(saved.getCreatedAt())
                        .build());
    }

    /** Delete (permanently remove) a user account by ID. */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }
}
