package com.roberthevesi.cryptoshred_health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "patient_visits", indexes = {
        @Index(name = "idx_visit_owner_id",   columnList = "owner_id"),
        @Index(name = "idx_visit_shredded",    columnList = "shredded"),
        @Index(name = "idx_visit_patient_id",  columnList = "patient_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PatientVisit {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Associated Master Patient */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    @JsonIgnore
    private Patient patient;

    /** Patient Full Name snapshot at visit time (Transient, encrypted in encryptedDataBlob) */
    @Transient
    private String patientName;

    /** Medical Record Number (Transient, encrypted in encryptedDataBlob) */
    @Transient
    private String mrn;

    // ── Vitals & Biometrics (Transient, encrypted in encryptedDataBlob) ─────
    @Transient
    private String bloodPressure;

    @Transient
    private Integer heartRate;

    @Transient
    private String respiratoryRate;

    @Transient
    private String temperature;

    @Transient
    private String oxygenSaturation;

    @Transient
    private String heightCm;

    @Transient
    private String weightKg;

    @Transient
    private String bmi;

    @Transient
    private Integer painScore;

    // ── Clinical Profile (Transient, encrypted in encryptedDataBlob) ─────────
    @Transient
    private String allergies;

    @Transient
    private String prescriptions;

    @Transient
    private String chiefComplaint;

    @Transient
    private String chronicConditions;

    @Transient
    private String immunizationStatus;

    @Transient
    private String lifestyleFactors;

    @Transient
    private String followUpDate;

    // ── SOAP Encounter Notes (Transient, encrypted in encryptedDataBlob) ─────
    @Transient
    private String diagnosis;

    @Transient
    private String medicalNotes;

    @Transient
    private String soapSubjective;

    @Transient
    private String soapObjective;

    @Transient
    private String soapAssessment;

    @Transient
    private String soapPlan;

    // ── Care Provider & Department (Transient, encrypted in encryptedDataBlob)
    @Transient
    private String attendingDoctor;

    @Transient
    private String department;

    // ── Encryption & Lifecycle Fields ────────────────────────────────────────
    /** Base64 ciphertext of the encrypted clinical encounter payload. */
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String encryptedDataBlob;

    /** Cryptographic shredding indicator. */
    @Column(nullable = false)
    private boolean shredded = false;

    /** Permanent JSON serialization of the GDPR Art. 17 verifiable deletion proof. */
    @Column(columnDefinition = "TEXT")
    private String deletionProofJson;

    /** Encrypted binary attachments (PDF reports, scans, etc.). */
    @OneToMany(mappedBy = "patientVisit", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PatientAttachment> attachments = new ArrayList<>();

    /** Envelope Encryption Key metadata. */
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "encryption_key_id")
    @JsonIgnore
    private EncryptionKey encryptionKey;

    /** Clinician who authored this visit. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    private User owner;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
