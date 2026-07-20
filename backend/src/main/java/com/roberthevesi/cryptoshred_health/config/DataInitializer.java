package com.roberthevesi.cryptoshred_health.config;

import com.roberthevesi.cryptoshred_health.model.*;
import com.roberthevesi.cryptoshred_health.repository.EncryptionKeyRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRecordRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PatientRecordRepository recordRepository;
    private final EncryptionKeyRepository keyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already seeded. Skipping initial data generation.");
            return;
        }

        log.info("Seeding initial EHR demo accounts and patient records...");

        // 1. Create Default Users
        User doctor = createUser("doctor@hospital.com", "Password123!", Role.DOCTOR);
        User auditor = createUser("auditor@health.gov", "Password123!", Role.AUDITOR);
        User patient = createUser("patient@health.org", "Password123!", Role.PATIENT);

        // 2. Create Sample Patient Records with PDF Attachments
        createRecord(
                doctor,
                "Eleanor Vance",
                "MRN-90482",
                "1985-04-12",
                "Female",
                "A+",
                "124/82 mmHg",
                74,
                "Penicillin, Latex",
                "Metformin 500mg BD, Lisinopril 10mg OD",
                "Type 2 Diabetes Mellitus & Essential Hypertension",
                "Patient presents for routine 6-month checkup. HbA1c level is 7.2%. Blood pressure well-controlled on current medication. Recommended dietary modifications.",
                "Lab_Report_HbA1c.pdf",
                generateSamplePdfContent("ELEANOR VANCE", "MRN-90482", "HbA1c Blood Panel Report - Result: 7.2% (Controlled)")
        );

        createRecord(
                doctor,
                "Marcus Thorne",
                "MRN-81104",
                "1992-09-25",
                "Male",
                "O-",
                "138/88 mmHg",
                88,
                "None Known",
                "Amoxicillin 500mg TDS (7 days), Ibuprofen 400mg PRN",
                "Acute Appendicitis - Post-operative Follow-up",
                "Successful laparoscopic appendectomy performed 2 weeks ago. Surgical incisions healing clean with no signs of infection. Patient reports minimal localized discomfort.",
                "Ultrasound_Scan_Report.pdf",
                generateSamplePdfContent("MARCUS THORNE", "MRN-81104", "Abdominal Ultrasound Scan - Findings: Post-op Normal Recovery")
        );

        createRecord(
                doctor,
                "Sarah Jenkins",
                "MRN-72319",
                "1978-11-03",
                "Female",
                "B+",
                "142/90 mmHg",
                82,
                "Sulfa Drugs",
                "Atorvastatin 20mg OD, Amlodipine 5mg OD",
                "Hyperlipidemia & Stage 1 Hypertension",
                "Cardiology consultation summary. Lipid panel shows elevated LDL (160 mg/dL). Initiated statin therapy and scheduled follow-up lipid panel in 12 weeks.",
                "Cardiology_ECG_Summary.pdf",
                generateSamplePdfContent("SARAH JENKINS", "MRN-72319", "12-Lead Electrocardiogram (ECG) - Result: Normal Sinus Rhythm")
        );

        log.info("Seeding completed successfully! Demo accounts: doctor@hospital.com, auditor@health.gov, patient@health.org (Password123!)");
    }

    private User createUser(String email, String password, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return userRepository.save(user);
    }

    private void createRecord(
            User doctor,
            String patientName,
            String mrn,
            String dob,
            String gender,
            String bloodType,
            String bp,
            int hr,
            String allergies,
            String prescriptions,
            String diagnosis,
            String notes,
            String pdfName,
            byte[] pdfBytes) {

        String keyId = UUID.randomUUID().toString();
        String mockKeyValue = Base64.getEncoder().encodeToString((keyId + "_aes256_key").getBytes());
        EncryptionKey key = new EncryptionKey(keyId, mockKeyValue);

        PatientRecord record = new PatientRecord();
        record.setPatientName(patientName);
        record.setMrn(mrn);
        record.setDateOfBirth(dob);
        record.setGender(gender);
        record.setBloodType(bloodType);
        record.setBloodPressure(bp);
        record.setHeartRate(hr);
        record.setAllergies(allergies);
        record.setPrescriptions(prescriptions);
        record.setDiagnosis(diagnosis);
        record.setMedicalNotes(notes);
        record.setEncryptedDataBlob(Base64.getEncoder().encodeToString(notes.getBytes()));
        record.setEncryptionKey(key);
        record.setOwner(doctor);

        PatientAttachment attachment = new PatientAttachment();
        attachment.setFileName(pdfName);
        attachment.setContentType("application/pdf");
        attachment.setFileSize(pdfBytes.length);
        attachment.setEncryptedDataBlob(Base64.getEncoder().encodeToString(pdfBytes));
        attachment.setPatientRecord(record);

        record.getAttachments().add(attachment);

        recordRepository.save(record);
    }

    private byte[] generateSamplePdfContent(String patientName, String mrn, String reportDetails) {
        String mockPdfHeader = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n5 0 obj\n<< /Length 200 >>\nstream\nBT\n/F1 14 Tf\n50 750 Td\n(CRYPTOSHRED HEALTH EHR MEDICAL REPORT) Tj\n0 -30 Td\n(Patient: " + patientName + ") Tj\n0 -20 Td\n(MRN: " + mrn + ") Tj\n0 -30 Td\n(" + reportDetails + ") Tj\nET\nendstream\nendobj\nxref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000220 00000 n \n0000000293 00000 n \ntrailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n550\n%%EOF";
        return mockPdfHeader.getBytes();
    }
}
