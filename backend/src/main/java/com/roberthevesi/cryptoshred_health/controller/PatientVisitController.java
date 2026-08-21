package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.PatientVisitRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitResponse;
import com.roberthevesi.cryptoshred_health.service.PatientVisitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/visits", "/api/records"})
@RequiredArgsConstructor
public class PatientVisitController {

    private final PatientVisitService patientVisitService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PatientVisitResponse> create(
            @Valid @RequestBody PatientVisitRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patientVisitService.create(request, currentUser.getUsername()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'AUDITOR', 'PATIENT')")
    public ResponseEntity<List<PatientVisitResponse>> findAll(
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(patientVisitService.findAll(currentUser.getUsername()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'AUDITOR', 'PATIENT')")
    public ResponseEntity<PatientVisitResponse> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(patientVisitService.findById(id, currentUser.getUsername()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ResponseEntity<PatientVisitResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PatientVisitRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(patientVisitService.update(id, request, currentUser.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails currentUser) {
        patientVisitService.delete(id, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }
}
