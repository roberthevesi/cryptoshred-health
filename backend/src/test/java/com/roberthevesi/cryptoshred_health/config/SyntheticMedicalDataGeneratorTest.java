package com.roberthevesi.cryptoshred_health.config;

import com.roberthevesi.cryptoshred_health.config.SyntheticMedicalDataGenerator.AttachmentSpec;
import com.roberthevesi.cryptoshred_health.config.SyntheticMedicalDataGenerator.PatientTemplate;
import com.roberthevesi.cryptoshred_health.dto.GpRequest;
import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SyntheticMedicalDataGeneratorTest {

    private SyntheticMedicalDataGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SyntheticMedicalDataGenerator();
    }

    @Test
    void testPredefinedGpsCoverage() {
        List<GpRequest> gps = generator.getPredefinedGps();
        assertNotNull(gps);
        assertTrue(gps.size() >= 10, "Should have at least 10 GPs/Specialists");

        Set<String> specialties = new HashSet<>();
        Set<String> gmcNumbers = new HashSet<>();

        for (GpRequest gp : gps) {
            assertNotNull(gp.getFirstName());
            assertNotNull(gp.getLastName());
            assertNotNull(gp.getEmail());
            assertNotNull(gp.getPhoneNumber());
            assertNotNull(gp.getGmcNumber());
            assertNotNull(gp.getSpecialisation());
            assertNotNull(gp.getPracticeName());

            assertTrue(gp.getGmcNumber().startsWith("GMC-"));
            assertTrue(gmcNumbers.add(gp.getGmcNumber()), "GMC numbers must be unique: " + gp.getGmcNumber());
            specialties.add(gp.getSpecialisation());
        }

        // Verify key medical specialties are covered
        List<String> requiredKeywords = List.of(
                "General Practice", "Cardiology", "Endocrinology", "Respiratory",
                "Neurology", "Orthopedic", "Dermatology", "Gastroenterology",
                "Rheumatology", "Psychiatry"
        );

        for (String keyword : requiredKeywords) {
            boolean covered = specialties.stream().anyMatch(s -> s.contains(keyword));
            assertTrue(covered, "Specialty matching keyword '" + keyword + "' should be present");
        }
    }

    @Test
    void test100PatientIdentitiesUniquenessAndDiversity() {
        List<PatientTemplate> patients = generator.getPredefinedPatients();
        assertNotNull(patients);
        assertEquals(100, patients.size(), "Must generate exactly 100 master patient templates");

        Set<String> patientIds = new HashSet<>();
        Set<String> nhsNumbers = new HashSet<>();
        Set<String> emails = new HashSet<>();

        Set<String> genders = new HashSet<>();
        Set<String> cities = new HashSet<>();

        LocalDate today = LocalDate.now();

        for (PatientTemplate pt : patients) {
            assertNotNull(pt.patientId());
            assertNotNull(pt.firstName());
            assertNotNull(pt.lastName());
            assertNotNull(pt.dateOfBirth());
            assertNotNull(pt.gender());
            assertNotNull(pt.email());
            assertNotNull(pt.phoneNumber());
            assertNotNull(pt.address());
            assertNotNull(pt.nhsNumber());
            assertNotNull(pt.bloodType());

            // Uniqueness
            assertTrue(patientIds.add(pt.patientId()), "Duplicate Patient ID: " + pt.patientId());
            assertTrue(nhsNumbers.add(pt.nhsNumber()), "Duplicate NHS Number: " + pt.nhsNumber());
            assertTrue(emails.add(pt.email()), "Duplicate Email: " + pt.email());

            // NHS Number Format: XXX XXX XXXX
            assertTrue(pt.nhsNumber().matches("^\\d{3} \\d{3} \\d{4}$"),
                    "NHS number must follow XXX XXX XXXX format: " + pt.nhsNumber());

            // UK Phone Number Format: +44 7700 900XXX
            assertTrue(pt.phoneNumber().startsWith("+44 7700 900"),
                    "UK phone number must start with +44 7700 900: " + pt.phoneNumber());

            // Age distribution (18 to 85)
            LocalDate dob = LocalDate.parse(pt.dateOfBirth());
            int age = Period.between(dob, today).getYears();
            assertTrue(age >= 18 && age <= 86, "Age must be between 18 and 85, was: " + age + " for DOB " + dob);

            genders.add(pt.gender());

            // Track UK Cities in addresses
            for (String city : List.of("London", "Manchester", "Birmingham", "Edinburgh", "Bristol", "Leeds", "Cambridge", "Oxford", "Cardiff", "Belfast")) {
                if (pt.address().contains(city)) {
                    cities.add(city);
                }
            }
        }

        // Verify diversity in gender
        assertTrue(genders.contains("Male"));
        assertTrue(genders.contains("Female"));
        assertTrue(genders.contains("Non-Binary"));

        // Verify UK geographic coverage across all 10 major hubs
        assertEquals(10, cities.size(), "Should cover all 10 UK cities across London, Manchester, Birmingham, Edinburgh, Bristol, Leeds, Cambridge, Oxford, Cardiff, Belfast");
    }

    @Test
    void test10VisitsPerPatientChronologicalAndClinicalDetail() {
        List<PatientTemplate> patients = generator.getPredefinedPatients();
        List<GpResponse> mockGps = List.of(
                GpResponse.builder().id(UUID.randomUUID()).firstName("Alistair").lastName("Finch").specialisation("General Practice").build()
        );

        for (int i = 0; i < patients.size(); i++) {
            PatientTemplate pt = patients.get(i);
            List<PatientVisitRequest> visits = generator.generateVisitsForPatient(pt, i, mockGps);

            assertNotNull(visits);
            assertEquals(10, visits.size(), "Each patient must have exactly 10 clinical visits");

            LocalDate previousFollowUpDate = null;

            for (int v = 0; v < visits.size(); v++) {
                PatientVisitRequest visit = visits.get(v);

                // Demographic linkage
                assertEquals(pt.firstName() + " " + pt.lastName(), visit.getPatientName());
                assertEquals(pt.patientId(), visit.getMrn());
                assertEquals(pt.dateOfBirth(), visit.getDateOfBirth());
                assertEquals(pt.gender(), visit.getGender());
                assertEquals(pt.bloodType(), visit.getBloodType());

                // Biometrics & LOINC Vitals
                assertNotNull(visit.getBloodPressure(), "Blood pressure must not be null");
                assertTrue(visit.getBloodPressure().contains("mmHg"));
                assertNotNull(visit.getHeartRate());
                assertTrue(visit.getHeartRate() >= 60 && visit.getHeartRate() <= 95);
                assertNotNull(visit.getRespiratoryRate());
                assertTrue(visit.getRespiratoryRate().contains("breaths/min"));
                assertNotNull(visit.getTemperature());
                assertTrue(visit.getTemperature().contains("°C"));
                assertNotNull(visit.getOxygenSaturation());
                assertTrue(visit.getOxygenSaturation().contains("%"));
                assertNotNull(visit.getHeightCm());
                assertNotNull(visit.getWeightKg());
                assertNotNull(visit.getBmi());
                assertNotNull(visit.getPainScore());
                assertTrue(visit.getPainScore() >= 0 && visit.getPainScore() <= 7);

                // SOAP Encounter Notes
                assertNotNull(visit.getChiefComplaint(), "Chief complaint required");
                assertNotNull(visit.getDiagnosis(), "Diagnosis required");
                assertNotNull(visit.getSoapSubjective(), "SOAP Subjective required");
                assertNotNull(visit.getSoapObjective(), "SOAP Objective required");
                assertNotNull(visit.getSoapAssessment(), "SOAP Assessment required");
                assertNotNull(visit.getSoapPlan(), "SOAP Plan required");
                assertNotNull(visit.getPrescriptions(), "Prescriptions required");
                assertNotNull(visit.getAttendingDoctor(), "Attending doctor required");
                assertNotNull(visit.getDepartment(), "Department required");

                // Verify Chronological ordering of Follow-up dates
                assertNotNull(visit.getFollowUpDate());
                LocalDate currentFollowUp = LocalDate.parse(visit.getFollowUpDate());
                if (previousFollowUpDate != null) {
                    assertTrue(currentFollowUp.isAfter(previousFollowUpDate),
                            "Visits must be chronologically ordered. Previous: " + previousFollowUpDate + ", Current: " + currentFollowUp);
                }
                previousFollowUpDate = currentFollowUp;
            }
        }
    }

    @Test
    void testDiagnosticAttachmentsGeneration() {
        List<AttachmentSpec> atts = generator.getAttachmentSpecsForVisit(1, "Eleanor Vance", "PAT-10001");
        assertFalse(atts.isEmpty(), "Visit 1 (Cardiology) should have attached reports");
        assertEquals("Cardiology_12_Lead_ECG.pdf", atts.get(0).fileName());

        byte[] pdfBytes = generator.generateSamplePdfContent(
                "Eleanor Vance", "PAT-10001",
                atts.get(0).title(), atts.get(0).details()
        );

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 100);
        String pdfStr = new String(pdfBytes, StandardCharsets.UTF_8);
        assertTrue(pdfStr.startsWith("%PDF-1.4"));
        assertTrue(pdfStr.contains("Eleanor Vance"));
        assertTrue(pdfStr.contains("PAT-10001"));
        assertTrue(pdfStr.endsWith("%%EOF"));
    }

    @Test
    void testToPatientRequest() {
        PatientTemplate pt = generator.getPredefinedPatients().get(0);
        UUID gpUuid = UUID.randomUUID();
        List<GpResponse> gps = List.of(
                GpResponse.builder().id(gpUuid).firstName("Alistair").lastName("Finch").build()
        );

        PatientRequest req = generator.toPatientRequest(pt, gps);
        assertNotNull(req);
        assertEquals(pt.patientId(), req.getPatientId());
        assertEquals(pt.firstName(), req.getFirstName());
        assertEquals(pt.lastName(), req.getLastName());
        assertEquals(pt.nhsNumber(), req.getNhsNumber());
        assertEquals(gpUuid, req.getGpId());
    }
}
