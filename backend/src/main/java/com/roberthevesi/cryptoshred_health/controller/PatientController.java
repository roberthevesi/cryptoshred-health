package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PreAuthorize("hasAnyRole('DOCTOR', 'AUDITOR')")
    @GetMapping
    public ResponseEntity<List<PatientResponse>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID gpId,
            @RequestParam(required = false, defaultValue = "true") boolean includeDeleted) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(patientService.search(search));
        }
        if (gpId != null) {
            return ResponseEntity.ok(patientService.findByGp(gpId));
        }
        return ResponseEntity.ok(patientService.findAll(includeDeleted));
    }

    @PreAuthorize("hasAnyRole('DOCTOR', 'AUDITOR')")
    @GetMapping("/{patientId}")
    public ResponseEntity<PatientResponse> getById(@PathVariable String patientId) {
        return ResponseEntity.ok(patientService.findByPatientId(patientId));
    }

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(patientService.create(request));
    }

    @PutMapping("/{patientId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PatientResponse> update(@PathVariable String patientId, @Valid @RequestBody PatientRequest request) {
        return ResponseEntity.ok(patientService.update(patientId, request));
    }

    @DeleteMapping("/{patientId}")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<Void> delete(@PathVariable String patientId) {
        patientService.deactivate(patientId);
        return ResponseEntity.noContent().build();
    }
}
