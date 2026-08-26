package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.AttachmentResponse;
import com.roberthevesi.cryptoshred_health.model.PatientAttachment;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.PatientAttachmentRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    private final PatientAttachmentRepository attachmentRepository;
    private final PatientVisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final VaultKmsService vaultKmsService;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    @Transactional
    public AttachmentResponse uploadAttachment(UUID visitId, MultipartFile file, String currentUserEmail) throws IOException {
        PatientVisit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new IllegalArgumentException("Patient visit not found: " + visitId));

        User user = findUser(currentUserEmail);
        if (visit.isShredded()) {
            throw new IllegalStateException("Cannot attach files to a shredded visit");
        }
        if (user.getRole() != Role.DOCTOR && !visit.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to upload attachments to this visit");
        }

        // 1. Retrieve DEK via Vault KEK unwrap
        byte[] dek = vaultKmsService.unwrapDek(
                visit.getEncryptionKey().getVaultKeyName(),
                visit.getEncryptionKey().getWrappedDek());

        // 2. Encrypt file binary payload with AES-256-GCM
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(file.getBytes(), dek);

        PatientAttachment attachment = new PatientAttachment();
        attachment.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment.pdf");
        attachment.setContentType(file.getContentType() != null ? file.getContentType() : "application/pdf");
        attachment.setFileSize(file.getSize());
        attachment.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
        attachment.setIv(encryptedPayload.ivBase64());
        attachment.setPatientVisit(visit);

        PatientAttachment saved = attachmentRepository.save(attachment);
        log.info("Encrypted and attached file {} ({} bytes) to visit {}", saved.getFileName(), saved.getFileSize(), visitId);

        return AttachmentResponse.builder()
                .id(saved.getId())
                .fileName(saved.getFileName())
                .contentType(saved.getContentType())
                .fileSize(saved.getFileSize())
                .shredded(false)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachmentsForVisit(UUID visitId, String currentUserEmail) {
        PatientVisit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new IllegalArgumentException("Patient visit not found: " + visitId));

        checkReadAccess(visit, findUser(currentUserEmail));

        return attachmentRepository.findByPatientVisitId(visitId).stream()
                .map(att -> AttachmentResponse.builder()
                        .id(att.getId())
                        .fileName(att.getFileName())
                        .contentType(att.getContentType())
                        .fileSize(att.getFileSize())
                        .shredded(att.isShredded() || visit.isShredded())
                        .createdAt(att.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public byte[] downloadAttachment(UUID attachmentId, String currentUserEmail) {
        PatientAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        PatientVisit visit = attachment.getPatientVisit();
        checkReadAccess(visit, findUser(currentUserEmail));

        if (attachment.isShredded() || visit.isShredded() || attachment.getEncryptedDataBlob() == null) {
            throw new IllegalStateException("Attachment payload has been crypto-shredded and is irrecoverable");
        }

        try {
            // Unwrap DEK via Vault KEK
            byte[] dek = vaultKmsService.unwrapDek(
                    visit.getEncryptionKey().getVaultKeyName(),
                    visit.getEncryptionKey().getWrappedDek());

            // Decrypt ciphertext using AES-256-GCM
            return envelopeEncryptionService.decrypt(
                    attachment.getEncryptedDataBlob(),
                    attachment.getIv() != null ? attachment.getIv() : visit.getEncryptionKey().getIv(),
                    dek);
        } catch (Exception e) {
            log.warn("Failed to decrypt attachment {}: Vault key shredded or invalid", attachmentId);
            throw new IllegalStateException("Attachment decryption failed: key is destroyed or inaccessible", e);
        }
    }

    @Transactional(readOnly = true)
    public PatientAttachment getAttachmentMetadata(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    private void checkReadAccess(PatientVisit visit, User user) {
        if (user.getRole() == Role.PATIENT) {
            boolean hasAccess = (visit.getPatient() != null && visit.getPatient().getEmail() != null && visit.getPatient().getEmail().equalsIgnoreCase(user.getEmail()))
                    || (visit.getOwner() != null && visit.getOwner().getId().equals(user.getId()))
                    || (visit.getMrn() != null && patientRepository.findByEmailIgnoreCase(user.getEmail()).map(p -> p.getPatientId().equalsIgnoreCase(visit.getMrn())).orElse(false));
            if (!hasAccess) {
                throw new AccessDeniedException("Not authorized to access attachments on this visit");
            }
        }
    }
}
