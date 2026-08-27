package com.roberthevesi.cryptoshred_health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String patientId;

    /** Transient in-memory demographic PII fields (persisted only in AES-256-GCM encryptedDataBlob) */
    @Transient
    private String firstName;

    @Transient
    private String lastName;

    @Transient
    private LocalDate dateOfBirth;

    @Transient
    private String gender;

    @Transient
    private String email;

    @Transient
    private String phoneNumber;

    @Transient
    private String address;

    @Transient
    private String nhsNumber;

    @Transient
    private String bloodType;

    @Transient
    private String emergencyContactName;

    @Transient
    private String emergencyContactPhone;

    @Transient
    private String emergencyContactRelationship;

    @Transient
    private String insuranceProvider;

    @Transient
    private String insurancePolicyNumber;

    @Transient
    private String insuranceGroupNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gp_id")
    private GP gp;

    @Column(nullable = false)
    private boolean isActive = true;

    /** Base64-encoded AES-256-GCM encrypted demographic PII payload. */
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String encryptedDataBlob;

    /** Cryptographic shredding status. */
    @Column(nullable = false)
    private boolean shredded = false;

    /** Permanent JSON serialization of the GDPR Art. 17 verifiable deletion proof. */
    @Column(columnDefinition = "TEXT")
    private String deletionProofJson;

    /** Dedicated Vault Transit KEK for patient demographics. */
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "encryption_key_id")
    @JsonIgnore
    private EncryptionKey encryptionKey;

    /** All clinical visits for this patient. */
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<PatientVisit> visits = new ArrayList<>();

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
