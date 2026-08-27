package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.AttachmentResponse;
import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitResponse;
import com.roberthevesi.cryptoshred_health.model.*;
import com.roberthevesi.cryptoshred_health.repository.GpRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class FhirExportIntegrationTest {

    private PatientService patientService;
    private PatientRepository patientRepository;
    private PatientVisitService patientVisitService;
    private PatientVisitRepository patientVisitRepository;
    private GpRepository gpRepository;
    private VaultKmsService vaultKmsService;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private FhirExportService fhirExportService;

    @BeforeEach
    void setUp() {
        patientService = Mockito.mock(PatientService.class);
        patientRepository = Mockito.mock(PatientRepository.class);
        patientVisitService = Mockito.mock(PatientVisitService.class);
        patientVisitRepository = Mockito.mock(PatientVisitRepository.class);
        gpRepository = Mockito.mock(GpRepository.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        envelopeEncryptionService = Mockito.mock(EnvelopeEncryptionService.class);

        fhirExportService = new FhirExportService(
                patientService,
                patientRepository,
                patientVisitService,
                patientVisitRepository,
                gpRepository,
                vaultKmsService,
                envelopeEncryptionService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should export complete valid HL7 FHIR R4 Bundle with all clinical resources and LOINC observations for active patient")
    @SuppressWarnings("unchecked")
    void testExportActivePatientFhirBundle() {
        // Given: An active master patient with assigned GP
        UUID patientUuid = UUID.randomUUID();
        String patientId = "PAT-10001";

        GP gp = new GP();
        gp.setId(UUID.randomUUID());
        gp.setFirstName("Alistair");
        gp.setLastName("Finch");
        gp.setGmcNumber("7123456");
        gp.setEmail("finch@nhs.net");
        gp.setPhoneNumber("+44 20 7946 0123");
        gp.setPracticeName("St. Thomas Health Centre");

        Patient patient = new Patient();
        patient.setId(patientUuid);
        patient.setPatientId(patientId);
        patient.setFirstName("John");
        patient.setLastName("Smith");
        patient.setDateOfBirth(LocalDate.of(1985, 5, 12));
        patient.setGender("Male");
        patient.setEmail("john.smith@example.com");
        patient.setPhoneNumber("+44 7700 900077");
        patient.setAddress("10 Downing Street, London, SW1A 2AA");
        patient.setNhsNumber("943 476 5919");
        patient.setGp(gp);
        patient.setActive(true);
        patient.setShredded(false);
        patient.setCreatedAt(LocalDateTime.now().minusDays(10));
        patient.setUpdatedAt(LocalDateTime.now().minusDays(1));

        PatientResponse patientResponse = PatientResponse.builder()
                .id(patientUuid)
                .patientId(patientId)
                .firstName("John")
                .lastName("Smith")
                .dateOfBirth(LocalDate.of(1985, 5, 12))
                .gender("Male")
                .email("john.smith@example.com")
                .phoneNumber("+44 7700 900077")
                .address("10 Downing Street, London, SW1A 2AA")
                .nhsNumber("943 476 5919")
                .gp(GpResponse.builder()
                        .id(gp.getId())
                        .firstName(gp.getFirstName())
                        .lastName(gp.getLastName())
                        .gmcNumber(gp.getGmcNumber())
                        .email(gp.getEmail())
                        .phoneNumber(gp.getPhoneNumber())
                        .practiceName(gp.getPracticeName())
                        .build())
                .isActive(true)
                .shredded(false)
                .build();

        // Clinical visit with full vitals and attachments
        UUID visitUuid = UUID.randomUUID();
        PatientVisit visit = new PatientVisit();
        visit.setId(visitUuid);
        visit.setPatient(patient);
        visit.setPatientName("John Smith");
        visit.setMrn(patientId);
        visit.setCreatedAt(LocalDateTime.now().minusDays(2));

        UUID attUuid = UUID.randomUUID();
        AttachmentResponse attResp = AttachmentResponse.builder()
                .id(attUuid)
                .fileName("ecg-report.pdf")
                .contentType("application/pdf")
                .fileSize(102400L)
                .shredded(false)
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();

        PatientVisitResponse visitResponse = PatientVisitResponse.builder()
                .id(visitUuid)
                .patientId(patientId)
                .patientName("John Smith")
                .mrn(patientId)
                .bloodPressure("135/85")
                .heartRate(72)
                .respiratoryRate("16")
                .temperature("36.8")
                .oxygenSaturation("99")
                .heightCm("180")
                .weightKg("82")
                .bmi("25.3")
                .painScore(0)
                .diagnosis("Essential Hypertension")
                .chiefComplaint("Elevated blood pressure during annual check")
                .medicalNotes("Patient advised on sodium reduction and regular exercise")
                .allergies("Penicillin")
                .prescriptions("Amlodipine 5mg once daily")
                .attendingDoctor("Dr. Alistair Finch")
                .department("Cardiology Outpatients")
                .attachments(List.of(attResp))
                .shredded(false)
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();

        when(patientRepository.findByPatientId(patientId)).thenReturn(Optional.of(patient));
        when(patientVisitRepository.findByPatientIdentifier(patientId)).thenReturn(List.of(visit));
        when(patientService.toResponse(patient)).thenReturn(patientResponse);
        when(patientVisitService.toResponse(visit)).thenReturn(visitResponse);

        // When: Generating FHIR R4 export
        Map<String, Object> bundle = fhirExportService.exportPatientFhirR4(patientId);

        // Then: Validate Bundle root structure
        assertNotNull(bundle);
        assertEquals("Bundle", bundle.get("resourceType"));
        assertEquals("collection", bundle.get("type"));
        assertNotNull(bundle.get("timestamp"));
        assertTrue(((String) bundle.get("id")).startsWith("bundle-"));

        List<Map<String, Object>> entries = (List<Map<String, Object>>) bundle.get("entry");
        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        // Extract resources by type
        Map<String, Map<String, Object>> resourcesByType = new HashMap<>();
        List<Map<String, Object>> observations = new ArrayList<>();
        List<Map<String, Object>> documents = new ArrayList<>();

        for (Map<String, Object> entry : entries) {
            Map<String, Object> res = (Map<String, Object>) entry.get("resource");
            String type = (String) res.get("resourceType");
            if ("Observation".equals(type)) {
                observations.add(res);
            } else if ("DocumentReference".equals(type)) {
                documents.add(res);
            } else {
                resourcesByType.put(type, res);
            }
        }

        // 1. Validate Patient Resource
        assertTrue(resourcesByType.containsKey("Patient"));
        Map<String, Object> patientRes = resourcesByType.get("Patient");
        assertEquals("Patient", patientRes.get("resourceType"));
        assertEquals(patientId, patientRes.get("id"));
        assertEquals(true, patientRes.get("active"));
        assertEquals("male", patientRes.get("gender"));
        assertEquals("1985-05-12", patientRes.get("birthDate"));

        List<Map<String, Object>> names = (List<Map<String, Object>>) patientRes.get("name");
        assertEquals("Smith", names.get(0).get("family"));
        assertEquals(List.of("John"), names.get(0).get("given"));

        List<Map<String, Object>> identifiers = (List<Map<String, Object>>) patientRes.get("identifier");
        boolean hasNhs = identifiers.stream().anyMatch(i -> "https://fhir.nhs.uk/Id/nhs-number".equals(i.get("system")) && "943 476 5919".equals(i.get("value")));
        assertTrue(hasNhs, "FHIR Patient must contain NHS Number identifier");

        // 2. Validate Practitioner Resource
        assertTrue(resourcesByType.containsKey("Practitioner"));
        Map<String, Object> gpRes = resourcesByType.get("Practitioner");
        assertEquals(gp.getId().toString(), gpRes.get("id"));
        List<Map<String, Object>> gpNames = (List<Map<String, Object>>) gpRes.get("name");
        assertEquals("Finch", gpNames.get(0).get("family"));
        assertEquals(List.of("Alistair"), gpNames.get(0).get("given"));

        // 3. Validate Encounter Resource
        assertTrue(resourcesByType.containsKey("Encounter"));
        Map<String, Object> encounterRes = resourcesByType.get("Encounter");
        assertEquals(visitUuid.toString(), encounterRes.get("id"));
        assertEquals("finished", encounterRes.get("status"));
        assertEquals("Cardiology Outpatients", ((Map<String, Object>) encounterRes.get("serviceProvider")).get("display"));

        // 4. Validate Condition Resource
        assertTrue(resourcesByType.containsKey("Condition"));
        Map<String, Object> conditionRes = resourcesByType.get("Condition");
        assertEquals("Essential Hypertension", ((Map<String, Object>) conditionRes.get("code")).get("text"));

        // 5. Validate Observations (LOINC codes)
        assertEquals(9, observations.size(), "Should produce 9 vital observations");

        // Check Blood Pressure Panel (LOINC 85354-9)
        Map<String, Object> bpObs = observations.stream()
                .filter(o -> ((String) o.get("id")).startsWith("obs-bp-"))
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> bpComponents = (List<Map<String, Object>>) bpObs.get("component");
        assertEquals(2, bpComponents.size());
        assertEquals(135.0, ((Map<String, Object>) bpComponents.get(0).get("valueQuantity")).get("value"));
        assertEquals(85.0, ((Map<String, Object>) bpComponents.get(1).get("valueQuantity")).get("value"));

        // Check Heart Rate (LOINC 8867-4)
        Map<String, Object> hrObs = observations.stream()
                .filter(o -> ((String) o.get("id")).startsWith("obs-hr-"))
                .findFirst()
                .orElseThrow();
        assertEquals(72.0, ((Map<String, Object>) hrObs.get("valueQuantity")).get("value"));

        // Check Temperature (LOINC 8310-5)
        Map<String, Object> tempObs = observations.stream()
                .filter(o -> ((String) o.get("id")).startsWith("obs-temp-"))
                .findFirst()
                .orElseThrow();
        assertEquals(36.8, ((Map<String, Object>) tempObs.get("valueQuantity")).get("value"));

        // Check Oxygen Saturation (LOINC 2708-6)
        Map<String, Object> spo2Obs = observations.stream()
                .filter(o -> ((String) o.get("id")).startsWith("obs-spo2-"))
                .findFirst()
                .orElseThrow();
        assertEquals(99.0, ((Map<String, Object>) spo2Obs.get("valueQuantity")).get("value"));

        // 6. Validate AllergyIntolerance
        assertTrue(resourcesByType.containsKey("AllergyIntolerance"));
        Map<String, Object> allergyRes = resourcesByType.get("AllergyIntolerance");
        assertEquals("Penicillin", ((Map<String, Object>) allergyRes.get("code")).get("text"));

        // 7. Validate MedicationStatement
        assertTrue(resourcesByType.containsKey("MedicationStatement"));
        Map<String, Object> medRes = resourcesByType.get("MedicationStatement");
        assertEquals("Amlodipine 5mg once daily", ((Map<String, Object>) medRes.get("medicationCodeableConcept")).get("text"));

        // 8. Validate DocumentReference
        assertEquals(1, documents.size());
        Map<String, Object> docRes = documents.get(0);
        List<Map<String, Object>> contents = (List<Map<String, Object>>) docRes.get("content");
        Map<String, Object> attMap = (Map<String, Object>) contents.get(0).get("attachment");
        assertEquals("ecg-report.pdf", attMap.get("title"));
        assertEquals("application/pdf", attMap.get("contentType"));
    }

    @Test
    @DisplayName("Should export crypto-shredded FHIR R4 Bundle with zero PII leaks and CRYPTO_SHREDDED tags")
    @SuppressWarnings("unchecked")
    void testExportCryptoShreddedPatientFhirBundle() {
        // Given: A shredded patient profile
        String patientId = "PAT-SHREDDED-99";
        UUID patientUuid = UUID.randomUUID();

        Patient shreddedPatient = new Patient();
        shreddedPatient.setId(patientUuid);
        shreddedPatient.setPatientId(patientId);
        shreddedPatient.setShredded(true);
        shreddedPatient.setActive(false);

        PatientResponse redactedResponse = PatientResponse.builder()
                .id(patientUuid)
                .patientId(patientId)
                .firstName("[SHREDDED]")
                .lastName("[SHREDDED]")
                .dateOfBirth(null)
                .gender("[SHREDDED]")
                .email(null)
                .phoneNumber(null)
                .address(null)
                .nhsNumber(null)
                .isActive(false)
                .shredded(true)
                .build();

        UUID visitUuid = UUID.randomUUID();
        PatientVisit shreddedVisit = new PatientVisit();
        shreddedVisit.setId(visitUuid);
        shreddedVisit.setPatient(shreddedPatient);
        shreddedVisit.setShredded(true);

        PatientVisitResponse redactedVisitResponse = PatientVisitResponse.builder()
                .id(visitUuid)
                .patientId(patientId)
                .patientName("[SHREDDED]")
                .diagnosis("[SHREDDED]")
                .medicalNotes("[SHREDDED]")
                .allergies("[SHREDDED]")
                .prescriptions("[SHREDDED]")
                .bloodPressure("[SHREDDED]")
                .heartRate(null)
                .shredded(true)
                .build();

        when(patientRepository.findByPatientId(patientId)).thenReturn(Optional.of(shreddedPatient));
        when(patientVisitRepository.findByPatientIdentifier(patientId)).thenReturn(List.of(shreddedVisit));
        when(patientService.toResponse(shreddedPatient)).thenReturn(redactedResponse);
        when(patientVisitService.toResponse(shreddedVisit)).thenReturn(redactedVisitResponse);

        // When: Generating FHIR R4 export
        Map<String, Object> bundle = fhirExportService.exportPatientFhirR4(patientId);

        // Then: Bundle and Patient resources must carry CRYPTO_SHREDDED tags and sanitized values
        assertNotNull(bundle);
        Map<String, Object> bundleMeta = (Map<String, Object>) bundle.get("meta");
        assertNotNull(bundleMeta.get("tag"));
        List<Map<String, Object>> bundleTags = (List<Map<String, Object>>) bundleMeta.get("tag");
        assertEquals("CRYPTO_SHREDDED", bundleTags.get(0).get("code"));

        List<Map<String, Object>> entries = (List<Map<String, Object>>) bundle.get("entry");
        assertFalse(entries.isEmpty());

        Map<String, Object> patientRes = (Map<String, Object>) entries.get(0).get("resource");
        assertEquals("Patient", patientRes.get("resourceType"));
        assertEquals(false, patientRes.get("active"));
        assertEquals("unknown", patientRes.get("gender"));
        assertNull(patientRes.get("birthDate"));
        assertNull(patientRes.get("telecom"));
        assertNull(patientRes.get("address"));

        List<Map<String, Object>> names = (List<Map<String, Object>>) patientRes.get("name");
        assertEquals("[SHREDDED]", names.get(0).get("family"));
        assertEquals(List.of("[SHREDDED]"), names.get(0).get("given"));

        // Confirm NHS number is omitted from shredded bundle
        List<Map<String, Object>> identifiers = (List<Map<String, Object>>) patientRes.get("identifier");
        boolean hasNhs = identifiers.stream().anyMatch(i -> "https://fhir.nhs.uk/Id/nhs-number".equals(i.get("system")));
        assertFalse(hasNhs, "Shredded patient bundle must NOT contain NHS number");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when patient does not exist")
    void testPatientNotFoundThrowsException() {
        when(patientRepository.findByPatientId("PAT-NONEXISTENT")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            fhirExportService.exportPatientFhirR4("PAT-NONEXISTENT");
        });
    }
}
