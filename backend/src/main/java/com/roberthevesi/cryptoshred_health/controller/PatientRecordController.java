package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.PatientRecordRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientRecordResponse;
import com.roberthevesi.cryptoshred_health.service.PatientRecordService;
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
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class PatientRecordController {

    private final PatientRecordService patientRecordService;

    @GetMapping
    public ResponseEntity<List<PatientRecordResponse>> getAll(
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(patientRecordService.findAll(currentUser.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientRecordResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(patientRecordService.findById(id, currentUser.getUsername()));
    }

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PatientRecordResponse> create(
            @Valid @RequestBody PatientRecordRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        PatientRecordResponse response = patientRecordService.create(request, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PatientRecordResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PatientRecordRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(patientRecordService.update(id, request, currentUser.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails currentUser) {
        patientRecordService.delete(id, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }
}
