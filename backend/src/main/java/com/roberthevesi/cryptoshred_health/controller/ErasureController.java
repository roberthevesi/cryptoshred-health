package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.DeletionProofResponse;
import com.roberthevesi.cryptoshred_health.service.ErasureService;
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

    /**
     * Triggers the Right-to-be-Forgotten workflow for the specified patient record.
     * Restricted to users with the AUDITOR role.
     *
     * @param patientRecordId the UUID of the record to be crypto-shredded
     * @param currentUser     the authenticated auditor
     * @return a signed {@link DeletionProofResponse} for compliance records
     */
    @DeleteMapping("/{patientRecordId}/forget")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<DeletionProofResponse> forgetPatient(
            @PathVariable UUID patientRecordId,
            @AuthenticationPrincipal UserDetails currentUser) {
        DeletionProofResponse proof =
                erasureService.forgetPatient(patientRecordId, currentUser.getUsername());
        return ResponseEntity.ok(proof);
    }
}
