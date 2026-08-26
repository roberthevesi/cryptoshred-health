package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.AttachmentResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitEventDto;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitResponse;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientVisitService {

    private final PatientVisitRepository patientVisitRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final VaultKmsService vaultKmsService;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final EventLogPublisher eventLogPublisher;
    private final PatientVisitCacheService patientVisitCacheService;
    private final PatientCacheService patientCacheService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PatientVisitResponse create(PatientVisitRequest request, String currentUserEmail) {
        User owner = findUser(currentUserEmail);

        // 1. Resolve master Patient entity if MRN / patientId provided
        Patient linkedPatient = null;
        if (request.getMrn() != null && !request.getMrn().isBlank()) {
            linkedPatient = patientRepository.findByPatientId(request.getMrn()).orElse(null);
        }

        // 2. Generate unique Vault KEK reference name & DEK
        UUID visitUuid = UUID.randomUUID();
        String patientUuidStr = (linkedPatient != null && linkedPatient.getId() != null)
                ? linkedPatient.getId().toString()
                : "unlinked";
        String keyId = visitUuid.toString();
        String vaultKeyName = "patient_" + patientUuidStr + "_visit_" + visitUuid;
        byte[] dek = envelopeEncryptionService.generateDek();


        // 3. Wrap DEK via Vault KEK
        vaultKmsService.ensureKeyExists(vaultKeyName);
            String wrappedDek = vaultKmsService.wrapDek(vaultKeyName, dek);


        // 4. Build comprehensive clinical payload to encrypt under AES-256-GCM
        Map<String, Object> clinicalPayload = buildClinicalPayload(request);

        String ciphertextBase64;
        String ivBase64;
        try {
            String jsonToEncrypt = objectMapper.writeValueAsString(clinicalPayload);
            EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                    envelopeEncryptionService.encrypt(jsonToEncrypt.getBytes(StandardCharsets.UTF_8), dek);
            ciphertextBase64 = encryptedPayload.ciphertextBase64();
            ivBase64 = encryptedPayload.ivBase64();
        } catch (Exception e) {
            log.error("Failed to encrypt clinical visit payload: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to encrypt clinical visit payload", e);
        }

        EncryptionKey encryptionKey = new EncryptionKey(keyId, vaultKeyName, wrappedDek, ivBase64);

        PatientVisit visit = new PatientVisit();
        visit.setId(visitUuid);
        visit.setPatient(linkedPatient);

        visit.setPatientName(request.getPatientName());
        visit.setMrn(request.getMrn() != null && !request.getMrn().isBlank()
                ? request.getMrn()
                : (linkedPatient != null ? linkedPatient.getPatientId() : "MRN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase()));
        visit.setDateOfBirth(request.getDateOfBirth());
        visit.setGender(request.getGender());
        visit.setBloodType(request.getBloodType());

        // Clinical fields
        visit.setBloodPressure(request.getBloodPressure());
        visit.setHeartRate(request.getHeartRate());
        visit.setRespiratoryRate(request.getRespiratoryRate());
        visit.setTemperature(request.getTemperature());
        visit.setOxygenSaturation(request.getOxygenSaturation());
        visit.setHeightCm(request.getHeightCm());
        visit.setWeightKg(request.getWeightKg());
        visit.setBmi(request.getBmi());
        visit.setPainScore(request.getPainScore());

        visit.setAllergies(request.getAllergies());
        visit.setPrescriptions(request.getPrescriptions());
        visit.setChiefComplaint(request.getChiefComplaint());
        visit.setChronicConditions(request.getChronicConditions());
        visit.setImmunizationStatus(request.getImmunizationStatus());
        visit.setLifestyleFactors(request.getLifestyleFactors());
        visit.setFollowUpDate(request.getFollowUpDate());

        visit.setDiagnosis(request.getDiagnosis());
        visit.setMedicalNotes(request.getMedicalNotes());
        visit.setSoapSubjective(request.getSoapSubjective());
        visit.setSoapObjective(request.getSoapObjective());
        visit.setSoapAssessment(request.getSoapAssessment());
        visit.setSoapPlan(request.getSoapPlan());

        visit.setAttendingDoctor(request.getAttendingDoctor());
        visit.setDepartment(request.getDepartment());
        visit.setInsuranceProvider(request.getInsuranceProvider());
        visit.setInsurancePolicyNumber(request.getInsurancePolicyNumber());
        visit.setInsuranceGroupNumber(request.getInsuranceGroupNumber());
        visit.setPhone(request.getPhone());
        visit.setEmail(request.getEmail());
        visit.setAddress(request.getAddress());
        visit.setEmergencyContactName(request.getEmergencyContactName());
        visit.setEmergencyContactPhone(request.getEmergencyContactPhone());
        visit.setEmergencyContactRelationship(request.getEmergencyContactRelationship());

        LocalDateTime visitTime = LocalDateTime.now();
        if (request.getCreatedAt() != null) {
            visitTime = request.getCreatedAt();
        } else if (request.getVisitDate() != null && !request.getVisitDate().isBlank()) {
            try {
                if (request.getVisitDate().length() == 10) {
                    visitTime = LocalDate.parse(request.getVisitDate()).atTime(9, 30);
                } else {
                    visitTime = LocalDateTime.parse(request.getVisitDate());
                }
            } catch (Exception e) {
                visitTime = LocalDateTime.now();
            }
        }
        visit.setCreatedAt(visitTime);
        visit.setUpdatedAt(visitTime);

        visit.setEncryptedDataBlob(ciphertextBase64);
        visit.setEncryptionKey(encryptionKey);
        visit.setOwner(owner);

        PatientVisit savedVisit = patientVisitRepository.save(visit);

        // 5. Publish encrypted event to Kafka event log (pseudonymized)
        eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .visitId(savedVisit.getId())
                .patientId(linkedPatient != null ? linkedPatient.getPatientId() : savedVisit.getMrn())
                .eventType("VISIT_CREATED")
                .vaultKeyName(vaultKeyName)
                                                                .timestamp(LocalDateTime.now())
                .build());

        PatientVisitResponse response = toResponse(savedVisit);
        if (response != null && !response.isShredded()) {
            patientVisitCacheService.put(savedVisit.getId(), response);
        }

        // Evict cached patient demographic profile so visitCount is recomputed on next read
        String patientIdentifier = linkedPatient != null ? linkedPatient.getPatientId() : savedVisit.getMrn();
        if (patientIdentifier != null && patientCacheService != null) {
            patientCacheService.evict(patientIdentifier);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<PatientVisitResponse> findAll(String currentUserEmail) {
        User user = findUser(currentUserEmail);
        List<PatientVisit> visits;

        if (user.getRole() == Role.PATIENT) {
            visits = patientVisitRepository.findByOwnerId(user.getId());
        } else {
            visits = patientVisitRepository.findAll();
        }

        return visits.stream().map(visit -> {
            if (visit.isShredded()) {
                return toResponse(visit); // Skip Redis check entirely for shredded visits
            }
            PatientVisitResponse cached = patientVisitCacheService.get(visit.getId());
            if (cached != null) {
                return cached; // Trust the cache — ErasureService proactively evicts on shred
            }
            PatientVisitResponse response = toResponse(visit);
            if (response != null && !response.isShredded()) {
                patientVisitCacheService.put(visit.getId(), response);
            }
            return response;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PatientVisitResponse> findByPatientIdentifier(String patientId, String currentUserEmail) {
        User user = findUser(currentUserEmail);
        List<PatientVisit> visits = patientVisitRepository.findByPatientIdentifier(patientId);

        if (user.getRole() == Role.PATIENT) {
            visits = visits.stream()
                    .filter(v -> v.getOwner() != null && v.getOwner().getId().equals(user.getId()))
                    .collect(Collectors.toList());
        }

        return visits.stream().map(visit -> {
            if (visit.isShredded()) {
                return toResponse(visit); // Skip Redis check entirely for shredded visits
            }
            PatientVisitResponse cached = patientVisitCacheService.get(visit.getId());
            if (cached != null) {
                return cached;
            }
            PatientVisitResponse response = toResponse(visit);
            if (response != null && !response.isShredded()) {
                patientVisitCacheService.put(visit.getId(), response);
            }
            return response;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PatientVisitResponse findById(UUID id, String currentUserEmail) {
        PatientVisit visit = findVisit(id);
        checkReadAccess(visit, findUser(currentUserEmail));

        if (visit.isShredded()) {
            return toResponse(visit); // Skip Redis check entirely for shredded visits
        }

        // 1. Check Redis cache
        PatientVisitResponse cached = patientVisitCacheService.get(id);
        if (cached != null) {
            log.info("⚡ [REDIS HIT] Serving clinical visit record {} from Redis L2 cache", id);
            return cached;
        }

        // 2. Cache miss — decrypt from DB & put in Redis if not shredded
        log.info("🐢 [REDIS MISS] Clinical visit {} not in Redis. Decrypting payload via Vault KMS and caching...", id);
        PatientVisitResponse response = toResponse(visit);
        if (response != null && !response.isShredded()) {
            patientVisitCacheService.put(id, response);
        }
        return response;
    }

    @Transactional
    public PatientVisitResponse update(UUID id, PatientVisitRequest request, String currentUserEmail) {
        PatientVisit visit = findVisit(id);
        User user = findUser(currentUserEmail);

        if (visit.isShredded()) {
            throw new IllegalStateException("Cannot update a shredded visit");
        }
        if (user.getRole() != Role.DOCTOR && !visit.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to update this visit");
        }

        visit.setPatientName(request.getPatientName());
        if (request.getMrn() != null) visit.setMrn(request.getMrn());
        visit.setDateOfBirth(request.getDateOfBirth());
        visit.setGender(request.getGender());
        visit.setBloodType(request.getBloodType());

        visit.setBloodPressure(request.getBloodPressure());
        visit.setHeartRate(request.getHeartRate());
        visit.setRespiratoryRate(request.getRespiratoryRate());
        visit.setTemperature(request.getTemperature());
        visit.setOxygenSaturation(request.getOxygenSaturation());
        visit.setHeightCm(request.getHeightCm());
        visit.setWeightKg(request.getWeightKg());
        visit.setBmi(request.getBmi());
        visit.setPainScore(request.getPainScore());

        visit.setAllergies(request.getAllergies());
        visit.setPrescriptions(request.getPrescriptions());
        visit.setChiefComplaint(request.getChiefComplaint());
        visit.setChronicConditions(request.getChronicConditions());
        visit.setImmunizationStatus(request.getImmunizationStatus());
        visit.setLifestyleFactors(request.getLifestyleFactors());
        visit.setFollowUpDate(request.getFollowUpDate());

        visit.setDiagnosis(request.getDiagnosis());
        visit.setMedicalNotes(request.getMedicalNotes());
        visit.setSoapSubjective(request.getSoapSubjective());
        visit.setSoapObjective(request.getSoapObjective());
        visit.setSoapAssessment(request.getSoapAssessment());
        visit.setSoapPlan(request.getSoapPlan());

        visit.setAttendingDoctor(request.getAttendingDoctor());
        visit.setDepartment(request.getDepartment());
        visit.setInsuranceProvider(request.getInsuranceProvider());
        visit.setInsurancePolicyNumber(request.getInsurancePolicyNumber());
        visit.setInsuranceGroupNumber(request.getInsuranceGroupNumber());
        visit.setPhone(request.getPhone());
        visit.setEmail(request.getEmail());
        visit.setAddress(request.getAddress());
        visit.setEmergencyContactName(request.getEmergencyContactName());
        visit.setEmergencyContactPhone(request.getEmergencyContactPhone());
        visit.setEmergencyContactRelationship(request.getEmergencyContactRelationship());

        // Re-encrypt clinical payload
        if (visit.getEncryptionKey() != null && !visit.getEncryptionKey().isInvalidated()) {
            try {
                byte[] dek = vaultKmsService.unwrapDek(
                        visit.getEncryptionKey().getVaultKeyName(),
                        visit.getEncryptionKey().getWrappedDek());

                Map<String, Object> clinicalPayload = buildClinicalPayload(request);

                String jsonToEncrypt = objectMapper.writeValueAsString(clinicalPayload);
                EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                        envelopeEncryptionService.encrypt(jsonToEncrypt.getBytes(StandardCharsets.UTF_8), dek);

                visit.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
                visit.getEncryptionKey().setIv(encryptedPayload.ivBase64());
            } catch (Exception e) {
                log.error("Re-encryption failed for visit {}. Aborting update to preserve data integrity.", id, e);
                throw new IllegalStateException("Clinical payload re-encryption failed; update aborted.", e);
            }
        }

        PatientVisit updatedVisit = patientVisitRepository.save(visit);

        if (updatedVisit.getEncryptionKey() != null) {
            eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                    .eventId(UUID.randomUUID())
                    .visitId(updatedVisit.getId())
                    .patientId(updatedVisit.getPatient() != null ? updatedVisit.getPatient().getPatientId() : updatedVisit.getMrn())
                    .eventType("VISIT_UPDATED")
                    .vaultKeyName(updatedVisit.getEncryptionKey().getVaultKeyName())
                                                                                .timestamp(LocalDateTime.now())
                    .build());
        }

        PatientVisitResponse updatedResponse = toResponse(updatedVisit);
        if (updatedResponse != null && !updatedResponse.isShredded()) {
            patientVisitCacheService.put(id, updatedResponse);
        }
        return updatedResponse;
    }

    @Transactional
    public void delete(UUID id, String currentUserEmail) {
        PatientVisit visit = findVisit(id);
        User user = findUser(currentUserEmail);

        if (user.getRole() != Role.DOCTOR && !visit.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to delete this visit");
        }
        String patientIdentifier = visit.getPatient() != null ? visit.getPatient().getPatientId() : visit.getMrn();
        patientVisitRepository.delete(visit);
        patientVisitCacheService.evict(id);
        if (patientIdentifier != null && patientCacheService != null) {
            patientCacheService.evict(patientIdentifier);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PatientVisit findVisit(UUID id) {
        return patientVisitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient visit not found: " + id));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private void checkReadAccess(PatientVisit visit, User user) {
        if (user.getRole() == Role.PATIENT && !visit.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to view this visit");
        }
    }

    private void redactResponse(PatientVisitResponse r) {
        r.setAllergies("[SHREDDED]");
        r.setPrescriptions("[SHREDDED]");
        r.setDiagnosis("[SHREDDED]");
        r.setMedicalNotes("[SHREDDED]");
        r.setSoapSubjective("[SHREDDED]");
        r.setSoapObjective("[SHREDDED]");
        r.setSoapAssessment("[SHREDDED]");
        r.setSoapPlan("[SHREDDED]");
        r.setChiefComplaint("[SHREDDED]");
        r.setChronicConditions("[SHREDDED]");
        r.setImmunizationStatus("[SHREDDED]");
        r.setLifestyleFactors("[SHREDDED]");
        r.setFollowUpDate(null);
        r.setEncryptedDataBlob(null);
    }

    public PatientVisitResponse toResponse(PatientVisit v) {
        List<AttachmentResponse> attachmentResponses = v.getAttachments().stream()
                .map(att -> AttachmentResponse.builder()
                        .id(att.getId())
                        .fileName(att.getFileName())
                        .contentType(att.getContentType())
                        .fileSize(att.getFileSize())
                        .shredded(att.isShredded() || v.isShredded())
                        .createdAt(att.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        boolean isShredded = v.isShredded() ||
                (v.getEncryptionKey() != null && v.getEncryptionKey().isInvalidated()) ||
                (v.getPatient() != null && v.getPatient().isShredded());

        String diagnosis = v.getDiagnosis();
        String medicalNotes = v.getMedicalNotes();
        String allergies = v.getAllergies();
        String prescriptions = v.getPrescriptions();
        String chiefComplaint = v.getChiefComplaint();
        String chronicConditions = v.getChronicConditions();
        String immunizationStatus = v.getImmunizationStatus();
        String lifestyleFactors = v.getLifestyleFactors();
        String followUpDate = v.getFollowUpDate();
        String soapSubjective = v.getSoapSubjective();
        String soapObjective = v.getSoapObjective();
        String soapAssessment = v.getSoapAssessment();
        String soapPlan = v.getSoapPlan();

        // If encrypted data blob exists and key is valid, unwrap and decrypt
        if (!isShredded && v.getEncryptedDataBlob() != null && v.getEncryptionKey() != null) {
            try {
                byte[] dek = vaultKmsService.unwrapDek(
                        v.getEncryptionKey().getVaultKeyName(),
                        v.getEncryptionKey().getWrappedDek());
                byte[] decryptedBytes = envelopeEncryptionService.decrypt(
                        v.getEncryptedDataBlob(),
                        v.getEncryptionKey().getIv(),
                        dek);
                String json = new String(decryptedBytes, StandardCharsets.UTF_8);
                Map<?, ?> map = objectMapper.readValue(json, Map.class);
                if (map.containsKey("diagnosis")) diagnosis = (String) map.get("diagnosis");
                if (map.containsKey("medicalNotes")) medicalNotes = (String) map.get("medicalNotes");
                if (map.containsKey("allergies")) allergies = (String) map.get("allergies");
                if (map.containsKey("prescriptions")) prescriptions = (String) map.get("prescriptions");
                if (map.containsKey("chiefComplaint")) chiefComplaint = (String) map.get("chiefComplaint");
                if (map.containsKey("chronicConditions")) chronicConditions = (String) map.get("chronicConditions");
                if (map.containsKey("immunizationStatus")) immunizationStatus = (String) map.get("immunizationStatus");
                if (map.containsKey("lifestyleFactors")) lifestyleFactors = (String) map.get("lifestyleFactors");
                if (map.containsKey("followUpDate")) followUpDate = (String) map.get("followUpDate");
                if (map.containsKey("soapSubjective")) soapSubjective = (String) map.get("soapSubjective");
                if (map.containsKey("soapObjective")) soapObjective = (String) map.get("soapObjective");
                if (map.containsKey("soapAssessment")) soapAssessment = (String) map.get("soapAssessment");
                if (map.containsKey("soapPlan")) soapPlan = (String) map.get("soapPlan");
            } catch (Exception e) {
                log.warn("Decryption failed for visit {}: Vault key shredded or invalid", v.getId());
                isShredded = true;
            }
        }

        PatientVisitResponse resp = PatientVisitResponse.builder()
                .id(v.getId())
                .patientId(v.getPatient() != null ? v.getPatient().getPatientId() : v.getMrn())
                .patientName(isShredded ? "[SHREDDED]" : v.getPatientName())
                .mrn(v.getMrn())
                .dateOfBirth(isShredded ? null : v.getDateOfBirth())
                .gender(isShredded ? "[SHREDDED]" : v.getGender())
                .bloodType(isShredded ? null : v.getBloodType())
                .bloodPressure(isShredded ? null : v.getBloodPressure())
                .heartRate(isShredded ? null : v.getHeartRate())
                .respiratoryRate(isShredded ? null : v.getRespiratoryRate())
                .temperature(isShredded ? null : v.getTemperature())
                .oxygenSaturation(isShredded ? null : v.getOxygenSaturation())
                .heightCm(isShredded ? null : v.getHeightCm())
                .weightKg(isShredded ? null : v.getWeightKg())
                .bmi(isShredded ? null : v.getBmi())
                .painScore(isShredded ? null : v.getPainScore())
                .allergies(isShredded ? "[SHREDDED]" : allergies)
                .prescriptions(isShredded ? "[SHREDDED]" : prescriptions)
                .chiefComplaint(isShredded ? "[SHREDDED]" : chiefComplaint)
                .chronicConditions(isShredded ? "[SHREDDED]" : chronicConditions)
                .immunizationStatus(isShredded ? "[SHREDDED]" : immunizationStatus)
                .lifestyleFactors(isShredded ? "[SHREDDED]" : lifestyleFactors)
                .followUpDate(isShredded ? null : followUpDate)
                .diagnosis(isShredded ? "[SHREDDED]" : diagnosis)
                .medicalNotes(isShredded ? "[SHREDDED]" : medicalNotes)
                .soapSubjective(isShredded ? "[SHREDDED]" : soapSubjective)
                .soapObjective(isShredded ? "[SHREDDED]" : soapObjective)
                .soapAssessment(isShredded ? "[SHREDDED]" : soapAssessment)
                .soapPlan(isShredded ? "[SHREDDED]" : soapPlan)
                .attendingDoctor(isShredded ? "[SHREDDED]" : v.getAttendingDoctor())
                .department(isShredded ? null : v.getDepartment())
                .insuranceProvider(isShredded ? null : v.getInsuranceProvider())
                .insurancePolicyNumber(isShredded ? null : v.getInsurancePolicyNumber())
                .insuranceGroupNumber(isShredded ? null : v.getInsuranceGroupNumber())
                .phone(isShredded ? null : v.getPhone())
                .email(isShredded ? null : v.getEmail())
                .address(isShredded ? null : v.getAddress())
                .emergencyContactName(isShredded ? null : v.getEmergencyContactName())
                .emergencyContactPhone(isShredded ? null : v.getEmergencyContactPhone())
                .emergencyContactRelationship(isShredded ? null : v.getEmergencyContactRelationship())
                                .shredded(isShredded)
                .ownerEmail(v.getOwner() != null ? v.getOwner().getEmail() : null)
                .attachments(attachmentResponses)
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();

        if (isShredded) {
            redactResponse(resp);
        }

        return resp;
    }

    private Map<String, Object> buildClinicalPayload(PatientVisitRequest request) {
        Map<String, Object> payload = new HashMap<>();
        if (request.getDiagnosis() != null) payload.put("diagnosis", request.getDiagnosis());
        if (request.getMedicalNotes() != null) payload.put("medicalNotes", request.getMedicalNotes());
        if (request.getAllergies() != null) payload.put("allergies", request.getAllergies());
        if (request.getPrescriptions() != null) payload.put("prescriptions", request.getPrescriptions());
        if (request.getChiefComplaint() != null) payload.put("chiefComplaint", request.getChiefComplaint());
        if (request.getChronicConditions() != null) payload.put("chronicConditions", request.getChronicConditions());
        if (request.getImmunizationStatus() != null) payload.put("immunizationStatus", request.getImmunizationStatus());
        if (request.getLifestyleFactors() != null) payload.put("lifestyleFactors", request.getLifestyleFactors());
        if (request.getFollowUpDate() != null) payload.put("followUpDate", request.getFollowUpDate());
        if (request.getSoapSubjective() != null) payload.put("soapSubjective", request.getSoapSubjective());
        if (request.getSoapObjective() != null) payload.put("soapObjective", request.getSoapObjective());
        if (request.getSoapAssessment() != null) payload.put("soapAssessment", request.getSoapAssessment());
        if (request.getSoapPlan() != null) payload.put("soapPlan", request.getSoapPlan());
        return payload;
    }
}
