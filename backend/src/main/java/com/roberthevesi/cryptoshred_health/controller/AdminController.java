package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.AdminUserRequest;
import com.roberthevesi.cryptoshred_health.dto.AdminUserResponse;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import com.roberthevesi.cryptoshred_health.service.DataPopulationService;
import com.roberthevesi.cryptoshred_health.service.PatientCacheService;
import com.roberthevesi.cryptoshred_health.util.TemporaryPasswordGenerator;
import com.roberthevesi.cryptoshred_health.dto.RetentionPolicyDto;
import com.roberthevesi.cryptoshred_health.service.RetentionPolicyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin-only endpoint for provisioning staff accounts (DOCTOR, AUDITOR, ADMIN),
 * managing statutory data retention policies, and triggering on-demand synthetic clinical data population.
 * Public self-registration is disabled; patient accounts are provisioned via clinical registration.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataPopulationService dataPopulationService;
    private final RetentionPolicyService retentionPolicyService;
    private final PatientCacheService patientCacheService;

    @Autowired
    public AdminController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DataPopulationService dataPopulationService,
            RetentionPolicyService retentionPolicyService,
            @Autowired(required = false) PatientCacheService patientCacheService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataPopulationService = dataPopulationService;
        this.retentionPolicyService = retentionPolicyService;
        this.patientCacheService = patientCacheService;
    }

    public AdminController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DataPopulationService dataPopulationService) {
        this(userRepository, passwordEncoder, dataPopulationService, new RetentionPolicyService(8), null);
    }

    /**
     * Get system-wide statutory retention policy configuration.
     */
    @GetMapping("/retention-policy")
    public ResponseEntity<RetentionPolicyDto> getRetentionPolicy() {
        return ResponseEntity.ok(retentionPolicyService.getRetentionPolicy());
    }

    /**
     * Update statutory retention policy period in years.
     */
    @PutMapping("/retention-policy")
    public ResponseEntity<RetentionPolicyDto> updateRetentionPolicy(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails currentUser) {
        if (body == null || !body.containsKey("retentionPeriodYears")) {
            throw new IllegalArgumentException("Field 'retentionPeriodYears' is required.");
        }
        int years;
        try {
            years = Integer.parseInt(body.get("retentionPeriodYears").toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid retentionPeriodYears value: " + body.get("retentionPeriodYears"));
        }
        String adminUser = currentUser != null ? currentUser.getUsername() : "ADMIN";
        RetentionPolicyDto updated = retentionPolicyService.updateRetentionPolicy(years, adminUser);
        if (patientCacheService != null) {
            patientCacheService.evictAll();
        }
        return ResponseEntity.ok(updated);
    }

    /**
     * Trigger on-demand pre-population of synthetic clinical data (100 patients, 1,000 visits, attachments, 25 shredded records).
     */
    @PostMapping("/seed-data")
    public ResponseEntity<Map<String, Object>> populateSyntheticData() {
        return ResponseEntity.ok(dataPopulationService.populateSyntheticData());
    }

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

    /** Update an existing staff user account (role, email, or password). */
    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        // Check if email changed and is already taken
        if (!user.getEmail().equalsIgnoreCase(request.getEmail().trim())
                && userRepository.existsByEmail(request.getEmail().trim())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        user.setEmail(request.getEmail().trim());
        user.setRole(request.getRole());

        String temporaryPassword = null;
        if (request.getPassword() != null && !request.getPassword().trim().isBlank()) {
            String rawPassword = request.getPassword().trim();
            if ("AUTO_GENERATE".equalsIgnoreCase(rawPassword)) {
                rawPassword = TemporaryPasswordGenerator.generate();
                temporaryPassword = rawPassword;
            }
            user.setPassword(passwordEncoder.encode(rawPassword));
        }

        User saved = userRepository.save(user);

        return ResponseEntity.ok(
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
