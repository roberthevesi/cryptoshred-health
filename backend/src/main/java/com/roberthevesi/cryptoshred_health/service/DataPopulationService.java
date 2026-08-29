package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.config.SyntheticMedicalDataGenerator;
import com.roberthevesi.cryptoshred_health.config.SyntheticMedicalDataGenerator.AttachmentSpec;
import com.roberthevesi.cryptoshred_health.config.SyntheticMedicalDataGenerator.InMemoryMultipartFile;
import com.roberthevesi.cryptoshred_health.config.SyntheticMedicalDataGenerator.PatientTemplate;
import com.roberthevesi.cryptoshred_health.dto.GpRequest;
import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitRequest;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.MerkleNodeRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DataPopulationService — Dedicated on-demand service for seeding synthetic clinical datasets.
 * Default application startup ONLY seeds core authentication users and clinicians;
 * full demographic/visit population and Merkle DAG history is triggered on-demand via Admin REST API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataPopulationService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PatientVisitRepository patientVisitRepository;
    private final PatientService patientService;
    private final PatientVisitService visitService;
    private final GpService gpService;
    private final AttachmentService attachmentService;
    private final ErasureService erasureService;
    private final MerkleNodeRepository merkleNodeRepository;
    private final MerkleTreeService merkleTreeService;
    private final PasswordEncoder passwordEncoder;
    private final SyntheticMedicalDataGenerator dataGenerator;

    /**
     * Seeds default NHS clinicians (GPs) if missing.
     */
    public List<GpResponse> seedCliniciansIfMissing() {
        List<GpResponse> existingGps = gpService.findAll();
        Set<String> existingGmcNumbers = existingGps.stream()
                .map(GpResponse::getGmcNumber)
                .collect(Collectors.toSet());

        List<GpRequest> predefinedGps = dataGenerator.getPredefinedGps();
        for (GpRequest gpReq : predefinedGps) {
            if (!existingGmcNumbers.contains(gpReq.getGmcNumber())) {
                try {
                    gpService.create(gpReq);
                } catch (Exception e) {
                    log.error("Failed to seed GP {}: {}", gpReq.getLastName(), e.getMessage());
                }
            }
        }
        return gpService.findAll();
    }

    /**
     * Seeds default accounts (DOCTOR, AUDITOR, PATIENT, ADMIN).
     */
    public void seedDefaultAccounts() {
        if (userRepository.findByEmail("doctor@hospital.com").isEmpty()) {
            createUser("doctor@hospital.com", "Password123!", Role.DOCTOR);
            log.info("Created default demo doctor account: doctor@hospital.com");
        }
        if (userRepository.findByEmail("auditor@health.gov").isEmpty()) {
            createUser("auditor@health.gov", "Password123!", Role.AUDITOR);
            log.info("Created default demo auditor account: auditor@health.gov");
        }
        if (userRepository.findByEmail("admin@cryptoshred.health").isEmpty()) {
            createUser("admin@cryptoshred.health", "Password123!", Role.ADMIN);
            log.info("Created default admin account: admin@cryptoshred.health");
        }
    }

    /**
     * On-demand database population: seeds 100 active patients with 10 visits each (and attachments)
     * plus 25 historical crypto-shredded records and populates the Merkle DAG deletion ledger.
     */
    public synchronized Map<String, Object> populateSyntheticData() {
        log.info("🚀 [DATA POPULATION] Admin triggered synthetic medical data generation...");

        seedDefaultAccounts();
        List<GpResponse> seededGps = seedCliniciansIfMissing();
        User doctor = userRepository.findByEmail("doctor@hospital.com").orElse(null);
        String doctorEmail = doctor != null ? doctor.getEmail() : "doctor@hospital.com";

        int totalPatientsSeeded = 0;
        int totalVisitsSeeded = 0;
        int totalAttachmentsSeeded = 0;

        // 1. Seed 100 Master Patients
        List<PatientTemplate> patientTemplates = dataGenerator.getPredefinedPatients();
        for (int i = 0; i < patientTemplates.size(); i++) {
            PatientTemplate pt = patientTemplates.get(i);

            if (pt.email() != null && !pt.email().isBlank() && userRepository.findByEmail(pt.email()).isEmpty()) {
                createUser(pt.email(), "Password123!", Role.PATIENT);
            }

            if (patientRepository.findByPatientId(pt.patientId()).isPresent()) {
                continue;
            }

            try {
                PatientRequest pReq = dataGenerator.toPatientRequest(pt, seededGps);
                PatientResponse savedPatient = patientService.create(pReq);
                totalPatientsSeeded++;

                List<PatientVisitRequest> visitRequests = dataGenerator.generateVisitsForPatient(pt, i, seededGps);
                for (int v = 0; v < visitRequests.size(); v++) {
                    PatientVisitRequest vReq = visitRequests.get(v);
                    var savedVisit = visitService.create(vReq, doctorEmail);
                    totalVisitsSeeded++;

                    List<AttachmentSpec> attSpecs = dataGenerator.getAttachmentSpecsForVisit(
                            v,
                            savedPatient.getFirstName() + " " + savedPatient.getLastName(),
                            savedPatient.getPatientId()
                    );

                    for (AttachmentSpec spec : attSpecs) {
                        try {
                            byte[] pdfBytes = dataGenerator.generateSamplePdfContent(
                                    savedPatient.getFirstName() + " " + savedPatient.getLastName(),
                                    savedPatient.getPatientId(),
                                    spec.title(),
                                    spec.details()
                            );
                            attachmentService.uploadAttachment(
                                    savedVisit.getId(),
                                    new InMemoryMultipartFile(spec.fileName(), "application/pdf", pdfBytes),
                                    doctorEmail
                            );
                            totalAttachmentsSeeded++;
                        } catch (Exception attEx) {
                            log.warn("Could not upload sample attachment {}: {}", spec.fileName(), attEx.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to seed patient {}: {}", pt.patientId(), e.getMessage());
            }
        }

        // 2. Seed 25 Historical Crypto-Shredded Records & Merkle DAG
        int shreddedCount = 0;
        List<PatientTemplate> historicalPatients = dataGenerator.getHistoricalErasurePatients();
        for (int i = 0; i < historicalPatients.size(); i++) {
            PatientTemplate pt = historicalPatients.get(i);
            Patient existingPatient = patientRepository.findByPatientId(pt.patientId()).orElse(null);
            if (existingPatient == null) {
                try {
                    PatientRequest pReq = dataGenerator.toPatientRequest(pt, seededGps);
                    PatientResponse savedPatient = patientService.create(pReq);

                    List<PatientVisitRequest> visits = dataGenerator.generateVisitsForPatient(pt, i + 100, seededGps).subList(0, 5);
                    for (PatientVisitRequest vReq : visits) {
                        visitService.create(vReq, doctorEmail);
                    }

                    erasureService.forgetPatient(pt.patientId(), "dpo@hospital.nhs.uk");
                    shreddedCount++;
                } catch (Exception e) {
                    log.warn("Could not pre-shred patient {}: {}", pt.patientId(), e.getMessage());
                }
            } else if (!existingPatient.isShredded()) {
                try {
                    erasureService.forgetPatient(pt.patientId(), "dpo@hospital.nhs.uk");
                    shreddedCount++;
                } catch (Exception e) {
                    log.warn("Could not pre-shred existing patient {}: {}", pt.patientId(), e.getMessage());
                }
            }
        }

        // Shred 10 individual clinical encounters on active patients
        for (int i = 2; i <= 11; i++) {
            String pid = String.format("PAT-10%03d", i);
            List<PatientVisit> visits = patientVisitRepository.findByPatientIdentifier(pid);
            if (!visits.isEmpty()) {
                PatientVisit v = visits.get(0);
                if (!v.isShredded()) {
                    try {
                        erasureService.forgetVisit(v.getId(), doctorEmail);
                        shreddedCount++;
                    } catch (Exception ex) {
                        log.warn("Could not pre-shred visit {}: {}", v.getId(), ex.getMessage());
                    }
                }
            }
        }

        // 3. Warm Redis cache
        try {
            patientService.findAll(false);
        } catch (Exception ignored) {}

        log.info("✅ [DATA POPULATION COMPLETE] Total Active Patients: {}, Total Visits: {}, Merkle Leaves: {}, Active Root: {}",
                patientRepository.count(), patientVisitRepository.count(), merkleNodeRepository.count(), merkleTreeService.getMerkleRoot());

        return Map.of(
                "status", "SUCCESS",
                "timestamp", LocalDateTime.now().toString(),
                "totalPatients", patientRepository.count(),
                "totalVisits", patientVisitRepository.count(),
                "totalAttachments", totalAttachmentsSeeded,
                "merkleLeaves", merkleNodeRepository.count(),
                "merkleRoot", merkleTreeService.getMerkleRoot() != null ? merkleTreeService.getMerkleRoot() : "N/A",
                "message", "Synthetic clinical data successfully generated with zero-plaintext encryption and Merkle DAG history."
        );
    }

    private User createUser(String email, String password, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return userRepository.save(user);
    }
}
