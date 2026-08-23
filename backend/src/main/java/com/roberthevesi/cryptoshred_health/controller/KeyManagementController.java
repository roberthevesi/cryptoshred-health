package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.KeyRotationRequestDto;
import com.roberthevesi.cryptoshred_health.dto.KeyRotationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.KeyStatusSummaryDto;
import com.roberthevesi.cryptoshred_health.service.KeyManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * KeyManagementController — exposes REST endpoints for cryptographic key lifecycle operations,
 * including zero-plaintext DEK re-wrapping and Vault Transit KEK rotation.
 */
@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class KeyManagementController {

    private final KeyManagementService keyManagementService;

    /**
     * Executes cryptographic key rotation across the specified scope (ALL, PATIENT, VISIT, or KEY).
     * Re-wraps stored DEKs under newly rotated Vault Transit KEK versions without decrypting
     * or exposing clinical ciphertext.
     */
    @PostMapping("/rotate")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('AUDITOR')")
    public ResponseEntity<KeyRotationResponseDto> rotateKeys(
            @RequestBody(required = false) KeyRotationRequestDto request,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String targetId) {

        if (request == null) {
            request = new KeyRotationRequestDto();
        }
        if (scope != null && !scope.isBlank()) {
            request.setScope(scope);
        }
        if (targetId != null && !targetId.isBlank()) {
            request.setTargetId(targetId);
        }

        KeyRotationResponseDto response = keyManagementService.rotateKeys(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Convenience shortcut endpoint to rotate all keys for a specific patient.
     */
    @PostMapping("/patients/{patientId}/rotate")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('AUDITOR')")
    public ResponseEntity<KeyRotationResponseDto> rotatePatientKeys(@PathVariable String patientId) {
        KeyRotationRequestDto request = KeyRotationRequestDto.builder()
                .scope("PATIENT")
                .targetId(patientId)
                .build();
        return ResponseEntity.ok(keyManagementService.rotateKeys(request));
    }

    /**
     * Convenience shortcut endpoint to rotate the key for a specific visit.
     */
    @PostMapping("/visits/{visitId}/rotate")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('AUDITOR')")
    public ResponseEntity<KeyRotationResponseDto> rotateVisitKey(@PathVariable UUID visitId) {
        KeyRotationRequestDto request = KeyRotationRequestDto.builder()
                .scope("VISIT")
                .targetId(visitId.toString())
                .build();
        return ResponseEntity.ok(keyManagementService.rotateKeys(request));
    }

    /**
     * Retrieves high-level metrics on KMS encryption keys.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('AUDITOR')")
    public ResponseEntity<KeyStatusSummaryDto> getKeySummary() {
        return ResponseEntity.ok(keyManagementService.getKeySummary());
    }
}
