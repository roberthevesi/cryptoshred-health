package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.AttachmentResponse;
import com.roberthevesi.cryptoshred_health.model.PatientAttachment;
import com.roberthevesi.cryptoshred_health.model.PatientRecord;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.PatientAttachmentRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRecordRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final PatientAttachmentRepository attachmentRepository;
    private final PatientRecordRepository recordRepository;
    private final UserRepository userRepository;

    @Transactional
    public AttachmentResponse uploadAttachment(UUID recordId, MultipartFile file, String currentUserEmail) throws IOException {
        PatientRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Patient record not found: " + recordId));

        User user = findUser(currentUserEmail);
        if (record.isShredded()) {
            throw new IllegalStateException("Cannot attach files to a shredded record");
        }
        if (user.getRole() != Role.DOCTOR && !record.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to upload attachments to this record");
        }

        // Store file data encoded as Base64 (representing encrypted blob)
        String base64Data = Base64.getEncoder().encodeToString(file.getBytes());

        PatientAttachment attachment = new PatientAttachment();
        attachment.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment.pdf");
        attachment.setContentType(file.getContentType() != null ? file.getContentType() : "application/pdf");
        attachment.setFileSize(file.getSize());
        attachment.setEncryptedDataBlob(base64Data);
        attachment.setPatientRecord(record);

        PatientAttachment saved = attachmentRepository.save(attachment);

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
    public List<AttachmentResponse> getAttachmentsForRecord(UUID recordId, String currentUserEmail) {
        PatientRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Patient record not found: " + recordId));

        checkReadAccess(record, findUser(currentUserEmail));

        return attachmentRepository.findByPatientRecordId(recordId).stream()
                .map(att -> AttachmentResponse.builder()
                        .id(att.getId())
                        .fileName(att.getFileName())
                        .contentType(att.getContentType())
                        .fileSize(att.getFileSize())
                        .shredded(att.isShredded() || record.isShredded())
                        .createdAt(att.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public byte[] downloadAttachment(UUID attachmentId, String currentUserEmail) {
        PatientAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        PatientRecord record = attachment.getPatientRecord();
        checkReadAccess(record, findUser(currentUserEmail));

        if (attachment.isShredded() || record.isShredded() || attachment.getEncryptedDataBlob() == null) {
            throw new IllegalStateException("Attachment payload has been crypto-shredded and is irrecoverable");
        }

        return Base64.getDecoder().decode(attachment.getEncryptedDataBlob());
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

    private void checkReadAccess(PatientRecord record, User user) {
        if (user.getRole() == Role.PATIENT && !record.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to access attachments on this record");
        }
    }
}
