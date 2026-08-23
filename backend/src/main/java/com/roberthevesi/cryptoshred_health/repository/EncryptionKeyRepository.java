package com.roberthevesi.cryptoshred_health.repository;

import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncryptionKeyRepository extends JpaRepository<EncryptionKey, UUID> {
    Optional<EncryptionKey> findByKeyId(String keyId);
    Optional<EncryptionKey> findByVaultKeyName(String vaultKeyName);
    List<EncryptionKey> findByInvalidatedFalse();
    long countByInvalidatedFalse();
    long countByInvalidatedTrue();
}

