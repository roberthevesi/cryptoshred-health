package com.roberthevesi.cryptoshred_health.repository;

import com.roberthevesi.cryptoshred_health.model.PatientAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientAttachmentRepository extends JpaRepository<PatientAttachment, UUID> {
    List<PatientAttachment> findByPatientRecordId(UUID patientRecordId);
}
