package com.roberthevesi.cryptoshred_health.security;

import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Evaluates patient-level authorization checks in Spring Security SpEL expressions.
 */
@Service("patientSecurityService")
@RequiredArgsConstructor
@Slf4j
public class PatientSecurityService {

    private final PatientRepository patientRepository;

    /**
     * Determines whether the currently authenticated principal owns the patient record identified by patientId.
     *
     * @param authentication Current security authentication context
     * @param patientId The target patient identifier (e.g., PAT-49201)
     * @return true if the principal's email matches the patient's record, false otherwise
     */
    public boolean isSelf(Authentication authentication, String patientId) {
        if (authentication == null || authentication.getName() == null || patientId == null || patientId.isBlank()) {
            return false;
        }

        String currentUserEmail = authentication.getName();
        Optional<Patient> patientOpt = patientRepository.findByEmailIgnoreCase(currentUserEmail);

        if (patientOpt.isEmpty()) {
            log.debug("No patient record found for user email: {}", currentUserEmail);
            return false;
        }

        Patient patient = patientOpt.get();
        boolean matches = patient.getPatientId() != null && patient.getPatientId().equalsIgnoreCase(patientId.trim());
        log.debug("PatientSecurityService.isSelf evaluated to {} for user: {} and patientId: {}", matches, currentUserEmail, patientId);
        return matches;
    }
}
