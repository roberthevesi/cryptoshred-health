package com.roberthevesi.cryptoshred_health.model;

import jakarta.persistence.*;
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
@Table(name = "patient_records")
@Getter
@Setter
@NoArgsConstructor
public class PatientRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String patientName;

    @Column(unique = true)
    private String mrn;

    private String dateOfBirth;

    private String gender;

    private String bloodType;

    private String bloodPressure;

    private Integer heartRate;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(columnDefinition = "TEXT")
    private String prescriptions;

    @Column
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String medicalNotes;

    /** Base64-encoded ciphertext of the sensitive medical payload. */
    @Column(columnDefinition = "TEXT")
    private String encryptedDataBlob;

    /** Link to the encryption key. Invalidating this key crypto-shreds the record. */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "encryption_key_id")
    private EncryptionKey encryptionKey;

    @OneToMany(mappedBy = "patientRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PatientAttachment> attachments = new ArrayList<>();

    /** True once the Right-to-be-Forgotten has been exercised. */
    @Column(nullable = false)
    private boolean shredded = false;

    /** The doctor/system user who created this record. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
