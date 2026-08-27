package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.GpRequest;
import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.service.GpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gps")
@RequiredArgsConstructor
public class GpController {

    private final GpService gpService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<GpResponse>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "true") boolean includeInactive) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(gpService.search(search));
        }
        return ResponseEntity.ok(gpService.findAll(includeInactive));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<GpResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(gpService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GpResponse> create(@Valid @RequestBody GpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gpService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GpResponse> update(@PathVariable UUID id, @Valid @RequestBody GpRequest request) {
        return ResponseEntity.ok(gpService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePermanently(@PathVariable UUID id) {
        gpService.deletePermanently(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivatePatch(@PathVariable UUID id) {
        gpService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivatePut(@PathVariable UUID id) {
        gpService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GpResponse> reactivatePatch(@PathVariable UUID id) {
        return ResponseEntity.ok(gpService.reactivate(id));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GpResponse> reactivatePut(@PathVariable UUID id) {
        return ResponseEntity.ok(gpService.reactivate(id));
    }
}
