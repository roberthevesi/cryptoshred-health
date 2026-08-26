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

    @PrePersist
    public void ensureId() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }


    @Column(nullable = false, unique = true)
    private String patientId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column
    private LocalDate dateOfBirth;

    @Column
    private String gender;

    @Column
    private String email;

    @Column
    private String phoneNumber;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column
    private String nhsNumber;

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
