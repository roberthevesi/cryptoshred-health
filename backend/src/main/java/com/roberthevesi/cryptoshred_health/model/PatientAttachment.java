package com.roberthevesi.cryptoshred_health.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "patient_attachments")
@Getter
@Setter
@NoArgsConstructor
public class PatientAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    /** Base64-encoded AES-256-GCM encrypted binary blob. Nullified upon crypto-shredding. */
    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String encryptedDataBlob;

    /** AES-GCM Initialization Vector for this attachment. */
    @Column
    private String iv;

    @Column(nullable = false)
    private boolean shredded = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_visit_id", nullable = false)
    @JsonIgnore
    private PatientVisit patientVisit;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
