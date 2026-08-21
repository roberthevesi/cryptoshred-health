package com.roberthevesi.cryptoshred_health.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PatientVisitRequest {

    @NotBlank(message = "Patient name is required")
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

    /** Optional pre-encrypted data blob (Base64). */
    private String encryptedDataBlob;
}
