package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientRecordResponse {
    private UUID id;
    private String patientName;
    private String mrn;
    private String dateOfBirth;
    private String gender;
    private String bloodType;
    private String bloodPressure;
    private Integer heartRate;
    private String allergies;
    private String prescriptions;
    private String diagnosis;
    private String medicalNotes;
    private String encryptedDataBlob;
    private boolean shredded;
    private String ownerEmail;
    private List<AttachmentResponse> attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
