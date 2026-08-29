package com.roberthevesi.cryptoshred_health.repository;

import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientVisitRepository extends JpaRepository<PatientVisit, UUID> {
    List<PatientVisit> findByOwnerId(UUID ownerId);
    List<PatientVisit> findByShreddedFalse();
    Optional<PatientVisit> findByEncryptionKey(com.roberthevesi.cryptoshred_health.model.EncryptionKey encryptionKey);

    @Query("SELECT v FROM PatientVisit v WHERE v.patient.patientId = :patientId")
    List<PatientVisit> findByPatientIdentifier(String patientId);

    @Query("SELECT COUNT(v) FROM PatientVisit v WHERE v.patient.patientId = :patientId AND v.shredded = false")
    int countActiveByPatientIdentifier(String patientId);

    @Query("SELECT COUNT(v) FROM PatientVisit v WHERE v.patient.patientId = :patientId")
    int countByPatientIdentifier(String patientId);

    @Query("SELECT DISTINCT v FROM PatientVisit v LEFT JOIN FETCH v.encryptionKey")
    List<PatientVisit> findAllWithEncryptionKey();

    @Query("SELECT v FROM PatientVisit v LEFT JOIN FETCH v.patient p LEFT JOIN FETCH v.encryptionKey k WHERE (p.id = :patientId OR p.patientId = :patientBusinessId) ORDER BY v.createdAt ASC")
    List<PatientVisit> findAllByPatientComprehensive(@Param("patientId") UUID patientId, @Param("patientBusinessId") String patientBusinessId);
}
