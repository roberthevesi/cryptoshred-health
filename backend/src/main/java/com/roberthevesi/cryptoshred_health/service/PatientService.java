package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.model.GP;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.repository.GpRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;
    private final GpRepository gpRepository;

    public List<PatientResponse> findAll() {
        return patientRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PatientResponse findByPatientId(String patientId) {
        return patientRepository.findByPatientId(patientId)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
    }

    public List<PatientResponse> search(String query) {
        return patientRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrPatientIdContainingIgnoreCase(query, query, query).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<PatientResponse> findByGp(UUID gpId) {
        return patientRepository.findByGpId(gpId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PatientResponse create(PatientRequest request) {
        Patient patient = new Patient();
        
        String patientId = request.getPatientId();
        if (patientId == null || patientId.trim().isBlank()) {
            patientId = generateUniquePatientId();
        } else {
            patientId = patientId.trim();
            if (patientRepository.findByPatientId(patientId).isPresent()) {
                throw new IllegalArgumentException("Patient ID '" + patientId + "' is already assigned to another patient.");
            }
        }
        patient.setPatientId(patientId);

        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        
        if (request.getDateOfBirth() != null && !request.getDateOfBirth().isBlank()) {
            patient.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
        }
        
        patient.setGender(request.getGender());
        patient.setEmail(request.getEmail());
        patient.setPhoneNumber(request.getPhoneNumber());
        patient.setAddress(request.getAddress());
        patient.setNhsNumber(request.getNhsNumber());
        
        if (request.getGpId() != null) {
            GP gp = gpRepository.findById(request.getGpId())
                    .orElseThrow(() -> new RuntimeException("GP not found"));
            patient.setGp(gp);
        }
        
        return toResponse(patientRepository.save(patient));
    }

    private String generateUniquePatientId() {
        String generated;
        do {
            int randomNum = (int) (Math.random() * 90000) + 10000;
            generated = "PAT-" + randomNum;
        } while (patientRepository.findByPatientId(generated).isPresent());
        return generated;
    }

    public PatientResponse update(String patientId, PatientRequest request) {
        Patient patient = patientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
                
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        
        if (request.getDateOfBirth() != null && !request.getDateOfBirth().isBlank()) {
            patient.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
        } else {
            patient.setDateOfBirth(null);
        }
        
        patient.setGender(request.getGender());
        patient.setEmail(request.getEmail());
        patient.setPhoneNumber(request.getPhoneNumber());
        patient.setAddress(request.getAddress());
        patient.setNhsNumber(request.getNhsNumber());
        
        if (request.getGpId() != null) {
            GP gp = gpRepository.findById(request.getGpId())
                    .orElseThrow(() -> new RuntimeException("GP not found"));
            patient.setGp(gp);
        } else {
            patient.setGp(null);
        }
        
        return toResponse(patientRepository.save(patient));
    }

    public void deactivate(String patientId) {
        Patient patient = patientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        patient.setActive(false);
        patientRepository.save(patient);
    }
    
    public void anonymise(String patientId) {
        Patient patient = patientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        
        patient.setFirstName("[REDACTED]");
        patient.setLastName("[REDACTED]");
        patient.setEmail(null);
        patient.setPhoneNumber(null);
        patient.setAddress(null);
        patient.setNhsNumber(null);
        patient.setDateOfBirth(null);
        patient.setActive(false);
        
        patientRepository.save(patient);
    }

    private PatientResponse toResponse(Patient patient) {
        return PatientResponse.builder()
                .id(patient.getId())
                .patientId(patient.getPatientId())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .dateOfBirth(patient.getDateOfBirth())
                .gender(patient.getGender())
                .email(patient.getEmail())
                .phoneNumber(patient.getPhoneNumber())
                .address(patient.getAddress())
                .nhsNumber(patient.getNhsNumber())
                .gp(patient.getGp() != null ? toGpResponse(patient.getGp()) : null)
                .isActive(patient.isActive())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .build();
    }
    
    private GpResponse toGpResponse(GP gp) {
        return GpResponse.builder()
                .id(gp.getId())
                .firstName(gp.getFirstName())
                .lastName(gp.getLastName())
                .email(gp.getEmail())
                .phoneNumber(gp.getPhoneNumber())
                .gmcNumber(gp.getGmcNumber())
                .specialisation(gp.getSpecialisation())
                .practiceName(gp.getPracticeName())
                .isActive(gp.isActive())
                .createdAt(gp.getCreatedAt())
                .build();
    }
}
