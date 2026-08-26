package com.roberthevesi.cryptoshred_health.config;

import com.roberthevesi.cryptoshred_health.config.SyntheticMedicalDataGenerator.AttachmentSpec;
import com.roberthevesi.cryptoshred_health.config.SyntheticMedicalDataGenerator.InMemoryMultipartFile;
import com.roberthevesi.cryptoshred_health.config.SyntheticMedicalDataGenerator.PatientTemplate;
import com.roberthevesi.cryptoshred_health.dto.GpRequest;
import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitRequest;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import com.roberthevesi.cryptoshred_health.service.AttachmentService;
import com.roberthevesi.cryptoshred_health.service.GpService;
import com.roberthevesi.cryptoshred_health.service.PatientService;
import com.roberthevesi.cryptoshred_health.service.PatientVisitService;
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
    private final PatientService patientService;
    private final PatientVisitService visitService;
    private final GpService gpService;
    private final AttachmentService attachmentService;
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
