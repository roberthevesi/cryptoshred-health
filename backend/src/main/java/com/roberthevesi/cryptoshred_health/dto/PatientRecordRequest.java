package com.roberthevesi.cryptoshred_health.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PatientRecordRequest {

    @NotBlank(message = "Patient name is required")
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

    /** Optional pre-encrypted data blob (Base64). If not provided, medicalNotes is used. */
    private String encryptedDataBlob;
}
