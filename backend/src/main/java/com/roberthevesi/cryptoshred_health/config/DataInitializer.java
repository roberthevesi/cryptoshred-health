package com.roberthevesi.cryptoshred_health.config;

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
import com.roberthevesi.cryptoshred_health.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

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
    private final JdbcTemplate jdbcTemplate;
    private final SyntheticMedicalDataGenerator dataGenerator;

    @Override
    public void run(String... args) {
        // 0. Drop legacy PostgreSQL enum check constraint if it exists from earlier schema versions
        try {
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        } catch (Exception e) {
            log.debug("users_role_check constraint check: {}", e.getMessage());
        }

        // 1. Create Default Demo Users if missing
        User doctor = userRepository.findByEmail("doctor@hospital.com").orElse(null);
        if (doctor == null) {
            doctor = createUser("doctor@hospital.com", "Password123!", Role.DOCTOR);
            log.info("Created default demo doctor account: doctor@hospital.com");
        }
        if (userRepository.findByEmail("auditor@health.gov").isEmpty()) {
            createUser("auditor@health.gov", "Password123!", Role.AUDITOR);
            log.info("Created default demo auditor account: auditor@health.gov");
        }
        if (userRepository.findByEmail("patient@health.org").isEmpty()) {
            createUser("patient@health.org", "Password123!", Role.PATIENT);
            log.info("Created default demo patient account: patient@health.org");
        }
        if (userRepository.findByEmail("admin@cryptoshred.health").isEmpty()) {
            createUser("admin@cryptoshred.health", "Password123!", Role.ADMIN);
            log.info("Created default admin account: admin@cryptoshred.health");
        }

        // 2. Seed NHS General Practitioners & Specialists across 10 specialties
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
        List<GpResponse> seededGps = gpService.findAll();
        log.info("NHS GP & Specialist directory initialized with {} active clinicians.", seededGps.size());

        // 3. Seed 100 Master Patients with 10 Clinical Visits Each (1,000 total visits)
        long currentPatientCount = patientRepository.count();
        if (currentPatientCount < 100) {
            log.info("Seeding 100 master patients with 10 clinical visits each across 10 medical specialties (current count: {})...", currentPatientCount);

            List<PatientTemplate> patientTemplates = dataGenerator.getPredefinedPatients();
            int totalVisitsSeeded = 0;
            int totalAttachmentsSeeded = 0;

            for (int i = 0; i < patientTemplates.size(); i++) {
                PatientTemplate pt = patientTemplates.get(i);

                if (pt.email() != null && !pt.email().isBlank() && userRepository.findByEmail(pt.email()).isEmpty()) {
                    createUser(pt.email(), "Password123!", Role.PATIENT);
                }

                if (patientRepository.findByPatientId(pt.patientId()).isPresent()) {
                    continue;
                }

                try {
                    // Create Patient with Vault Demographics Envelope Encryption
                    PatientRequest pReq = dataGenerator.toPatientRequest(pt, seededGps);
                    PatientResponse savedPatient = patientService.create(pReq);

                    // Generate 10 Clinical Visits for this Patient
                    List<PatientVisitRequest> visitRequests = dataGenerator.generateVisitsForPatient(pt, i, seededGps);
                    for (int v = 0; v < visitRequests.size(); v++) {
                        PatientVisitRequest vReq = visitRequests.get(v);
                        var savedVisit = visitService.create(vReq, doctor.getEmail());
                        totalVisitsSeeded++;

                        // Attach Diagnostic PDF Reports on Selected Visits
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
                                        doctor.getEmail()
                                );
                                totalAttachmentsSeeded++;
                            } catch (Exception attEx) {
                                log.warn("Could not upload sample attachment {} for visit {}: {}", spec.fileName(), savedVisit.getId(), attEx.getMessage());
                            }
                        }
                    }

                    if ((i + 1) % 10 == 0 || (i + 1) == patientTemplates.size()) {
                        log.info("Seeded patient {}/100 with 10 clinical visits (total visits: {})...", i + 1, totalVisitsSeeded);
                    }
                } catch (Exception e) {
                    log.error("Failed to seed patient {}: {}", pt.patientId(), e.getMessage(), e);
                }
            }

            log.info("Clinical data seeding completed! Total patients: {}, Total visits: {}, Diagnostic PDF attachments: {}.",
                    patientRepository.count(), totalVisitsSeeded, totalAttachmentsSeeded);
        } else {
            log.info("Database already contains {} patients. Skipping synthetic data seeding.", currentPatientCount);
        }

        // 4. Seed Historical GDPR Art. 17 Deletions & Merkle Tree DAG if Merkle tree is empty
        long currentMerkleLeaves = merkleNodeRepository.count();
        if (currentMerkleLeaves < 25) {
            log.info("Initializing Historical Merkle DAG & GDPR Art. 17 Deletion Ledger (current leaves: {})...", currentMerkleLeaves);

            // A. Seed and crypto-shred 15 whole-patient profiles (PAT-10101 to PAT-10115) + their cascaded visits
            List<PatientTemplate> historicalPatients = dataGenerator.getHistoricalErasurePatients();
            for (int i = 0; i < historicalPatients.size(); i++) {
                PatientTemplate pt = historicalPatients.get(i);
                Patient existingPatient = patientRepository.findByPatientId(pt.patientId()).orElse(null);
                if (existingPatient == null) {
                    try {
                        PatientRequest pReq = dataGenerator.toPatientRequest(pt, seededGps);
                        PatientResponse savedPatient = patientService.create(pReq);

                        // Seed 5 visits for this patient
                        List<PatientVisitRequest> visits = dataGenerator.generateVisitsForPatient(pt, i + 100, seededGps).subList(0, 5);
                        for (PatientVisitRequest vReq : visits) {
                            visitService.create(vReq, doctor.getEmail());
                        }

                        // Execute Verifiable Crypto-Shredding (destroys Vault KEK, cascaded visit KEKs, mints proofs, updates Merkle DAG)
                        erasureService.forgetPatient(
                                pt.patientId(),
                                "dpo@hospital.nhs.uk"
                        );
                    } catch (Exception e) {
                        log.warn("Could not pre-shred patient {}: {}", pt.patientId(), e.getMessage());
                    }
                } else if (!existingPatient.isShredded()) {
                    try {
                        erasureService.forgetPatient(
                                pt.patientId(),
                                "dpo@hospital.nhs.uk"
                        );
                    } catch (Exception e) {
                        log.warn("Could not pre-shred existing patient {}: {}", pt.patientId(), e.getMessage());
                    }
                }
            }

            // B. Crypto-shred 10 individual clinical encounters on active patients (PAT-10002 to PAT-10011)
            for (int i = 2; i <= 11; i++) {
                String pid = String.format("PAT-10%03d", i);
                List<PatientVisit> visits = patientVisitRepository.findByPatientIdentifier(pid);
                if (!visits.isEmpty()) {
                    PatientVisit v = visits.get(0);
                    if (!v.isShredded()) {
                        try {
                            erasureService.forgetVisit(
                                    v.getId(),
                                    "patient@health.org"
                            );
                        } catch (Exception ex) {
                            log.warn("Could not pre-shred visit {}: {}", v.getId(), ex.getMessage());
                        }
                    }
                }
            }

            log.info("Historical Merkle DAG and GDPR Art. 17 Deletion Ledger initialized with {} verifiable deletion proofs (Active Root R: {}).",
                    merkleNodeRepository.count(), merkleTreeService.getMerkleRoot());
        }

        // 5. Warm Redis cache for active patient demographics
        try {
            patientService.findAll(false);
            log.info("Redis L2 cache warmed for all active patient demographic profiles (patient:*).");
        } catch (Exception e) {
            log.warn("Redis cache pre-warming skipped: {}", e.getMessage());
        }

        log.info("CryptoShred Health EHR readiness verified. Demo accounts: doctor@hospital.com, auditor@health.gov, patient@health.org, admin@cryptoshred.health");
    }

    private User createUser(String email, String password, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return userRepository.save(user);
    }
}
