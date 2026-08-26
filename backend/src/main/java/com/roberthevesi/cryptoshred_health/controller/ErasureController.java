package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.ProofVerificationRequestDto;
import com.roberthevesi.cryptoshred_health.dto.ProofVerificationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import com.roberthevesi.cryptoshred_health.service.ErasureService;
import com.roberthevesi.cryptoshred_health.service.ProofSigningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/erasure")
@RequiredArgsConstructor
public class ErasureController {

    private final ErasureService erasureService;
    private final ProofSigningService proofSigningService;

    /**
     * Complete Patient Right-to-be-Forgotten:
     * Destroys the patient's master demographic Vault KEK, shreds all linked clinical visits and attachments.
     * Restricted to users with the AUDITOR role.
     */
    @DeleteMapping("/patients/{patientId}/forget")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<VerifiableDeletionProofDto> forgetPatient(
            @PathVariable String patientId,
            @AuthenticationPrincipal UserDetails currentUser) {
        VerifiableDeletionProofDto proof =
                erasureService.forgetPatient(patientId, currentUser.getUsername());
        return ResponseEntity.ok(proof);
    }

    /**
     * Individual Visit Right-to-be-Forgotten:
     * Destroys the visit's Vault KEK, nullifies clinical payload and attachments.
     * Restricted to users with the AUDITOR role.
     */
    @DeleteMapping({"/visits/{visitId}/forget", "/records/{visitId}/forget", "/{visitId}/forget"})
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<VerifiableDeletionProofDto> forgetVisit(
            @PathVariable UUID visitId,
            @AuthenticationPrincipal UserDetails currentUser) {
        VerifiableDeletionProofDto proof =
                erasureService.forgetVisit(visitId, currentUser.getUsername());
        return ResponseEntity.ok(proof);
    }

    @GetMapping("/patients/{patientId}/proof")
    @PreAuthorize("hasAnyRole('DOCTOR', 'AUDITOR', 'ADMIN', 'PATIENT')")
    public ResponseEntity<VerifiableDeletionProofDto> getPatientProof(@PathVariable String patientId) {
        return ResponseEntity.ok(erasureService.getPatientDeletionProof(patientId));
    }

    @GetMapping("/visits/{visitId}/proof")
    @PreAuthorize("hasAnyRole('DOCTOR', 'AUDITOR', 'ADMIN', 'PATIENT')")
    public ResponseEntity<VerifiableDeletionProofDto> getVisitProof(@PathVariable UUID visitId) {
        return ResponseEntity.ok(erasureService.getVisitDeletionProof(visitId));
    }

    /**
     * Verifies a submitted {@link VerifiableDeletionProofDto} artifact against the system's RSA public key and Merkle tree.
     */
    @PostMapping("/verify-proof")
    public ResponseEntity<ProofVerificationResponseDto> verifyProof(
            @RequestBody ProofVerificationRequestDto request) {
        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(request.getProofArtifact());
        return ResponseEntity.ok(response);
    }

    /**
     * Exposes the system RSA public key in PEM format for independent offline verification.
     */
    @GetMapping("/public-key")
    public ResponseEntity<String> getPublicKeyPem() {
        return ResponseEntity.ok(proofSigningService.getPublicKeyPem());
    }
}
