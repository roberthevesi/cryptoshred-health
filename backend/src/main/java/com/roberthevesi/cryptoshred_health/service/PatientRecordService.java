package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.AttachmentResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRecordRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientRecordResponse;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.PatientRecord;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.PatientRecordRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientRecordService {

    private final PatientRecordRepository patientRecordRepository;
    private final UserRepository userRepository;
    private final VaultKmsService vaultKmsService;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    @Transactional
    public PatientRecordResponse create(PatientRecordRequest request, String currentUserEmail) {
        User owner = findUser(currentUserEmail);

        // 1. Generate unique Vault KEK reference name & DEK
        String keyId = UUID.randomUUID().toString();
        String vaultKeyName = "patient_kek_" + keyId.replace("-", "");
        byte[] dek = envelopeEncryptionService.generateDek();

        // 2. Wrap DEK via Vault KEK
        String wrappedDek = vaultKmsService.wrapDek(vaultKeyName, dek);

        // 3. Encrypt sensitive payload using AES-256-GCM
        String rawNotes = request.getMedicalNotes() != null ? request.getMedicalNotes() : "";
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(rawNotes.getBytes(StandardCharsets.UTF_8), dek);

        EncryptionKey encryptionKey = new EncryptionKey(keyId, vaultKeyName, wrappedDek, encryptedPayload.ivBase64());

        PatientRecord record = new PatientRecord();
        record.setPatientName(request.getPatientName());
        record.setMrn(request.getMrn() != null && !request.getMrn().isBlank()
                ? request.getMrn()
                : "MRN-" + (10000 + new Random().nextInt(90000)));
        record.setDateOfBirth(request.getDateOfBirth());
        record.setGender(request.getGender());
        record.setBloodType(request.getBloodType());
        record.setBloodPressure(request.getBloodPressure());
        record.setHeartRate(request.getHeartRate());
        record.setAllergies(request.getAllergies());
        record.setPrescriptions(request.getPrescriptions());
        record.setDiagnosis(request.getDiagnosis());
        record.setMedicalNotes(rawNotes);
        record.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
        record.setEncryptionKey(encryptionKey);
        record.setOwner(owner);

        return toResponse(patientRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<PatientRecordResponse> findAll(String currentUserEmail) {
        User user = findUser(currentUserEmail);
        List<PatientRecord> records;

        if (user.getRole() == Role.PATIENT) {
            records = patientRecordRepository.findByOwnerId(user.getId());
        } else {
            // DOCTOR and AUDITOR see all records
            records = patientRecordRepository.findAll();
        }

        return records.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PatientRecordResponse findById(UUID id, String currentUserEmail) {
        PatientRecord record = findRecord(id);
        checkReadAccess(record, findUser(currentUserEmail));
        return toResponse(record);
    }

    @Transactional
    public PatientRecordResponse update(UUID id, PatientRecordRequest request, String currentUserEmail) {
        PatientRecord record = findRecord(id);
        User user = findUser(currentUserEmail);

        if (record.isShredded()) {
            throw new IllegalStateException("Cannot update a shredded record");
        }
        if (user.getRole() != Role.DOCTOR && !record.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to update this record");
        }

        record.setPatientName(request.getPatientName());
        if (request.getMrn() != null) record.setMrn(request.getMrn());
        record.setDateOfBirth(request.getDateOfBirth());
        record.setGender(request.getGender());
        record.setBloodType(request.getBloodType());
        record.setBloodPressure(request.getBloodPressure());
        record.setHeartRate(request.getHeartRate());
        record.setAllergies(request.getAllergies());
        record.setPrescriptions(request.getPrescriptions());
        record.setDiagnosis(request.getDiagnosis());
        record.setMedicalNotes(request.getMedicalNotes());

        // Re-encrypt medical notes if updated
        if (record.getEncryptionKey() != null && !record.getEncryptionKey().isInvalidated()) {
            try {
                byte[] dek = vaultKmsService.unwrapDek(
                        record.getEncryptionKey().getVaultKeyName(),
                        record.getEncryptionKey().getWrappedDek());
                String rawNotes = request.getMedicalNotes() != null ? request.getMedicalNotes() : "";
                EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                        envelopeEncryptionService.encrypt(rawNotes.getBytes(StandardCharsets.UTF_8), dek);
                record.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
                record.getEncryptionKey().setIv(encryptedPayload.ivBase64());
            } catch (Exception e) {
                log.warn("Failed to re-encrypt payload during record update", e);
            }
        }

        return toResponse(patientRecordRepository.save(record));
    }

    @Transactional
    public void delete(UUID id, String currentUserEmail) {
        PatientRecord record = findRecord(id);
        User user = findUser(currentUserEmail);

        if (user.getRole() != Role.DOCTOR && !record.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to delete this record");
        }
        patientRecordRepository.delete(record);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PatientRecord findRecord(UUID id) {
        return patientRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private void checkReadAccess(PatientRecord record, User user) {
        if (user.getRole() == Role.PATIENT && !record.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to view this record");
        }
    }

    PatientRecordResponse toResponse(PatientRecord r) {
        List<AttachmentResponse> attachmentResponses = r.getAttachments().stream()
                .map(att -> AttachmentResponse.builder()
                        .id(att.getId())
                        .fileName(att.getFileName())
                        .contentType(att.getContentType())
                        .fileSize(att.getFileSize())
                        .shredded(att.isShredded() || r.isShredded())
                        .createdAt(att.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        boolean isShredded = r.isShredded() || (r.getEncryptionKey() != null && r.getEncryptionKey().isInvalidated());

        String medicalNotes = r.getMedicalNotes();
        // If encrypted data blob exists and key is valid, decrypt notes via Vault DEK unwrap
        if (!isShredded && r.getEncryptedDataBlob() != null && r.getEncryptionKey() != null) {
            try {
                byte[] dek = vaultKmsService.unwrapDek(
                        r.getEncryptionKey().getVaultKeyName(),
                        r.getEncryptionKey().getWrappedDek());
                byte[] decryptedBytes = envelopeEncryptionService.decrypt(
                        r.getEncryptedDataBlob(),
                        r.getEncryptionKey().getIv(),
                        dek);
                medicalNotes = new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("Decryption failed for record {}: Vault key shredded or invalid", r.getId());
                isShredded = true;
                medicalNotes = "[SHREDDED]";
            }
        }

        return PatientRecordResponse.builder()
                .id(r.getId())
                .patientName(r.getPatientName())
                .mrn(r.getMrn())
                .dateOfBirth(r.getDateOfBirth())
                .gender(r.getGender())
                .bloodType(r.getBloodType())
                .bloodPressure(r.getBloodPressure())
                .heartRate(r.getHeartRate())
                .allergies(isShredded ? "[SHREDDED]" : r.getAllergies())
                .prescriptions(isShredded ? "[SHREDDED]" : r.getPrescriptions())
                .diagnosis(isShredded ? "[SHREDDED]" : r.getDiagnosis())
                .medicalNotes(isShredded ? "[SHREDDED]" : medicalNotes)
                .encryptedDataBlob(isShredded ? null : r.getEncryptedDataBlob())
                .shredded(isShredded)
                .ownerEmail(r.getOwner().getEmail())
                .attachments(attachmentResponses)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}

