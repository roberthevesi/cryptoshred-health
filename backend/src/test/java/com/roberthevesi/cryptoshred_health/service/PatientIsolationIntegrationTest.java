package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.AttachmentResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitResponse;
import com.roberthevesi.cryptoshred_health.model.*;
import com.roberthevesi.cryptoshred_health.repository.*;
import com.roberthevesi.cryptoshred_health.security.PatientSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PatientIsolationIntegrationTest {

    private PatientRepository patientRepository;
    private PatientVisitRepository patientVisitRepository;
    private PatientAttachmentRepository attachmentRepository;
    private UserRepository userRepository;
    private GpRepository gpRepository;
    private PasswordEncoder passwordEncoder;
    private VaultKmsService vaultKmsService;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private PatientCacheService patientCacheService;
    private PatientVisitCacheService patientVisitCacheService;
    private EventLogPublisher eventLogPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PatientService patientService;
    private PatientVisitService patientVisitService;
    private AttachmentService attachmentService;
    private PatientSecurityService patientSecurityService;
    private FhirExportService fhirExportService;

    @BeforeEach
    void setUp() {
        patientRepository = Mockito.mock(PatientRepository.class);
        patientVisitRepository = Mockito.mock(PatientVisitRepository.class);
        attachmentRepository = Mockito.mock(PatientAttachmentRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        gpRepository = Mockito.mock(GpRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        envelopeEncryptionService = Mockito.mock(EnvelopeEncryptionService.class);
        patientCacheService = Mockito.mock(PatientCacheService.class);
        patientVisitCacheService = Mockito.mock(PatientVisitCacheService.class);
        eventLogPublisher = Mockito.mock(EventLogPublisher.class);

        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "encoded_" + inv.getArgument(0));

        patientService = new PatientService(
                patientRepository,
                gpRepository,
                userRepository,
                passwordEncoder,
                vaultKmsService,
                envelopeEncryptionService,
                objectMapper,
                patientCacheService,
                eventLogPublisher
        );

        patientVisitService = new PatientVisitService(
                patientVisitRepository,
                patientRepository,
                userRepository,
                vaultKmsService,
                envelopeEncryptionService,
                eventLogPublisher,
                patientVisitCacheService,
                patientCacheService,
                patientService,
                objectMapper
        );

        attachmentService = new AttachmentService(
                attachmentRepository,
                visitRepository(),
                patientRepository,
                userRepository,
                vaultKmsService,
                envelopeEncryptionService
        );

        patientSecurityService = new PatientSecurityService(patientRepository, userRepository);

        fhirExportService = new FhirExportService(
                patientService,
                patientRepository,
                patientVisitService,
                patientVisitRepository,
                gpRepository,
                vaultKmsService,
                envelopeEncryptionService,
                objectMapper
        );
    }

    private PatientVisitRepository visitRepository() {
        return patientVisitRepository;
    }

    @Test
    @DisplayName("Auto-provisioning: Registering patient automatically generates User account with temporary password")
    void testAutoProvisioningOfPatientAccount() {
        PatientRequest request = new PatientRequest();
        request.setPatientId("PAT-49201");
        request.setFirstName("Oliver");
        request.setLastName("Smith");
        request.setEmail("oliver.smith@example.com");

        when(userRepository.findByEmail("oliver.smith@example.com")).thenReturn(Optional.empty());
        when(envelopeEncryptionService.generateDek()).thenReturn(new byte[32]);
        when(vaultKmsService.wrapDek(anyString(), any())).thenReturn("wrapped_dek");
        when(envelopeEncryptionService.encrypt(any(), any())).thenReturn(
                new EnvelopeEncryptionService.EncryptedPayload("cipher", "iv")
        );

        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });

        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        PatientResponse response = patientService.create(request);

        assertNotNull(response);
        assertNotNull(response.getTemporaryPassword(), "Temporary password must be returned on creation");
        assertTrue(response.getTemporaryPassword().matches("^[A-Za-z]+-\\d{4}[!@#$%&*][A-Za-z]+$"),
                "Temporary password must match word-digits-symbol-word format: " + response.getTemporaryPassword());

        // Verify User entity was persisted with Role.PATIENT
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("oliver.smith@example.com", savedUser.getEmail());
        assertEquals(Role.PATIENT, savedUser.getRole());
        assertTrue(savedUser.getPassword().startsWith("encoded_"));
    }

    @Test
    @DisplayName("Data Access: Patient can retrieve own profile via getPatientForCurrentUser")
    void testPatientCanRetrieveOwnProfile() {
        String patientEmail = "patient@health.org";
        User patientUser = new User();
        patientUser.setId(UUID.randomUUID());
        patientUser.setEmail(patientEmail);
        patientUser.setRole(Role.PATIENT);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setPatientId("PAT-49201");
        patient.setFirstName("Oliver");
        patient.setLastName("Smith");
        patient.setUser(patientUser);
        patient.setActive(true);
        patient.setShredded(false);

        when(userRepository.findByEmail(patientEmail)).thenReturn(Optional.of(patientUser));
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));

        PatientResponse response = patientService.getPatientForCurrentUser(patientEmail);

        assertNotNull(response);
        assertEquals("PAT-49201", response.getPatientId());
        assertEquals("Oliver", response.getFirstName());
    }

    @Test
    @DisplayName("Patient Security Service: isSelf verifies ownership correctly")
    void testPatientSecurityServiceIsSelf() {
        String selfEmail = "oliver.smith@example.com";
        User selfUser = new User();
        selfUser.setId(UUID.randomUUID());
        selfUser.setEmail(selfEmail);

        Patient selfPatient = new Patient();
        selfPatient.setPatientId("PAT-49201");
        selfPatient.setUser(selfUser);

        when(userRepository.findByEmail(selfEmail)).thenReturn(Optional.of(selfUser));
        when(patientRepository.findByUser(selfUser)).thenReturn(Optional.of(selfPatient));

        Authentication auth = new UsernamePasswordAuthenticationToken(
                selfEmail, "credentials", List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))
        );

        assertTrue(patientSecurityService.isSelf(auth, "PAT-49201"));
        assertFalse(patientSecurityService.isSelf(auth, "PAT-99999"));
        assertFalse(patientSecurityService.isSelf(null, "PAT-49201"));
    }

    @Test
    @DisplayName("Patient Isolation: Patient user cannot access another patient's visits")
    void testPatientCannotAccessAnotherPatientsVisits() {
        String selfEmail = "patient@health.org";
        User patientUser = new User();
        patientUser.setId(UUID.randomUUID());
        patientUser.setEmail(selfEmail);
        patientUser.setRole(Role.PATIENT);

        Patient selfPatient = new Patient();
        selfPatient.setPatientId("PAT-49201");
        selfPatient.setUser(patientUser);

        when(userRepository.findByEmail(selfEmail)).thenReturn(Optional.of(patientUser));
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(selfPatient));

        // Attempting to fetch visits of another patient (PAT-88888) should throw AccessDeniedException
        assertThrows(AccessDeniedException.class, () ->
                patientVisitService.findByPatientIdentifier("PAT-88888", selfEmail)
        );
    }

    @Test
    @DisplayName("Patient Isolation: Patient user can access their own visits and attachments")
    void testPatientCanAccessOwnVisitsAndAttachments() {
        String selfEmail = "patient@health.org";
        User patientUser = new User();
        patientUser.setId(UUID.randomUUID());
        patientUser.setEmail(selfEmail);
        patientUser.setRole(Role.PATIENT);

        Patient selfPatient = new Patient();
        selfPatient.setId(UUID.randomUUID());
        selfPatient.setPatientId("PAT-49201");
        selfPatient.setUser(patientUser);

        when(userRepository.findByEmail(selfEmail)).thenReturn(Optional.of(patientUser));
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(selfPatient));

        UUID visitId = UUID.randomUUID();
        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setPatient(selfPatient);
        visit.setMrn("PAT-49201");
        visit.setPatientName("Oliver Smith");
        visit.setOwner(new User()); // Created by a doctor

        when(patientVisitRepository.findByPatientIdentifier("PAT-49201")).thenReturn(List.of(visit));
        when(patientVisitRepository.findById(visitId)).thenReturn(Optional.of(visit));

        // Patient can find all their own visits
        List<PatientVisitResponse> myVisits = patientVisitService.findAll(selfEmail);
        assertEquals(1, myVisits.size());
        assertEquals("PAT-49201", myVisits.get(0).getPatientId());

        // Patient can find visits by their own patient ID
        List<PatientVisitResponse> byIdVisits = patientVisitService.findByPatientIdentifier("PAT-49201", selfEmail);
        assertEquals(1, byIdVisits.size());

        // Patient can view their specific visit
        PatientVisitResponse singleVisit = patientVisitService.findById(visitId, selfEmail);
        assertNotNull(singleVisit);

        // Patient can view attachments for their visit
        PatientAttachment attachment = new PatientAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setFileName("ECG_Report.pdf");
        attachment.setContentType("application/pdf");
        attachment.setFileSize(2048L);
        attachment.setPatientVisit(visit);

        when(attachmentRepository.findByPatientVisitId(visitId)).thenReturn(List.of(attachment));
        List<AttachmentResponse> attachments = attachmentService.getAttachmentsForVisit(visitId, selfEmail);
        assertEquals(1, attachments.size());
        assertEquals("ECG_Report.pdf", attachments.get(0).getFileName());
    }

    @Test
    @DisplayName("Patient FHIR R4 Bundle: Patient can export their complete clinical bundle")
    void testPatientCanExportFhirR4Bundle() {
        String patientId = "PAT-49201";
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setPatientId(patientId);
        patient.setFirstName("Oliver");
        patient.setLastName("Smith");
        patient.setEmail("patient@health.org");
        patient.setActive(true);
        patient.setShredded(false);

        PatientService mockPatientService = Mockito.mock(PatientService.class);
        FhirExportService localFhirExportService = new FhirExportService(
                mockPatientService,
                patientRepository,
                patientVisitService,
                patientVisitRepository,
                gpRepository,
                vaultKmsService,
                envelopeEncryptionService,
                objectMapper
        );

        when(patientRepository.findByPatientId(patientId)).thenReturn(Optional.of(patient));
        when(patientVisitRepository.findByPatientIdentifier(patientId)).thenReturn(Collections.emptyList());

        PatientResponse patientResponse = PatientResponse.builder()
                .patientId(patientId)
                .firstName("Oliver")
                .lastName("Smith")
                .email("patient@health.org")
                .isActive(true)
                .shredded(false)
                .build();
        when(mockPatientService.toResponse(patient)).thenReturn(patientResponse);

        Map<String, Object> bundle = localFhirExportService.exportPatientFhirR4(patientId);
        assertNotNull(bundle);
        assertEquals("Bundle", bundle.get("resourceType"));
        assertEquals("collection", bundle.get("type"));
    }
}
