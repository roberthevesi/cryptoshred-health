package com.roberthevesi.cryptoshred_health.repository;

import com.roberthevesi.cryptoshred_health.model.PatientRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientRecordRepository extends JpaRepository<PatientRecord, UUID> {
    List<PatientRecord> findByOwnerId(UUID ownerId);
    List<PatientRecord> findByShreddedFalse();

    @Query("SELECT DISTINCT p FROM PatientRecord p LEFT JOIN FETCH p.encryptionKey")
    List<PatientRecord> findAllWithEncryptionKey();
}

