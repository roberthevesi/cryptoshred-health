package com.roberthevesi.cryptoshred_health.repository;

import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientVisitRepository extends JpaRepository<PatientVisit, UUID> {
    List<PatientVisit> findByOwnerId(UUID ownerId);
    List<PatientVisit> findByShreddedFalse();
    List<PatientVisit> findByMrn(String mrn);
    Optional<PatientVisit> findByEncryptionKey(com.roberthevesi.cryptoshred_health.model.EncryptionKey encryptionKey);

    @Query("SELECT v FROM PatientVisit v WHERE v.patient.patientId = :patientId OR v.mrn = :patientId")
    List<PatientVisit> findByPatientIdentifier(String patientId);

    @Query("SELECT DISTINCT v FROM PatientVisit v LEFT JOIN FETCH v.encryptionKey")
    List<PatientVisit> findAllWithEncryptionKey();
}
