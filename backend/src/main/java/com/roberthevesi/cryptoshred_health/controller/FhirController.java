package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.service.FhirExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller exposing HL7 FHIR R4 interoperability export endpoints for clinical EHR records.
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "HL7 FHIR Interoperability", description = "Endpoints for HL7 FHIR R4 clinical data interoperability, bundle generation, and EHR export")
public class FhirController {

    private final FhirExportService fhirExportService;

    /**
     * Exports a complete, interoperable HL7 FHIR R4 collection Bundle for the given patient.
     * Contains Patient, Practitioner, Encounter, Condition, Observation (with LOINC codes),
     * AllergyIntolerance, MedicationStatement, and DocumentReference resources.
     */
    @GetMapping(value = "/{patientId}/fhir", produces = {"application/fhir+json", "application/json"})
    @PreAuthorize("hasAnyRole('DOCTOR', 'AUDITOR', 'ADMIN', 'PATIENT')")
    @Operation(
            summary = "Export Patient FHIR R4 Bundle",
            description = "Generates and exports an HL7 FHIR R4 collection Bundle containing all decrypted clinical records, telemetry observations, and encounter metadata for the specified patient."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully generated FHIR R4 bundle",
                    content = @Content(mediaType = "application/fhir+json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "404", description = "Patient record not found")
    })
    public ResponseEntity<Map<String, Object>> exportPatientFhirR4(@PathVariable String patientId) {
        Map<String, Object> bundle = fhirExportService.exportPatientFhirR4(patientId);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + patientId + "-fhir-r4.json\"");
        headers.setContentType(MediaType.parseMediaType("application/fhir+json"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(bundle);
    }
}
