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
public class PatientVisitResponse {
    private UUID id;
    private String patientId; // Synthetic ID of linked Patient
    private String patientName;
    private String mrn;
    private String dateOfBirth;
    private String gender;
    private String bloodType;

    // ── Vitals & Biometrics ──────────────────────────────────────────────────
    private String bloodPressure;
    private Integer heartRate;
    private String respiratoryRate;
    private String temperature;
    private String oxygenSaturation;
    private String heightCm;
    private String weightKg;
    private String bmi;
    private Integer painScore;

    // ── Clinical Profile ─────────────────────────────────────────────────────
    private String allergies;
    private String prescriptions;
    private String chiefComplaint;
    private String chronicConditions;
    private String immunizationStatus;
    private String lifestyleFactors;
    private String followUpDate;

    // ── SOAP Encounter Notes ─────────────────────────────────────────────────
    private String diagnosis;
    private String medicalNotes;
    private String soapSubjective;
    private String soapObjective;
    private String soapAssessment;
    private String soapPlan;

    // ── Care Provider & Insurance ────────────────────────────────────────────
    private String attendingDoctor;
    private String department;
    private String insuranceProvider;
    private String insurancePolicyNumber;
    private String insuranceGroupNumber;
    private String phone;
    private String email;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelationship;

    // ── Encryption Status ────────────────────────────────────────────────────
    private String encryptedDataBlob;
    private boolean shredded;
    private String ownerEmail;
    private List<AttachmentResponse> attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
