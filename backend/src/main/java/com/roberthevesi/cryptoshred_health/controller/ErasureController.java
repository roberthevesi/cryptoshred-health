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
     * Triggers the Right-to-be-Forgotten workflow for the specified patient record.
     * Restricted to users with the AUDITOR role.
     *
     * @param patientRecordId the UUID of the record to be crypto-shredded
     * @param currentUser     the authenticated auditor
     * @return a signed {@link VerifiableDeletionProofDto} for compliance records
     */
    @DeleteMapping("/{patientRecordId}/forget")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<VerifiableDeletionProofDto> forgetPatient(
            @PathVariable UUID patientRecordId,
            @AuthenticationPrincipal UserDetails currentUser) {
        VerifiableDeletionProofDto proof =
                erasureService.forgetPatient(patientRecordId, currentUser.getUsername());
        return ResponseEntity.ok(proof);
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
