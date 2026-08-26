package com.roberthevesi.cryptoshred_health.repository;

import com.roberthevesi.cryptoshred_health.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByPatientId(String patientId);
    Optional<Patient> findByEmailIgnoreCase(String email);
    Optional<Patient> findByEncryptionKey(com.roberthevesi.cryptoshred_health.model.EncryptionKey encryptionKey);
    List<Patient> findByIsActiveTrue();
    List<Patient> findByGpId(UUID gpId);
    List<Patient> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrPatientIdContainingIgnoreCase(String first, String last, String pid);
    boolean existsByPatientId(String patientId);
    boolean existsByNhsNumber(String nhsNumber);
}
