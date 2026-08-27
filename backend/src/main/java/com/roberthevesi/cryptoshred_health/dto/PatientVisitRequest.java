package com.roberthevesi.cryptoshred_health.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PatientVisitRequest {

    private String patientId;

    private String patientName;

    private String mrn;

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

    // ── Care Provider & Department ───────────────────────────────────────────
    private String attendingDoctor;

    private String department;

    /** Optional visit encounter timestamp (e.g. 2025-06-15T09:30:00 or 2025-06-15). */
    private String visitDate;

    /** Optional explicit creation timestamp. */
    private java.time.LocalDateTime createdAt;

    /** Optional pre-encrypted data blob (Base64). */
    private String encryptedDataBlob;
}
