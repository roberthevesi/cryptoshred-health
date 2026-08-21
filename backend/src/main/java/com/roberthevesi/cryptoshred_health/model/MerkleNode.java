package com.roberthevesi.cryptoshred_health.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted leaf and branch nodes for the GDPR Article 17 Merkle Tree.
 * Ensures cryptographic erasure proofs remain permanently verifiable across server restarts.
 */
@Entity
@Table(name = "merkle_nodes")
@Getter
@Setter
@NoArgsConstructor
public class MerkleNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private int leafIndex;

    @Column(nullable = false, length = 64)
    private String leafHash;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public MerkleNode(int leafIndex, String leafHash) {
        this.leafIndex = leafIndex;
        this.leafHash = leafHash;
    }
}
