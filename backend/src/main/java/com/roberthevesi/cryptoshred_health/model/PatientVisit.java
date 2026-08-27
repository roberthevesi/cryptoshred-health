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
        @Index(name = "idx_visit_mrn",         columnList = "mrn"),
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

    /** Patient Full Name snapshot at visit time */
    @Column(nullable = false)
    private String patientName;

    /** Medical Record Number or Patient ID pseudonym */
    @Column(nullable = false)
    private String mrn;

    // ── Vitals & Biometrics ──────────────────────────────────────────────────
    @Column
    private String bloodPressure;

    @Column
    private Integer heartRate;

    @Column
    private String respiratoryRate;

    @Column
    private String temperature;

    @Column
    private String oxygenSaturation;

    @Column
    private String heightCm;

    @Column
    private String weightKg;

    @Column
    private String bmi;

    @Column
    private Integer painScore;

    // ── Clinical Profile ─────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(columnDefinition = "TEXT")
    private String prescriptions;

    @Column(columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(columnDefinition = "TEXT")
    private String chronicConditions;

    @Column(columnDefinition = "TEXT")
    private String immunizationStatus;

    @Column(columnDefinition = "TEXT")
    private String lifestyleFactors;

    @Column
    private String followUpDate;

    // ── SOAP Encounter Notes ─────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String medicalNotes;

    @Column(columnDefinition = "TEXT")
    private String soapSubjective;

    @Column(columnDefinition = "TEXT")
    private String soapObjective;

    @Column(columnDefinition = "TEXT")
    private String soapAssessment;

    @Column(columnDefinition = "TEXT")
    private String soapPlan;

    // ── Care Provider & Department ───────────────────────────────────────────
    @Column
    private String attendingDoctor;

    @Column
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
