package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.controller.*;
import com.roberthevesi.cryptoshred_health.dto.*;
import com.roberthevesi.cryptoshred_health.exception.GlobalExceptionHandler;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import com.roberthevesi.cryptoshred_health.security.CustomUserDetailsService;
import com.roberthevesi.cryptoshred_health.security.JwtAuthenticationFilter;
import com.roberthevesi.cryptoshred_health.security.JwtTokenProvider;
import com.roberthevesi.cryptoshred_health.security.PatientSecurityService;
import com.roberthevesi.cryptoshred_health.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        PatientController.class,
        PatientVisitController.class,
        AdminController.class,
        KeyManagementController.class,
        ErasureController.class,
        AuthController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtTokenProvider.class,
        CustomUserDetailsService.class,
        PatientSecurityService.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:5173",
        "app.jwt.expiration-ms=86400000"
})
public class SecurityRbacHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Security & Auth dependencies
    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PatientRepository patientRepository;

    @MockBean
    private AuthenticationManager authenticationManager;

    // Controller service dependencies
    @MockBean
    private PatientService patientService;

    @MockBean
    private PatientVisitService patientVisitService;

    @MockBean
    private ErasureService erasureService;

    @MockBean
    private KeyManagementService keyManagementService;

    @MockBean
    private ProofSigningService proofSigningService;

    @MockBean
    private AuthService authService;

    @MockBean
    private DataPopulationService dataPopulationService;

    @BeforeEach
    void setUp() {
        when(proofSigningService.getPublicKeyPem()).thenReturn("-----BEGIN PUBLIC KEY-----\nMOCK\n-----END PUBLIC KEY-----");
        when(erasureService.verifyProofArtifact(any())).thenReturn(
                ProofVerificationResponseDto.builder()
                        .valid(true)
                        .signatureValid(true)
                        .payloadIntegrityValid(true)
                        .merkleInclusionValid(true)
                        .build()
        );
    }

    // =========================================================================
    // 1. Unauthenticated Requests (401 Unauthorized)
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated: GET /api/patients -> 401 Unauthorized")
    void testGetPatientsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated: POST /api/patients -> 401 Unauthorized")
    void testPostPatientsUnauthenticated() throws Exception {
        PatientRequest request = new PatientRequest();
        request.setPatientId("PAT-001");
        request.setFirstName("John");
        request.setLastName("Doe");

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated: GET /api/visits -> 401 Unauthorized")
    void testGetVisitsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/visits"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated: POST /api/visits -> 401 Unauthorized")
    void testPostVisitsUnauthenticated() throws Exception {
        PatientVisitRequest request = new PatientVisitRequest();
        request.setPatientId("PAT-001");

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated: GET /api/admin/users -> 401 Unauthorized")
    void testGetAdminUsersUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated: POST /api/keys/rotate -> 401 Unauthorized")
    void testRotateKeysUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/keys/rotate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated: DELETE /api/erasure/patients/PAT-001/forget -> 401 Unauthorized")
    void testForgetPatientUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/erasure/patients/PAT-001/forget"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. Public / Whitelisted Endpoints (PermitAll -> 200 OK)
    // =========================================================================

    @Test
    @DisplayName("Public Endpoint: GET /api/erasure/public-key is accessible without authentication")
    void testGetPublicKeyPermitAll() throws Exception {
        mockMvc.perform(get("/api/erasure/public-key"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public Endpoint: POST /api/erasure/verify-proof is accessible without authentication")
    void testVerifyProofPermitAll() throws Exception {
        VerifiableDeletionProofDto proofDto = VerifiableDeletionProofDto.builder()
                .patientId("PAT-001")
                .status("PATIENT_DELETED")
                .build();
        ProofVerificationRequestDto dto = new ProofVerificationRequestDto(proofDto);

        mockMvc.perform(post("/api/erasure/verify-proof")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 3. Role-Restricted Endpoints (403 Forbidden for unauthorized roles)
    // =========================================================================

    @Test
    @WithMockUser(username = "auditor@hospital.org", roles = {"AUDITOR"})
    @DisplayName("RBAC 403: AUDITOR cannot create clinical visits (POST /api/visits)")
    void testAuditorCannotCreateVisit() throws Exception {
        PatientVisitRequest request = new PatientVisitRequest();
        request.setPatientId("PAT-001");
        request.setChiefComplaint("Consultation");

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "auditor@hospital.org", roles = {"AUDITOR"})
    @DisplayName("RBAC 403: AUDITOR cannot create patient profile (POST /api/patients)")
    void testAuditorCannotCreatePatient() throws Exception {
        PatientRequest request = new PatientRequest();
        request.setPatientId("PAT-001");
        request.setFirstName("Alice");
        request.setLastName("Smith");

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "doctor@hospital.org", roles = {"DOCTOR"})
    @DisplayName("RBAC 403: DOCTOR cannot access admin staff management (GET /api/admin/users)")
    void testDoctorCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "auditor@hospital.org", roles = {"AUDITOR"})
    @DisplayName("RBAC 403: AUDITOR cannot access admin staff management (GET /api/admin/users)")
    void testAuditorCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patient@domain.org", roles = {"PATIENT"})
    @DisplayName("RBAC 403: PATIENT cannot access admin staff management (GET /api/admin/users)")
    void testPatientCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patient@domain.org", roles = {"PATIENT"})
    @DisplayName("RBAC 403: PATIENT cannot rotate KMS keys (POST /api/keys/rotate)")
    void testPatientCannotRotateKeys() throws Exception {
        mockMvc.perform(post("/api/keys/rotate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patient@domain.org", roles = {"PATIENT"})
    @DisplayName("RBAC 403: PATIENT cannot create clinical visits (POST /api/visits)")
    void testPatientCannotCreateVisit() throws Exception {
        PatientVisitRequest request = new PatientVisitRequest();
        request.setPatientId("PAT-001");

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. Authorized Requests (200 OK / 201 CREATED)
    // =========================================================================

    @Test
    @WithMockUser(username = "doctor@hospital.org", roles = {"DOCTOR"})
    @DisplayName("RBAC 201: DOCTOR can create clinical visit (POST /api/visits)")
    void testDoctorCanCreateVisit() throws Exception {
        PatientVisitRequest request = new PatientVisitRequest();
        request.setPatientId("PAT-001");
        request.setChiefComplaint("Routine Checkup");
        request.setDiagnosis("Healthy");
        request.setSoapPlan("Follow-up in 1 year");

        PatientVisitResponse response = PatientVisitResponse.builder()
                .id(UUID.randomUUID())
                .patientId("PAT-001")
                .build();

        when(patientVisitService.create(any(), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "doctor@hospital.org", roles = {"DOCTOR"})
    @DisplayName("RBAC 200: DOCTOR can list visits (GET /api/visits)")
    void testDoctorCanListVisits() throws Exception {
        when(patientVisitService.findAll(anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/visits"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "auditor@hospital.org", roles = {"AUDITOR"})
    @DisplayName("RBAC 200: AUDITOR can list visits (GET /api/visits)")
    void testAuditorCanListVisits() throws Exception {
        when(patientVisitService.findAll(anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/visits"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@hospital.org", roles = {"ADMIN"})
    @DisplayName("RBAC 200: ADMIN can list staff users (GET /api/admin/users)")
    void testAdminCanListUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@hospital.org", roles = {"ADMIN"})
    @DisplayName("RBAC 201: ADMIN can provision new staff (POST /api/admin/users)")
    void testAdminCanCreateUser() throws Exception {
        AdminUserRequest request = new AdminUserRequest();
        request.setEmail("dr.new@hospital.org");
        request.setRole(Role.DOCTOR);
        request.setPassword("SecureTemp123!");

        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail(request.getEmail());
        savedUser.setRole(Role.DOCTOR);
        savedUser.setCreatedAt(LocalDateTime.now());

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin@cryptoshred.health", roles = {"ADMIN"})
    @DisplayName("RBAC 200: ADMIN can trigger KMS key rotation (POST /api/keys/rotate)")
    void testAdminCanRotateKeys() throws Exception {
        KeyRotationResponseDto response = KeyRotationResponseDto.builder()
                .status("SUCCESS")
                .scope("ALL")
                .rotatedCount(5)
                .totalProcessed(5)
                .build();
        when(keyManagementService.rotateKeys(any())).thenReturn(response);

        mockMvc.perform(post("/api/keys/rotate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@cryptoshred.health", roles = {"ADMIN"})
    @DisplayName("RBAC 200: ADMIN can view KMS key summary (GET /api/keys/summary)")
    void testAdminCanGetKeySummary() throws Exception {
        when(keyManagementService.getKeySummary()).thenReturn(com.roberthevesi.cryptoshred_health.dto.KeyStatusSummaryDto.builder()
                .totalKeys(100)
                .activeKeys(100)
                .shreddedKeys(0)
                .build());

        mockMvc.perform(get("/api/keys/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "doctor@hospital.org", roles = {"DOCTOR"})
    @DisplayName("RBAC 403: DOCTOR cannot trigger KMS key rotation (POST /api/keys/rotate)")
    void testDoctorCannotRotateKeys() throws Exception {
        mockMvc.perform(post("/api/keys/rotate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "auditor@hospital.org", roles = {"AUDITOR"})
    @DisplayName("RBAC 403: AUDITOR cannot trigger KMS key rotation (POST /api/keys/rotate)")
    void testAuditorCannotRotateKeys() throws Exception {
        mockMvc.perform(post("/api/keys/rotate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "doctor@hospital.org", roles = {"DOCTOR"})
    @DisplayName("RBAC 200: DOCTOR can crypto-shred patient (DELETE /api/erasure/patients/{id}/forget)")
    void testDoctorCanCryptoShredPatient() throws Exception {
        VerifiableDeletionProofDto proof = VerifiableDeletionProofDto.builder()
                .patientId("PAT-001")
                .build();
        when(erasureService.forgetPatient(anyString(), anyString())).thenReturn(proof);

        mockMvc.perform(delete("/api/erasure/patients/PAT-001/forget"))
                .andExpect(status().isOk());
    }
}
