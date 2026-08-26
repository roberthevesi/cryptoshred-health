package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.AttachmentResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitResponse;
import com.roberthevesi.cryptoshred_health.model.GP;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.GpRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for constructing HL7 FHIR R4 compliant Bundles from CryptoShred Health EHR records.
 * Adheres to UK Core FHIR R4 profiles and handles crypto-shredded redactions cleanly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FhirExportService {

    private final PatientService patientService;
    private final PatientRepository patientRepository;
    private final PatientVisitService patientVisitService;
    private final PatientVisitRepository patientVisitRepository;
    private final GpRepository gpRepository;
    private final VaultKmsService vaultKmsService;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final ObjectMapper objectMapper;

    private static final Map<String, Object> CRYPTO_SHREDDED_TAG = Map.of(
            "system", "http://terminology.hl7.org/CodeSystem/v3-ObservationValue",
            "code", "CRYPTO_SHREDDED",
            "display", "Crypto-Shredded under GDPR Art. 17"
    );

    @Transactional(readOnly = true)
    public Map<String, Object> exportPatientFhirR4(String patientId) {
        log.info("Generating HL7 FHIR R4 bundle export for patient: {}", patientId);

        // 1. Fetch Patient entity
        Patient patient = patientRepository.findByPatientId(patientId)
                .or(() -> {
                    try {
                        UUID uuid = UUID.fromString(patientId);
                        return patientRepository.findById(uuid);
                    } catch (IllegalArgumentException e) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));

        // 2. Fetch associated clinical visits
        List<PatientVisit> visits = new ArrayList<>(patientVisitRepository.findByPatientIdentifier(patient.getPatientId()));
        if (visits.isEmpty() && patient.getId() != null) {
            visits = patientVisitRepository.findAll().stream()
                    .filter(v -> (v.getPatient() != null && v.getPatient().getId().equals(patient.getId()))
                            || (v.getMrn() != null && v.getMrn().equals(patient.getPatientId())))
                    .collect(Collectors.toList());
        }
        visits.sort(Comparator.comparing(PatientVisit::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        // 3. Resolve decrypted / redacted patient demographic response
        PatientResponse pResp = patientService.toResponse(patient);
        boolean isPatientShredded = pResp.isShredded() || patient.isShredded();

        // 4. Initialize FHIR R4 Bundle
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", "bundle-" + UUID.randomUUID());
        bundle.put("type", "collection");
        bundle.put("timestamp", Instant.now().toString());

        Map<String, Object> bundleMeta = new LinkedHashMap<>();
        bundleMeta.put("lastUpdated", Instant.now().toString());
        bundleMeta.put("profile", List.of("http://hl7.org/fhir/StructureDefinition/Bundle"));
        if (isPatientShredded) {
            bundleMeta.put("tag", List.of(CRYPTO_SHREDDED_TAG));
        }
        bundle.put("meta", bundleMeta);

        List<Map<String, Object>> entries = new ArrayList<>();

        // 5. Add Patient Resource Entry
        Map<String, Object> patientResource = buildPatientResource(patient, pResp, isPatientShredded);
        addBundleEntry(entries, "Patient/" + patient.getPatientId(), patientResource);

        // 6. Add Practitioner Resource Entry (if GP is assigned)
        if (patient.getGp() != null) {
            Map<String, Object> practitionerResource = buildPractitionerResource(patient.getGp());
            addBundleEntry(entries, "Practitioner/" + patient.getGp().getId(), practitionerResource);
        }

        // 7. Add Encounters, Conditions, Observations, Allergies, Meds, and Documents per Visit
        for (PatientVisit visit : visits) {
            PatientVisitResponse vResp = patientVisitService.toResponse(visit);
            boolean isVisitShredded = isPatientShredded || vResp.isShredded() || visit.isShredded();

            // Encounter
            Map<String, Object> encounterResource = buildEncounterResource(visit, vResp, patient, pResp, isVisitShredded, isPatientShredded);
            addBundleEntry(entries, "Encounter/" + visit.getId(), encounterResource);

            // Condition (Diagnosis & Chronic Conditions)
            buildConditionResource(visit, vResp, patient, isVisitShredded)
                    .ifPresent(cond -> addBundleEntry(entries, "Condition/" + cond.get("id"), cond));

            // Observations (Vitals with standard LOINC codes)
            List<Map<String, Object>> observationResources = buildObservationResources(visit, vResp, patient, isVisitShredded);
            for (Map<String, Object> obs : observationResources) {
                addBundleEntry(entries, "Observation/" + obs.get("id"), obs);
            }

            // AllergyIntolerance
            buildAllergyResource(visit, vResp, patient, isVisitShredded)
                    .ifPresent(allergy -> addBundleEntry(entries, "AllergyIntolerance/" + allergy.get("id"), allergy));

            // MedicationStatement
            buildMedicationResource(visit, vResp, patient, isVisitShredded)
                    .ifPresent(med -> addBundleEntry(entries, "MedicationStatement/" + med.get("id"), med));

            // DocumentReference (Attachments)
            List<Map<String, Object>> docResources = buildDocumentReferenceResources(visit, vResp, patient, isVisitShredded);
            for (Map<String, Object> doc : docResources) {
                addBundleEntry(entries, "DocumentReference/" + doc.get("id"), doc);
            }
        }

        bundle.put("entry", entries);
        bundle.put("total", entries.size());

        return bundle;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportPatientFhirR4(UUID patientUuid) {
        return exportPatientFhirR4(patientUuid.toString());
    }

    private Map<String, Object> buildPatientResource(Patient patient, PatientResponse pResp, boolean isShredded) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Patient");
        resource.put("id", patient.getPatientId());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("profile", List.of(
                "http://hl7.org/fhir/StructureDefinition/Patient",
                "https://fhir.hl7.org.uk/StructureDefinition/UKCore-Patient"
        ));
        if (patient.getUpdatedAt() != null) {
            meta.put("lastUpdated", patient.getUpdatedAt().toString());
        }
        if (isShredded) {
            meta.put("tag", List.of(CRYPTO_SHREDDED_TAG));
        }
        resource.put("meta", meta);

        // Identifiers
        List<Map<String, Object>> identifiers = new ArrayList<>();
        if (!isShredded && pResp.getNhsNumber() != null && !pResp.getNhsNumber().isBlank()) {
            identifiers.add(Map.of(
                    "use", "official",
                    "system", "https://fhir.nhs.uk/Id/nhs-number",
                    "value", pResp.getNhsNumber()
            ));
        }
        identifiers.add(Map.of(
                "use", "secondary",
                "system", "urn:oid:cryptoshred-health:patient-id",
                "value", patient.getPatientId()
        ));
        resource.put("identifier", identifiers);

        resource.put("active", pResp.isActive());

        // Name
        List<Map<String, Object>> nameList = new ArrayList<>();
        Map<String, Object> nameMap = new LinkedHashMap<>();
        nameMap.put("use", "official");
        nameMap.put("family", isShredded ? "[SHREDDED]" : (pResp.getLastName() != null ? pResp.getLastName() : ""));
        nameMap.put("given", List.of(isShredded ? "[SHREDDED]" : (pResp.getFirstName() != null ? pResp.getFirstName() : "")));
        nameList.add(nameMap);
        resource.put("name", nameList);

        // Gender & DOB
        resource.put("gender", isShredded ? "unknown" : mapGender(pResp.getGender()));
        if (!isShredded && pResp.getDateOfBirth() != null) {
            resource.put("birthDate", pResp.getDateOfBirth().toString());
        }

        // Telecom (Phone & Email)
        if (!isShredded) {
            List<Map<String, Object>> telecom = new ArrayList<>();
            if (pResp.getPhoneNumber() != null && !pResp.getPhoneNumber().isBlank()) {
                telecom.add(Map.of("system", "phone", "value", pResp.getPhoneNumber(), "use", "home"));
            }
            if (pResp.getEmail() != null && !pResp.getEmail().isBlank()) {
                telecom.add(Map.of("system", "email", "value", pResp.getEmail(), "use", "home"));
            }
            if (!telecom.isEmpty()) {
                resource.put("telecom", telecom);
            }
        }

        // Address
        if (!isShredded && pResp.getAddress() != null && !pResp.getAddress().isBlank()) {
            resource.put("address", List.of(Map.of(
                    "use", "home",
                    "line", List.of(pResp.getAddress()),
                    "text", pResp.getAddress()
            )));
        }

        // GP Reference
        if (patient.getGp() != null) {
            resource.put("generalPractitioner", List.of(Map.of(
                    "reference", "Practitioner/" + patient.getGp().getId().toString(),
                    "display", "Dr. " + patient.getGp().getFirstName() + " " + patient.getGp().getLastName()
            )));
        }

        return resource;
    }

    private Map<String, Object> buildPractitionerResource(GP gp) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Practitioner");
        resource.put("id", gp.getId().toString());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("profile", List.of("https://fhir.hl7.org.uk/StructureDefinition/UKCore-Practitioner"));
        resource.put("meta", meta);

        if (gp.getGmcNumber() != null && !gp.getGmcNumber().isBlank()) {
            resource.put("identifier", List.of(Map.of(
                    "system", "https://fhir.hl7.org.uk/Id/gmc-number",
                    "value", gp.getGmcNumber()
            )));
        }

        resource.put("name", List.of(Map.of(
                "family", gp.getLastName() != null ? gp.getLastName() : "",
                "given", List.of(gp.getFirstName() != null ? gp.getFirstName() : ""),
                "prefix", List.of("Dr.")
        )));

        List<Map<String, Object>> telecom = new ArrayList<>();
        if (gp.getPhoneNumber() != null && !gp.getPhoneNumber().isBlank()) {
            telecom.add(Map.of("system", "phone", "value", gp.getPhoneNumber(), "use", "work"));
        }
        if (gp.getEmail() != null && !gp.getEmail().isBlank()) {
            telecom.add(Map.of("system", "email", "value", gp.getEmail(), "use", "work"));
        }
        if (!telecom.isEmpty()) {
            resource.put("telecom", telecom);
        }

        return resource;
    }

    private Map<String, Object> buildEncounterResource(PatientVisit visit, PatientVisitResponse vResp,
                                                       Patient patient, PatientResponse pResp,
                                                       boolean isVisitShredded, boolean isPatientShredded) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Encounter");
        resource.put("id", visit.getId().toString());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("profile", List.of("https://fhir.hl7.org.uk/StructureDefinition/UKCore-Encounter"));
        if (isVisitShredded) {
            meta.put("tag", List.of(CRYPTO_SHREDDED_TAG));
        }
        resource.put("meta", meta);

        resource.put("status", "finished");
        resource.put("class", Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                "code", "AMB",
                "display", "ambulatory"
        ));

        resource.put("subject", Map.of(
                "reference", "Patient/" + patient.getPatientId(),
                "display", isPatientShredded ? "[SHREDDED]" : (pResp.getFirstName() + " " + pResp.getLastName())
        ));

        String startTime = visit.getCreatedAt() != null ? visit.getCreatedAt().toString() : Instant.now().toString();
        resource.put("period", Map.of("start", startTime));

        String dept = (vResp.getDepartment() != null && !vResp.getDepartment().isBlank()) ? vResp.getDepartment() : "General Practice";
        resource.put("serviceProvider", Map.of(
                "display", isVisitShredded ? "[SHREDDED]" : dept
        ));

        if (vResp.getChiefComplaint() != null && !vResp.getChiefComplaint().isBlank()) {
            resource.put("reasonCode", List.of(Map.of("text", vResp.getChiefComplaint())));
        }

        if (vResp.getAttendingDoctor() != null && !vResp.getAttendingDoctor().isBlank()) {
            resource.put("participant", List.of(Map.of(
                    "individual", Map.of("display", isVisitShredded ? "[SHREDDED]" : vResp.getAttendingDoctor())
            )));
        }

        return resource;
    }

    private Optional<Map<String, Object>> buildConditionResource(PatientVisit visit, PatientVisitResponse vResp,
                                                                 Patient patient, boolean isVisitShredded) {
        String diagnosis = vResp.getDiagnosis();
        String chronic = vResp.getChronicConditions();

        if ((diagnosis == null || diagnosis.isBlank()) && (chronic == null || chronic.isBlank())) {
            return Optional.empty();
        }

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Condition");
        resource.put("id", "condition-" + visit.getId());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("profile", List.of("https://fhir.hl7.org.uk/StructureDefinition/UKCore-Condition"));
        if (isVisitShredded) {
            meta.put("tag", List.of(CRYPTO_SHREDDED_TAG));
        }
        resource.put("meta", meta);

        resource.put("clinicalStatus", Map.of(
                "coding", List.of(Map.of(
                        "system", "http://terminology.hl7.org/CodeSystem/condition-clinical",
                        "code", isVisitShredded ? "inactive" : "active"
                ))
        ));

        resource.put("subject", Map.of("reference", "Patient/" + patient.getPatientId()));
        resource.put("encounter", Map.of("reference", "Encounter/" + visit.getId()));

        String condText = (diagnosis != null && !diagnosis.isBlank()) ? diagnosis : chronic;
        resource.put("code", Map.of("text", condText));

        List<Map<String, Object>> notes = new ArrayList<>();
        if (vResp.getMedicalNotes() != null && !vResp.getMedicalNotes().isBlank()) {
            notes.add(Map.of("text", vResp.getMedicalNotes()));
        }
        if (vResp.getSoapAssessment() != null && !vResp.getSoapAssessment().isBlank() && !vResp.getSoapAssessment().equals("[SHREDDED]")) {
            notes.add(Map.of("text", "SOAP Assessment: " + vResp.getSoapAssessment()));
        }
        if (!notes.isEmpty()) {
            resource.put("note", notes);
        }

        return Optional.of(resource);
    }

    private List<Map<String, Object>> buildObservationResources(PatientVisit visit, PatientVisitResponse vResp,
                                                                Patient patient, boolean isVisitShredded) {
        List<Map<String, Object>> obsList = new ArrayList<>();
        String effectiveDateTime = visit.getCreatedAt() != null ? visit.getCreatedAt().toString() : Instant.now().toString();
        String patientRef = "Patient/" + patient.getPatientId();
        String encounterRef = "Encounter/" + visit.getId();

        // 1. Blood Pressure (LOINC 85354-9, component: systolic 8480-6, diastolic 8462-4)
        if (vResp.getBloodPressure() != null && !vResp.getBloodPressure().isBlank()) {
            Map<String, Object> bpObs = new LinkedHashMap<>();
            bpObs.put("resourceType", "Observation");
            bpObs.put("id", "obs-bp-" + visit.getId());
            applyObservationCommon(bpObs, isVisitShredded, patientRef, encounterRef, effectiveDateTime);

            bpObs.put("code", Map.of(
                    "coding", List.of(Map.of(
                            "system", "http://loinc.org",
                            "code", "85354-9",
                            "display", "Blood pressure panel with all children optional"
                    )),
                    "text", "Blood Pressure"
            ));

            if (isVisitShredded || "[SHREDDED]".equals(vResp.getBloodPressure())) {
                bpObs.put("valueString", "[SHREDDED]");
            } else {
                String[] parts = vResp.getBloodPressure().split("/");
                if (parts.length == 2) {
                    Double sys = parseDoubleOrNull(parts[0]);
                    Double dia = parseDoubleOrNull(parts[1]);
                    List<Map<String, Object>> components = new ArrayList<>();

                    if (sys != null) {
                        components.add(Map.of(
                                "code", Map.of(
                                        "coding", List.of(Map.of("system", "http://loinc.org", "code", "8480-6", "display", "Systolic blood pressure")),
                                        "text", "Systolic Blood Pressure"
                                ),
                                "valueQuantity", Map.of("value", sys, "unit", "mmHg", "system", "http://unitsofmeasure.org", "code", "mm[Hg]")
                        ));
                    }
                    if (dia != null) {
                        components.add(Map.of(
                                "code", Map.of(
                                        "coding", List.of(Map.of("system", "http://loinc.org", "code", "8462-4", "display", "Diastolic blood pressure")),
                                        "text", "Diastolic Blood Pressure"
                                ),
                                "valueQuantity", Map.of("value", dia, "unit", "mmHg", "system", "http://unitsofmeasure.org", "code", "mm[Hg]")
                        ));
                    }
                    bpObs.put("component", components);
                } else {
                    bpObs.put("valueString", vResp.getBloodPressure());
                }
            }
            obsList.add(bpObs);
        }

        // 2. Heart Rate (LOINC 8867-4)
        if (vResp.getHeartRate() != null || (isVisitShredded && visit.getHeartRate() != null)) {
            obsList.add(createNumericObservation(
                    "obs-hr-" + visit.getId(), "8867-4", "Heart rate", "Heart Rate",
                    vResp.getHeartRate() != null ? vResp.getHeartRate().doubleValue() : null,
                    "/min", "/min", isVisitShredded, patientRef, encounterRef, effectiveDateTime
            ));
        }

        // 3. Respiratory Rate (LOINC 9279-1)
        if (vResp.getRespiratoryRate() != null && !vResp.getRespiratoryRate().isBlank()) {
            Double rrVal = parseDoubleOrNull(vResp.getRespiratoryRate());
            obsList.add(createNumericObservation(
                    "obs-rr-" + visit.getId(), "9279-1", "Respiratory rate", "Respiratory Rate",
                    rrVal, "/min", "/min", isVisitShredded || "[SHREDDED]".equals(vResp.getRespiratoryRate()),
                    patientRef, encounterRef, effectiveDateTime
            ));
        }

        // 4. Temperature (LOINC 8310-5)
        if (vResp.getTemperature() != null && !vResp.getTemperature().isBlank()) {
            Double tempVal = parseDoubleOrNull(vResp.getTemperature());
            obsList.add(createNumericObservation(
                    "obs-temp-" + visit.getId(), "8310-5", "Body temperature", "Body Temperature",
                    tempVal, "Cel", "Cel", isVisitShredded || "[SHREDDED]".equals(vResp.getTemperature()),
                    patientRef, encounterRef, effectiveDateTime
            ));
        }

        // 5. Oxygen Saturation (LOINC 2708-6)
        if (vResp.getOxygenSaturation() != null && !vResp.getOxygenSaturation().isBlank()) {
            Double spo2Val = parseDoubleOrNull(vResp.getOxygenSaturation());
            obsList.add(createNumericObservation(
                    "obs-spo2-" + visit.getId(), "2708-6", "Oxygen saturation in Arterial blood", "Oxygen Saturation",
                    spo2Val, "%", "%", isVisitShredded || "[SHREDDED]".equals(vResp.getOxygenSaturation()),
                    patientRef, encounterRef, effectiveDateTime
            ));
        }

        // 6. Height (LOINC 8302-2)
        if (vResp.getHeightCm() != null && !vResp.getHeightCm().isBlank()) {
            Double heightVal = parseDoubleOrNull(vResp.getHeightCm());
            obsList.add(createNumericObservation(
                    "obs-ht-" + visit.getId(), "8302-2", "Body height", "Body Height",
                    heightVal, "cm", "cm", isVisitShredded || "[SHREDDED]".equals(vResp.getHeightCm()),
                    patientRef, encounterRef, effectiveDateTime
            ));
        }

        // 7. Weight (LOINC 29463-7)
        if (vResp.getWeightKg() != null && !vResp.getWeightKg().isBlank()) {
            Double weightVal = parseDoubleOrNull(vResp.getWeightKg());
            obsList.add(createNumericObservation(
                    "obs-wt-" + visit.getId(), "29463-7", "Body weight", "Body Weight",
                    weightVal, "kg", "kg", isVisitShredded || "[SHREDDED]".equals(vResp.getWeightKg()),
                    patientRef, encounterRef, effectiveDateTime
            ));
        }

        // 8. BMI (LOINC 39156-5)
        if (vResp.getBmi() != null && !vResp.getBmi().isBlank()) {
            Double bmiVal = parseDoubleOrNull(vResp.getBmi());
            obsList.add(createNumericObservation(
                    "obs-bmi-" + visit.getId(), "39156-5", "Body mass index (BMI) [Ratio]", "Body Mass Index",
                    bmiVal, "kg/m2", "kg/m2", isVisitShredded || "[SHREDDED]".equals(vResp.getBmi()),
                    patientRef, encounterRef, effectiveDateTime
            ));
        }

        // 9. Pain Score (LOINC 72514-3)
        if (vResp.getPainScore() != null || (isVisitShredded && visit.getPainScore() != null)) {
            obsList.add(createNumericObservation(
                    "obs-pain-" + visit.getId(), "72514-3", "Pain severity - 0-10 verbal numeric rating", "Pain Score",
                    vResp.getPainScore() != null ? vResp.getPainScore().doubleValue() : null,
                    "{score}", "{score}", isVisitShredded, patientRef, encounterRef, effectiveDateTime
            ));
        }

        return obsList;
    }

    private Map<String, Object> createNumericObservation(String id, String loincCode, String loincDisplay,
                                                         String textDisplay, Double value, String unit,
                                                         String ucumCode, boolean isShredded,
                                                         String patientRef, String encounterRef,
                                                         String effectiveDateTime) {
        Map<String, Object> obs = new LinkedHashMap<>();
        obs.put("resourceType", "Observation");
        obs.put("id", id);
        applyObservationCommon(obs, isShredded, patientRef, encounterRef, effectiveDateTime);

        obs.put("code", Map.of(
                "coding", List.of(Map.of(
                        "system", "http://loinc.org",
                        "code", loincCode,
                        "display", loincDisplay
                )),
                "text", textDisplay
        ));

        if (isShredded || value == null) {
            obs.put("valueString", "[SHREDDED]");
        } else {
            obs.put("valueQuantity", Map.of(
                    "value", value,
                    "unit", unit,
                    "system", "http://unitsofmeasure.org",
                    "code", ucumCode
            ));
        }

        return obs;
    }

    private void applyObservationCommon(Map<String, Object> obs, boolean isShredded,
                                        String patientRef, String encounterRef,
                                        String effectiveDateTime) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("profile", List.of("https://fhir.hl7.org.uk/StructureDefinition/UKCore-Observation"));
        if (isShredded) {
            meta.put("tag", List.of(CRYPTO_SHREDDED_TAG));
        }
        obs.put("meta", meta);
        obs.put("status", "final");
        obs.put("category", List.of(Map.of(
                "coding", List.of(Map.of(
                        "system", "http://terminology.hl7.org/CodeSystem/observation-category",
                        "code", "vital-signs",
                        "display", "Vital Signs"
                ))
        )));
        obs.put("subject", Map.of("reference", patientRef));
        obs.put("encounter", Map.of("reference", encounterRef));
        obs.put("effectiveDateTime", effectiveDateTime);
    }

    private Optional<Map<String, Object>> buildAllergyResource(PatientVisit visit, PatientVisitResponse vResp,
                                                               Patient patient, boolean isVisitShredded) {
        if (vResp.getAllergies() == null || vResp.getAllergies().isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "AllergyIntolerance");
        resource.put("id", "allergy-" + visit.getId());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("profile", List.of("https://fhir.hl7.org.uk/StructureDefinition/UKCore-AllergyIntolerance"));
        if (isVisitShredded) {
            meta.put("tag", List.of(CRYPTO_SHREDDED_TAG));
        }
        resource.put("meta", meta);

        resource.put("clinicalStatus", Map.of(
                "coding", List.of(Map.of(
                        "system", "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
                        "code", isVisitShredded ? "inactive" : "active"
                ))
        ));
        resource.put("verificationStatus", Map.of(
                "coding", List.of(Map.of(
                        "system", "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
                        "code", "confirmed"
                ))
        ));

        resource.put("patient", Map.of("reference", "Patient/" + patient.getPatientId()));
        resource.put("encounter", Map.of("reference", "Encounter/" + visit.getId()));
        resource.put("code", Map.of("text", vResp.getAllergies()));

        return Optional.of(resource);
    }

    private Optional<Map<String, Object>> buildMedicationResource(PatientVisit visit, PatientVisitResponse vResp,
                                                                  Patient patient, boolean isVisitShredded) {
        if (vResp.getPrescriptions() == null || vResp.getPrescriptions().isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "MedicationStatement");
        resource.put("id", "med-" + visit.getId());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("profile", List.of("https://fhir.hl7.org.uk/StructureDefinition/UKCore-MedicationStatement"));
        if (isVisitShredded) {
            meta.put("tag", List.of(CRYPTO_SHREDDED_TAG));
        }
        resource.put("meta", meta);

        resource.put("status", isVisitShredded ? "inactive" : "active");
        resource.put("subject", Map.of("reference", "Patient/" + patient.getPatientId()));
        resource.put("context", Map.of("reference", "Encounter/" + visit.getId()));
        resource.put("medicationCodeableConcept", Map.of("text", vResp.getPrescriptions()));

        return Optional.of(resource);
    }

    private List<Map<String, Object>> buildDocumentReferenceResources(PatientVisit visit, PatientVisitResponse vResp,
                                                                      Patient patient, boolean isVisitShredded) {
        List<Map<String, Object>> docs = new ArrayList<>();
        if (vResp.getAttachments() == null || vResp.getAttachments().isEmpty()) {
            return docs;
        }

        for (AttachmentResponse att : vResp.getAttachments()) {
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("resourceType", "DocumentReference");
            resource.put("id", "doc-" + att.getId());

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("profile", List.of("https://fhir.hl7.org.uk/StructureDefinition/UKCore-DocumentReference"));
            if (att.isShredded() || isVisitShredded) {
                meta.put("tag", List.of(CRYPTO_SHREDDED_TAG));
            }
            resource.put("meta", meta);

            resource.put("status", "current");
            resource.put("subject", Map.of("reference", "Patient/" + patient.getPatientId()));
            resource.put("context", Map.of(
                    "encounter", List.of(Map.of("reference", "Encounter/" + visit.getId()))
            ));

            Map<String, Object> attachmentMap = new LinkedHashMap<>();
            attachmentMap.put("contentType", att.getContentType());
            attachmentMap.put("title", att.getFileName());
            attachmentMap.put("size", att.getFileSize());
            if (att.getCreatedAt() != null) {
                attachmentMap.put("creation", att.getCreatedAt().toString());
            }

            resource.put("content", List.of(Map.of("attachment", attachmentMap)));
            docs.add(resource);
        }

        return docs;
    }

    private void addBundleEntry(List<Map<String, Object>> entries, String relativeUrl, Map<String, Object> resource) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("fullUrl", "urn:uuid:" + UUID.randomUUID());
        entry.put("resource", resource);
        entries.add(entry);
    }

    private String mapGender(String gender) {
        if (gender == null || gender.isBlank()) return "unknown";
        String g = gender.trim().toLowerCase();
        if (g.startsWith("m")) return "male";
        if (g.startsWith("f")) return "female";
        if (g.startsWith("o")) return "other";
        return "unknown";
    }

    private Double parseDoubleOrNull(String str) {
        if (str == null || str.isBlank() || str.contains("[SHREDDED]")) return null;
        try {
            String clean = str.replaceAll("[^0-9.]", "").trim();
            if (clean.isBlank()) return null;
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return null;
        }
    }
}
