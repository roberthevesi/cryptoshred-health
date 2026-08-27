package com.roberthevesi.cryptoshred_health.repository;

import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByPatientId(String patientId);
    Optional<Patient> findByUser(User user);
    Optional<Patient> findByUserId(UUID userId);
    Optional<Patient> findByEncryptionKey(EncryptionKey encryptionKey);
    List<Patient> findByIsActiveTrue();
    List<Patient> findByGpId(UUID gpId);
    boolean existsByPatientId(String patientId);
}
