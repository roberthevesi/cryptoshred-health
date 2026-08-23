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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        String wrappedDek = vaultKmsService.wrapDek(vaultKeyName, dek);


        // 4. Build comprehensive clinical payload to encrypt under AES-256-GCM
        Map<String, Object> clinicalPayload = new HashMap<>();
        clinicalPayload.put("diagnosis", request.getDiagnosis());
        clinicalPayload.put("medicalNotes", request.getMedicalNotes());
        clinicalPayload.put("allergies", request.getAllergies());
        clinicalPayload.put("prescriptions", request.getPrescriptions());
        clinicalPayload.put("chiefComplaint", request.getChiefComplaint());
        clinicalPayload.put("chronicConditions", request.getChronicConditions());
        clinicalPayload.put("immunizationStatus", request.getImmunizationStatus());
        clinicalPayload.put("lifestyleFactors", request.getLifestyleFactors());
        clinicalPayload.put("followUpDate", request.getFollowUpDate());
        clinicalPayload.put("soapSubjective", request.getSoapSubjective());
        clinicalPayload.put("soapObjective", request.getSoapObjective());
        clinicalPayload.put("soapAssessment", request.getSoapAssessment());
        clinicalPayload.put("soapPlan", request.getSoapPlan());
        clinicalPayload.put("bloodPressure", request.getBloodPressure());
        clinicalPayload.put("heartRate", request.getHeartRate());
        clinicalPayload.put("respiratoryRate", request.getRespiratoryRate());
        clinicalPayload.put("temperature", request.getTemperature());
        clinicalPayload.put("oxygenSaturation", request.getOxygenSaturation());
        clinicalPayload.put("heightCm", request.getHeightCm());
        clinicalPayload.put("weightKg", request.getWeightKg());
        clinicalPayload.put("bmi", request.getBmi());
        clinicalPayload.put("painScore", request.getPainScore());
        clinicalPayload.put("attendingDoctor", request.getAttendingDoctor());
        clinicalPayload.put("department", request.getDepartment());
        clinicalPayload.put("insuranceProvider", request.getInsuranceProvider());
        clinicalPayload.put("insurancePolicyNumber", request.getInsurancePolicyNumber());
        clinicalPayload.put("insuranceGroupNumber", request.getInsuranceGroupNumber());
        clinicalPayload.put("emergencyContactName", request.getEmergencyContactName());
        clinicalPayload.put("emergencyContactPhone", request.getEmergencyContactPhone());
        clinicalPayload.put("emergencyContactRelationship", request.getEmergencyContactRelationship());

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
                : (linkedPatient != null ? linkedPatient.getPatientId() : "MRN-" + (10000 + new Random().nextInt(90000))));
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
                .wrappedDek(wrappedDek)
                .iv(ivBase64)
                .encryptedDataBlob(ciphertextBase64)
                .timestamp(LocalDateTime.now())
                .build());

        PatientVisitResponse response = toResponse(savedVisit);
        patientVisitCacheService.put(savedVisit.getId(), response);
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
            PatientVisitResponse cached = patientVisitCacheService.get(visit.getId());
            if (cached != null) {
                if (!cached.isShredded() && visit.getEncryptionKey() != null) {
                    try {
                        vaultKmsService.unwrapDek(
                                visit.getEncryptionKey().getVaultKeyName(),
                                visit.getEncryptionKey().getWrappedDek());
                        return cached;
                    } catch (Exception e) {
                        log.warn("Redis CACHE HIT detected destroyed Vault KEK for visit {}. Zero-purge invalidation triggered.", visit.getId());
                        cached.setShredded(true);
                        redactResponse(cached);
                        patientVisitCacheService.evict(visit.getId());
                        return cached;
                    }
                }
                return cached;
            }
            PatientVisitResponse response = toResponse(visit);
            patientVisitCacheService.put(visit.getId(), response);
            return response;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PatientVisitResponse findById(UUID id, String currentUserEmail) {
        PatientVisit visit = findVisit(id);
        checkReadAccess(visit, findUser(currentUserEmail));

        // 1. Check Redis cache
        PatientVisitResponse cached = patientVisitCacheService.get(id);
        if (cached != null) {
            if (!cached.isShredded() && visit.getEncryptionKey() != null) {
                try {
                    vaultKmsService.unwrapDek(
                            visit.getEncryptionKey().getVaultKeyName(),
                            visit.getEncryptionKey().getWrappedDek());
                    return cached;
                } catch (Exception e) {
                    log.warn("Redis CACHE HIT detected destroyed Vault KEK for visit {}. Zero-purge invalidation triggered.", id);
                    cached.setShredded(true);
                    redactResponse(cached);
                    patientVisitCacheService.evict(id);
                    return cached;
                }
            }
            return cached;
        }

        // 2. Cache miss — decrypt from DB & put in Redis
        PatientVisitResponse response = toResponse(visit);
        patientVisitCacheService.put(id, response);
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

                Map<String, Object> clinicalPayload = new HashMap<>();
                clinicalPayload.put("diagnosis", request.getDiagnosis());
                clinicalPayload.put("medicalNotes", request.getMedicalNotes());
                clinicalPayload.put("allergies", request.getAllergies());
                clinicalPayload.put("prescriptions", request.getPrescriptions());
                clinicalPayload.put("chiefComplaint", request.getChiefComplaint());
                clinicalPayload.put("chronicConditions", request.getChronicConditions());
                clinicalPayload.put("immunizationStatus", request.getImmunizationStatus());
                clinicalPayload.put("lifestyleFactors", request.getLifestyleFactors());
                clinicalPayload.put("followUpDate", request.getFollowUpDate());
                clinicalPayload.put("soapSubjective", request.getSoapSubjective());
                clinicalPayload.put("soapObjective", request.getSoapObjective());
                clinicalPayload.put("soapAssessment", request.getSoapAssessment());
                clinicalPayload.put("soapPlan", request.getSoapPlan());
                clinicalPayload.put("bloodPressure", request.getBloodPressure());
                clinicalPayload.put("heartRate", request.getHeartRate());
                clinicalPayload.put("respiratoryRate", request.getRespiratoryRate());
                clinicalPayload.put("temperature", request.getTemperature());
                clinicalPayload.put("oxygenSaturation", request.getOxygenSaturation());
                clinicalPayload.put("heightCm", request.getHeightCm());
                clinicalPayload.put("weightKg", request.getWeightKg());
                clinicalPayload.put("bmi", request.getBmi());
                clinicalPayload.put("painScore", request.getPainScore());
                clinicalPayload.put("attendingDoctor", request.getAttendingDoctor());
                clinicalPayload.put("department", request.getDepartment());
                clinicalPayload.put("insuranceProvider", request.getInsuranceProvider());
                clinicalPayload.put("insurancePolicyNumber", request.getInsurancePolicyNumber());
                clinicalPayload.put("insuranceGroupNumber", request.getInsuranceGroupNumber());
                clinicalPayload.put("emergencyContactName", request.getEmergencyContactName());
                clinicalPayload.put("emergencyContactPhone", request.getEmergencyContactPhone());
                clinicalPayload.put("emergencyContactRelationship", request.getEmergencyContactRelationship());

                String jsonToEncrypt = objectMapper.writeValueAsString(clinicalPayload);
                EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                        envelopeEncryptionService.encrypt(jsonToEncrypt.getBytes(StandardCharsets.UTF_8), dek);

                visit.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
                visit.getEncryptionKey().setIv(encryptedPayload.ivBase64());
            } catch (Exception e) {
                log.warn("Failed to re-encrypt clinical payload during update", e);
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
                    .wrappedDek(updatedVisit.getEncryptionKey().getWrappedDek())
                    .iv(updatedVisit.getEncryptionKey().getIv())
                    .encryptedDataBlob(updatedVisit.getEncryptedDataBlob())
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        PatientVisitResponse updatedResponse = toResponse(updatedVisit);
        patientVisitCacheService.put(id, updatedResponse);
        return updatedResponse;
    }

    @Transactional
    public void delete(UUID id, String currentUserEmail) {
        PatientVisit visit = findVisit(id);
        User user = findUser(currentUserEmail);

        if (user.getRole() != Role.DOCTOR && !visit.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to delete this visit");
        }
        patientVisitRepository.delete(visit);
        patientVisitCacheService.evict(id);
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
                .patientName(isShredded ? "[REDACTED]" : v.getPatientName())
                .mrn(v.getMrn())
                .dateOfBirth(isShredded ? null : v.getDateOfBirth())
                .gender(isShredded ? "[REDACTED]" : v.getGender())
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
                .attendingDoctor(isShredded ? "[REDACTED]" : v.getAttendingDoctor())
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
                .encryptedDataBlob(isShredded ? null : v.getEncryptedDataBlob())
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
}
