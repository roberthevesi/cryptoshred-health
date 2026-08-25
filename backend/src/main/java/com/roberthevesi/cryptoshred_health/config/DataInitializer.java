package com.roberthevesi.cryptoshred_health.config;

import com.roberthevesi.cryptoshred_health.dto.GpRequest;
import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitRequest;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import com.roberthevesi.cryptoshred_health.service.AttachmentService;
import com.roberthevesi.cryptoshred_health.service.GpService;
import com.roberthevesi.cryptoshred_health.service.PatientService;
import com.roberthevesi.cryptoshred_health.service.PatientVisitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PatientService patientService;
    private final PatientVisitService visitService;
    private final GpService gpService;
    private final AttachmentService attachmentService;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        // 0. Drop legacy PostgreSQL enum check constraint if it exists from earlier schema versions
        try {
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        } catch (Exception e) {
            log.debug("users_role_check constraint check: {}", e.getMessage());
        }

        // 1. Create Default Users if missing
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

        // 2. Create Sample GP Surgery Practices if missing
        GpResponse savedGp1;
        GpResponse savedGp2;
        var existingGps = gpService.findAll();
        if (existingGps.isEmpty()) {
            GpRequest gp1 = new GpRequest();
            gp1.setFirstName("Alistair");
            gp1.setLastName("Finch");
            gp1.setEmail("dr.finch@stmarys-surgery.nhs.uk");
            gp1.setPhoneNumber("+44 20 7946 0192");
            gp1.setGmcNumber("GMC-7412984");
            gp1.setSpecialisation("General Practice & Family Medicine");
            gp1.setPracticeName("St Mary's Health Centre, London");
            savedGp1 = gpService.create(gp1);

            GpRequest gp2 = new GpRequest();
            gp2.setFirstName("Clara");
            gp2.setLastName("Oswald");
            gp2.setEmail("dr.oswald@bakerst-medical.nhs.uk");
            gp2.setPhoneNumber("+44 20 7946 0833");
            gp2.setGmcNumber("GMC-6391024");
            gp2.setSpecialisation("Cardiology & Internal Medicine");
            gp2.setPracticeName("Baker Street Medical Practice, London");
            savedGp2 = gpService.create(gp2);
            log.info("Created default GP practice directories: Dr. Finch and Dr. Oswald");
        } else {
            savedGp1 = existingGps.get(0);
            savedGp2 = existingGps.size() > 1 ? existingGps.get(1) : existingGps.get(0);
        }

        // 3. Create Sample Master Patients & Visits if missing
        if (patientService.findAll().isEmpty()) {
            log.info("Seeding initial EHR demo Patients and Visits with nested Vault Transit keys (patients/{patientUuid})...");

            PatientRequest p1 = new PatientRequest();
            p1.setPatientId("PAT-10001");
            p1.setFirstName("Eleanor");
            p1.setLastName("Vance");
            p1.setDateOfBirth("1985-04-12");
            p1.setGender("Female");
            p1.setEmail("eleanor.vance@example.com");
            p1.setPhoneNumber("+44 7700 900142");
            p1.setAddress("42 Hill House Lane, London NW1 4NP");
            p1.setNhsNumber("943 476 5919");
            p1.setGpId(savedGp1.getId());
            PatientResponse patient1 = patientService.create(p1);

            PatientRequest p2 = new PatientRequest();
            p2.setPatientId("PAT-10002");
            p2.setFirstName("Marcus");
            p2.setLastName("Thorne");
            p2.setDateOfBirth("1992-09-25");
            p2.setGender("Male");
            p2.setEmail("marcus.thorne@example.com");
            p2.setPhoneNumber("+44 7700 900881");
            p2.setAddress("17 Kensington Church Walk, London W8 4NB");
            p2.setNhsNumber("485 910 2384");
            p2.setGpId(savedGp1.getId());
            PatientResponse patient2 = patientService.create(p2);

            PatientRequest p3 = new PatientRequest();
            p3.setPatientId("PAT-10003");
            p3.setFirstName("Sarah");
            p3.setLastName("Jenkins");
            p3.setDateOfBirth("1978-11-03");
            p3.setGender("Female");
            p3.setEmail("sarah.jenkins@example.com");
            p3.setPhoneNumber("+44 7700 900319");
            p3.setAddress("88 Bloomsbury Way, London WC1A 2SE");
            p3.setNhsNumber("712 849 3015");
            p3.setGpId(savedGp2.getId());
            PatientResponse patient3 = patientService.create(p3);


        // 4. Create Sample Clinical Visits with full SOAP notes & attachments
        try {
            PatientVisitRequest r1 = new PatientVisitRequest();
            r1.setPatientName("Eleanor Vance");
            r1.setMrn(patient1.getPatientId());
            r1.setDateOfBirth("1985-04-12");
            r1.setGender("Female");
            r1.setBloodType("A+");
            r1.setBloodPressure("124/82 mmHg");
            r1.setHeartRate(74);
            r1.setRespiratoryRate("16 breaths/min");
            r1.setTemperature("36.8 °C");
            r1.setOxygenSaturation("99%");
            r1.setHeightCm("168 cm");
            r1.setWeightKg("64.5 kg");
            r1.setBmi("22.9");
            r1.setPainScore(0);
            r1.setAllergies("Penicillin, Latex");
            r1.setPrescriptions("Metformin 500mg BD, Lisinopril 10mg OD");
            r1.setChiefComplaint("Routine 6-month diabetes follow-up");
            r1.setChronicConditions("Type 2 Diabetes Mellitus, Essential Hypertension");
            r1.setDiagnosis("Type 2 Diabetes Mellitus — Well Controlled");
            r1.setSoapSubjective("Patient reports compliance with medication and diet. No hypoglycemic episodes.");
            r1.setSoapObjective("BP 124/82, HR 74 regular. HbA1c 7.2%. Foot examination normal.");
            r1.setSoapAssessment("T2DM and hypertension well controlled on current regimen.");
            r1.setSoapPlan("Continue Metformin 500mg BD and Lisinopril 10mg OD. Repeat HbA1c in 6 months.");
            r1.setAttendingDoctor("Dr. Alistair Finch");
            r1.setDepartment("General Practice");
            var visit1 = visitService.create(r1, doctor.getEmail());

            byte[] pdfBytes1 = generateSamplePdfContent("ELEANOR VANCE", "PAT-10001", "HbA1c Blood Panel Report - Result: 7.2% (Controlled)");
            attachmentService.uploadAttachment(visit1.getId(), new InMemoryMultipartFile("Lab_Report_HbA1c.pdf", "application/pdf", pdfBytes1), doctor.getEmail());

            PatientVisitRequest r2 = new PatientVisitRequest();
            r2.setPatientName("Marcus Thorne");
            r2.setMrn(patient2.getPatientId());
            r2.setDateOfBirth("1992-09-25");
            r2.setGender("Male");
            r2.setBloodType("O-");
            r2.setBloodPressure("138/88 mmHg");
            r2.setHeartRate(88);
            r2.setRespiratoryRate("18 breaths/min");
            r2.setTemperature("37.1 °C");
            r2.setOxygenSaturation("98%");
            r2.setHeightCm("182 cm");
            r2.setWeightKg("79.0 kg");
            r2.setBmi("23.8");
            r2.setPainScore(2);
            r2.setAllergies("None Known");
            r2.setPrescriptions("Amoxicillin 500mg TDS (7 days), Ibuprofen 400mg PRN");
            r2.setChiefComplaint("Post-operative follow-up 2 weeks post-laparoscopic appendectomy");
            r2.setDiagnosis("Acute Appendicitis — Post-operative Recovery");
            r2.setSoapSubjective("Patient reports mild residual discomfort around trocar sites, improving daily.");
            r2.setSoapObjective("Abdomen soft, non-tender. Incisions clean and dry with no erythema or discharge.");
            r2.setSoapAssessment("Uncomplicated surgical recovery following laparoscopic appendectomy.");
            r2.setSoapPlan("Discontinue antibiotics at end of course. Resume full physical activity in 2 weeks.");
            r2.setAttendingDoctor("Dr. Alistair Finch");
            r2.setDepartment("General Surgery Follow-up");
            var visit2 = visitService.create(r2, doctor.getEmail());

            byte[] pdfBytes2 = generateSamplePdfContent("MARCUS THORNE", "PAT-10002", "Post-op Ultrasound & Surgical Pathology Summary");
            attachmentService.uploadAttachment(visit2.getId(), new InMemoryMultipartFile("Ultrasound_Scan_Report.pdf", "application/pdf", pdfBytes2), doctor.getEmail());

            PatientVisitRequest r3 = new PatientVisitRequest();
            r3.setPatientName("Sarah Jenkins");
            r3.setMrn(patient3.getPatientId());
            r3.setDateOfBirth("1978-11-03");
            r3.setGender("Female");
            r3.setBloodType("B+");
            r3.setBloodPressure("142/90 mmHg");
            r3.setHeartRate(82);
            r3.setRespiratoryRate("14 breaths/min");
            r3.setTemperature("36.6 °C");
            r3.setOxygenSaturation("99%");
            r3.setHeightCm("165 cm");
            r3.setWeightKg("72.0 kg");
            r3.setBmi("26.4");
            r3.setPainScore(0);
            r3.setAllergies("Sulfa Drugs");
            r3.setPrescriptions("Atorvastatin 20mg OD, Amlodipine 5mg OD");
            r3.setChiefComplaint("Cardiology review for elevated cholesterol");
            r3.setDiagnosis("Hyperlipidemia & Stage 1 Hypertension");
            r3.setSoapSubjective("Asymptomatic. No chest pain, palpitations, or shortness of breath.");
            r3.setSoapObjective("BP 142/90 mmHg. Heart sounds normal S1+S2. Lipid panel LDL 160 mg/dL.");
            r3.setSoapAssessment("Primary hypercholesterolemia with borderline Stage 1 hypertension.");
            r3.setSoapPlan("Start Atorvastatin 20mg nocte. Lifestyle and dietary consultation scheduled.");
            r3.setAttendingDoctor("Dr. Clara Oswald");
            r3.setDepartment("Cardiology Clinic");
            var visit3 = visitService.create(r3, doctor.getEmail());

            byte[] pdfBytes3 = generateSamplePdfContent("SARAH JENKINS", "PAT-10003", "12-Lead Electrocardiogram (ECG) - Result: Normal Sinus Rhythm");
            attachmentService.uploadAttachment(visit3.getId(), new InMemoryMultipartFile("Cardiology_ECG_Summary.pdf", "application/pdf", pdfBytes3), doctor.getEmail());
        } catch (Exception e) {
            log.error("Failed to seed sample patient visits: {}", e.getMessage(), e);
        }
        }

        log.info("Seeding completed successfully! Demo accounts: doctor@hospital.com, auditor@health.gov, patient@health.org, admin@cryptoshred.health");
    }

    private User createUser(String email, String password, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return userRepository.save(user);
    }

    private byte[] generateSamplePdfContent(String patientName, String mrn, String reportDetails) {
        String mockPdfHeader = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n5 0 obj\n<< /Length 200 >>\nstream\nBT\n/F1 14 Tf\n50 750 Td\n(CRYPTOSHRED HEALTH EHR MEDICAL REPORT) Tj\n0 -30 Td\n(Patient: " + patientName + ") Tj\n0 -20 Td\n(MRN: " + mrn + ") Tj\n0 -30 Td\n(" + reportDetails + ") Tj\nET\nendstream\nendobj\nxref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000220 00000 n \n0000000293 00000 n \ntrailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n550\n%%EOF";
        return mockPdfHeader.getBytes();
    }

    private static class InMemoryMultipartFile implements MultipartFile {
        private final String filename;
        private final String contentType;
        private final byte[] content;

        public InMemoryMultipartFile(String filename, String contentType, byte[] content) {
            this.filename = filename;
            this.contentType = contentType;
            this.content = content != null ? content : new byte[0];
        }

        @Override public String getName() { return filename; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) { throw new UnsupportedOperationException(); }
    }
}
