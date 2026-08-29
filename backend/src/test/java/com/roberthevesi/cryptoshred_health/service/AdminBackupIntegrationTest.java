package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.controller.AdminBackupController;
import com.roberthevesi.cryptoshred_health.dto.BackupBundleDto;
import com.roberthevesi.cryptoshred_health.dto.BackupFileEntryDto;
import com.roberthevesi.cryptoshred_health.exception.GlobalExceptionHandler;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import com.roberthevesi.cryptoshred_health.security.CustomUserDetailsService;
import com.roberthevesi.cryptoshred_health.security.JwtAuthenticationFilter;
import com.roberthevesi.cryptoshred_health.security.JwtTokenProvider;
import com.roberthevesi.cryptoshred_health.security.PatientSecurityService;
import com.roberthevesi.cryptoshred_health.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AdminBackupIntegrationTest {

    // =========================================================================
    // 1. Controller RBAC & Endpoint Tests (WebMvcTest)
    // =========================================================================
    @Nested
    @WebMvcTest(controllers = AdminBackupController.class)
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
    class AdminBackupControllerRbacTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private BackupManagementService backupManagementService;

        @MockBean
        private MerkleTombstoneReconciliationService merkleTombstoneReconciliationService;

        // Security mocks required for security filter chain
        @MockBean
        private UserRepository userRepository;

        @MockBean
        private PatientRepository patientRepository;

        @MockBean
        private AuthenticationManager authenticationManager;

        private BackupBundleDto mockBundle;

        @BeforeEach
        void setUp() {
            mockBundle = BackupBundleDto.builder()
                    .bundleId(UUID.randomUUID().toString())
                    .bundleName("bundle_2026-08-29_13-00-00")
                    .timestamp(LocalDateTime.now())
                    .merkleRoot("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e")
                    .totalSizeBytes(1024)
                    .status("VALID")
                    .valid(true)
                    .signature("MOCK_RSA_SIGNATURE")
                    .pqcSignature("MOCK_PQC_SIGNATURE")
                    .files(List.of(
                            BackupFileEntryDto.builder()
                                    .fileName("database_zero_plaintext.sql.gz")
                                    .sha256Checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                                    .sizeBytes(256)
                                    .type("POSTGRESQL_ZERO_PLAINTEXT_DUMP")
                                    .verified(true)
                                    .build()
                    ))
                    .build();

            when(backupManagementService.createAtomicBundle()).thenReturn(mockBundle);
            when(backupManagementService.listBundles()).thenReturn(List.of(mockBundle));
            when(backupManagementService.verifyBundleIntegrity("valid-bundle-id")).thenReturn(true);
            when(backupManagementService.verifyBundleIntegrity("corrupt-bundle-id")).thenReturn(false);
            when(merkleTombstoneReconciliationService.reconcileTombstones()).thenReturn(2);
        }

        // ── Unauthenticated Tests (401 Unauthorized) ─────────────────────────
        @Test
        @DisplayName("Unauthenticated: POST /api/admin/backups/bundle -> 401 Unauthorized")
        void testCreateBundleUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/admin/backups/bundle"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated: GET /api/admin/backups/bundles -> 401 Unauthorized")
        void testListBundlesUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/admin/backups/bundles"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated: POST /api/admin/backups/bundles/{id}/verify -> 401 Unauthorized")
        void testVerifyBundleUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/admin/backups/bundles/valid-bundle-id/verify"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unauthenticated: POST /api/admin/backups/reconcile-tombstones -> 401 Unauthorized")
        void testReconcileTombstonesUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/admin/backups/reconcile-tombstones"))
                    .andExpect(status().isUnauthorized());
        }

        // ── Non-Admin Role RBAC (403 Forbidden) ──────────────────────────────
        @Test
        @WithMockUser(username = "doctor@hospital.org", roles = {"DOCTOR"})
        @DisplayName("RBAC 403: DOCTOR cannot trigger backup bundle capture")
        void testDoctorCannotCreateBundle() throws Exception {
            mockMvc.perform(post("/api/admin/backups/bundle"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "auditor@hospital.org", roles = {"AUDITOR"})
        @DisplayName("RBAC 403: AUDITOR cannot trigger backup bundle capture")
        void testAuditorCannotCreateBundle() throws Exception {
            mockMvc.perform(post("/api/admin/backups/bundle"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "patient@hospital.org", roles = {"PATIENT"})
        @DisplayName("RBAC 403: PATIENT cannot trigger backup bundle capture")
        void testPatientCannotCreateBundle() throws Exception {
            mockMvc.perform(post("/api/admin/backups/bundle"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "doctor@hospital.org", roles = {"DOCTOR"})
        @DisplayName("RBAC 403: DOCTOR cannot list backup bundles")
        void testDoctorCannotListBundles() throws Exception {
            mockMvc.perform(get("/api/admin/backups/bundles"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "auditor@hospital.org", roles = {"AUDITOR"})
        @DisplayName("RBAC 403: AUDITOR cannot list backup bundles")
        void testAuditorCannotListBundles() throws Exception {
            mockMvc.perform(get("/api/admin/backups/bundles"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "doctor@hospital.org", roles = {"DOCTOR"})
        @DisplayName("RBAC 403: DOCTOR cannot verify backup bundles")
        void testDoctorCannotVerifyBundle() throws Exception {
            mockMvc.perform(post("/api/admin/backups/bundles/valid-bundle-id/verify"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "doctor@hospital.org", roles = {"DOCTOR"})
        @DisplayName("RBAC 403: DOCTOR cannot reconcile Merkle tombstones")
        void testDoctorCannotReconcileTombstones() throws Exception {
            mockMvc.perform(post("/api/admin/backups/reconcile-tombstones"))
                    .andExpect(status().isForbidden());
        }

        // ── Admin Authorized Tests (200 OK / 201 CREATED) ────────────────────
        @Test
        @WithMockUser(username = "admin@hospital.org", roles = {"ADMIN"})
        @DisplayName("RBAC 201: ADMIN can trigger on-demand atomic backup bundle capture")
        void testAdminCanCreateBundle() throws Exception {
            mockMvc.perform(post("/api/admin/backups/bundle"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.bundleId").value(mockBundle.getBundleId()))
                    .andExpect(jsonPath("$.bundleName").value(mockBundle.getBundleName()))
                    .andExpect(jsonPath("$.status").value("VALID"))
                    .andExpect(jsonPath("$.merkleRoot").value(mockBundle.getMerkleRoot()));
        }

        @Test
        @WithMockUser(username = "admin@hospital.org", roles = {"ADMIN"})
        @DisplayName("RBAC 200: ADMIN can list all backup bundles")
        void testAdminCanListBundles() throws Exception {
            mockMvc.perform(get("/api/admin/backups/bundles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].bundleId").value(mockBundle.getBundleId()))
                    .andExpect(jsonPath("$[0].bundleName").value(mockBundle.getBundleName()));
        }

        @Test
        @WithMockUser(username = "admin@hospital.org", roles = {"ADMIN"})
        @DisplayName("RBAC 200: ADMIN can verify valid backup bundle integrity")
        void testAdminCanVerifyValidBundle() throws Exception {
            mockMvc.perform(post("/api/admin/backups/bundles/valid-bundle-id/verify"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bundleId").value("valid-bundle-id"))
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.status").value("VERIFIED"));
        }

        @Test
        @WithMockUser(username = "admin@hospital.org", roles = {"ADMIN"})
        @DisplayName("RBAC 200: ADMIN receives CORRUPTED status when bundle verification fails")
        void testAdminCanVerifyCorruptedBundle() throws Exception {
            mockMvc.perform(post("/api/admin/backups/bundles/corrupt-bundle-id/verify"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bundleId").value("corrupt-bundle-id"))
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.status").value("CORRUPTED"));
        }

        @Test
        @WithMockUser(username = "admin@hospital.org", roles = {"ADMIN"})
        @DisplayName("RBAC 200: ADMIN can trigger immediate Merkle tombstone reconciliation")
        void testAdminCanReconcileTombstones() throws Exception {
            mockMvc.perform(post("/api/admin/backups/reconcile-tombstones"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.purgedKeysCount").value(2));
        }
    }

    // =========================================================================
    // 2. Functional & Cryptographic Integrity Tests (Service Layer)
    // =========================================================================
    @Nested
    class BackupManagementServiceFunctionalTest {

        private DataSource mockDataSource;
        private PatientVisitRepository mockVisitRepository;
        private MerkleTreeService mockMerkleTreeService;
        private ProofSigningService proofSigningService;
        private BackupManagementService backupService;
        private Path tempBackupDir;

        @BeforeEach
        void setUp(@TempDir Path tempDir) throws Exception {
            this.tempBackupDir = tempDir;
            mockDataSource = Mockito.mock(DataSource.class);
            mockVisitRepository = Mockito.mock(PatientVisitRepository.class);
            mockMerkleTreeService = Mockito.mock(MerkleTreeService.class);

            // Real ProofSigningService using local filesystem keypair
            proofSigningService = new ProofSigningService();
            proofSigningService.init();

            when(mockMerkleTreeService.getMerkleRoot()).thenReturn("4a5b6c7d8e9f0123456789abcdef4a5b6c7d8e9f0123456789abcdef4a5b6c7d");

            // Mock database metadata
            Connection mockConn = Mockito.mock(Connection.class);
            DatabaseMetaData mockMeta = Mockito.mock(DatabaseMetaData.class);
            ResultSet mockRs = Mockito.mock(ResultSet.class);
            when(mockDataSource.getConnection()).thenReturn(mockConn);
            when(mockConn.getMetaData()).thenReturn(mockMeta);
            when(mockMeta.getTables(any(), any(), any(), any())).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(false);

            // Mock clinical visits
            UUID visitId = UUID.randomUUID();
            PatientVisit visit = new PatientVisit();
            visit.setId(visitId);
            visit.setPatientName("John Doe");
            visit.setMrn("MRN-77777");
            visit.setEncryptedDataBlob("encrypted_blob_sample");
            visit.setShredded(false);
            visit.setCreatedAt(LocalDateTime.now());
            EncryptionKey key = new EncryptionKey("k1", "patient_key_1", "wrapped_dek_data", "iv_data");
            visit.setEncryptionKey(key);

            when(mockVisitRepository.findAllWithEncryptionKey()).thenReturn(List.of(visit));

            backupService = new BackupManagementService(
                    mockDataSource,
                    mockVisitRepository,
                    mockMerkleTreeService,
                    proofSigningService,
                    null, // vaultOperations fallback
                    tempDir.toString()
            );
        }

        @Test
        @DisplayName("createAtomicBundle generates all 4 artifacts with SHA-256 hashes and signatures")
        void testCreateAtomicBundleSuccess() throws Exception {
            BackupBundleDto bundle = backupService.createAtomicBundle();

            assertNotNull(bundle);
            assertNotNull(bundle.getBundleId());
            assertNotNull(bundle.getBundleName());
            assertTrue(bundle.isValid());
            assertEquals("VALID", bundle.getStatus());
            assertNotNull(bundle.getMerkleRoot());
            assertNotNull(bundle.getSignature());
            assertNotNull(bundle.getSigningPublicKey());

            Path bundlePath = tempBackupDir.resolve(bundle.getBundleName());
            assertTrue(Files.exists(bundlePath));

            // Verify the 4 files exist
            assertTrue(Files.exists(bundlePath.resolve(BackupManagementService.DB_BACKUP_FILENAME)));
            assertTrue(Files.exists(bundlePath.resolve(BackupManagementService.VAULT_SNAPSHOT_FILENAME)));
            assertTrue(Files.exists(bundlePath.resolve(BackupManagementService.WORM_ENCOUNTERS_FILENAME)));
            assertTrue(Files.exists(bundlePath.resolve(BackupManagementService.MANIFEST_FILENAME)));

            // Verify file entries in DTO
            assertEquals(3, bundle.getFiles().size());
            for (BackupFileEntryDto entry : bundle.getFiles()) {
                assertNotNull(entry.getSha256Checksum());
                assertEquals(64, entry.getSha256Checksum().length(), "SHA-256 must be 64 hex characters");
                assertTrue(entry.getSizeBytes() > 0);
            }
        }

        @Test
        @DisplayName("verifyBundleIntegrity passes for authentic, un-tampered backup bundle")
        void testVerifyBundleIntegritySuccess() {
            BackupBundleDto bundle = backupService.createAtomicBundle();
            boolean verified = backupService.verifyBundleIntegrity(bundle.getBundleId());
            assertTrue(verified, "Integrity check must pass for freshly created bundle");
        }

        @Test
        @DisplayName("verifyBundleIntegrity fails when a file in the bundle is tampered with")
        void testVerifyBundleIntegrityFailsOnTamperedFile() throws Exception {
            BackupBundleDto bundle = backupService.createAtomicBundle();

            // Tamper with WORM encounters file
            Path wormFile = tempBackupDir.resolve(bundle.getBundleName()).resolve(BackupManagementService.WORM_ENCOUNTERS_FILENAME);
            wormFile.toFile().setWritable(true);
            Files.writeString(wormFile, "{\"tampered\": true}");

            boolean verified = backupService.verifyBundleIntegrity(bundle.getBundleId());
            assertFalse(verified, "Integrity check must fail when file content is altered");
        }

        @Test
        @DisplayName("listBundles discovers and returns multiple bundles sorted newest first")
        void testListBundles() throws Exception {
            BackupBundleDto bundle1 = backupService.createAtomicBundle();
            Thread.sleep(1100); // Ensure different timestamp second
            BackupBundleDto bundle2 = backupService.createAtomicBundle();

            List<BackupBundleDto> list = backupService.listBundles();
            assertEquals(2, list.size());
            assertEquals(bundle2.getBundleName(), list.get(0).getBundleName(), "Newest bundle must be first");
            assertEquals(bundle1.getBundleName(), list.get(1).getBundleName());
        }

        @Test
        @DisplayName("Scheduled cron tasks execute cleanly without unhandled exceptions")
        void testScheduledCronTasks() {
            MerkleTombstoneReconciliationService mockReconciler = Mockito.mock(MerkleTombstoneReconciliationService.class);
            when(mockReconciler.reconcileTombstones()).thenReturn(0);

            ScheduledBackupCronTasks cronTasks = new ScheduledBackupCronTasks(backupService, mockReconciler);
            assertDoesNotThrow(cronTasks::executeDailyAtomicBackupBundle);
            assertDoesNotThrow(cronTasks::executePeriodicMerkleTombstoneReconciliation);
        }
    }
}
