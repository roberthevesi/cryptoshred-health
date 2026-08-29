package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.repository.GpRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlindIndexSearchTest {

    private CryptoService cryptoService;
    private PatientRepository patientRepository;
    private GpRepository gpRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private VaultKmsService vaultKmsService;
    private EventLogPublisher eventLogPublisher;
    private PatientCacheService patientCacheService;
    private ObjectMapper objectMapper;

    private PatientService patientService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        patientRepository = mock(PatientRepository.class);
        gpRepository = mock(GpRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        envelopeEncryptionService = mock(EnvelopeEncryptionService.class);
        vaultKmsService = mock(VaultKmsService.class);
        eventLogPublisher = mock(EventLogPublisher.class);
        patientCacheService = mock(PatientCacheService.class);
        objectMapper = new ObjectMapper();

        patientService = new PatientService(
                patientRepository,
                gpRepository,
                userRepository,
                passwordEncoder,
                vaultKmsService,
                envelopeEncryptionService,
                cryptoService,
                objectMapper,
                patientCacheService,
                eventLogPublisher
        );
    }

    @Test
    @DisplayName("HMAC-SHA256 Blind Indexing is deterministic and normalizes whitespace and casing")
    void testBlindIndexDeterminismAndNormalization() {
        String rawNhs1 = "  9876543210  ";
        String rawNhs2 = "9876543210";

        // Since whitespace and casing are stripped, these variations should yield identical blind indexes
        String index1 = cryptoService.computeBlindIndex(rawNhs1, null);
        String index2 = cryptoService.computeBlindIndex(rawNhs2, null);

        assertNotNull(index1);
        assertEquals(index1, index2, "Blind index must be identical for whitespace-varying inputs");

        String lastName1 = "MacDonald";
        String lastName2 = " MACDONALD ";
        assertEquals(
                cryptoService.computeBlindIndex(lastName1, null),
                cryptoService.computeBlindIndex(lastName2, null),
                "Blind index must be identical for case-insensitive matching"
        );
    }

    @Test
    @DisplayName("Different inputs and different salts produce distinct HMAC blind indexes")
    void testBlindIndexUniqueness() {
        String indexA = cryptoService.computeBlindIndex("Smith", "salt-1");
        String indexB = cryptoService.computeBlindIndex("Jones", "salt-1");
        String indexC = cryptoService.computeBlindIndex("Smith", "salt-2");

        assertNotEquals(indexA, indexB, "Different plaintext inputs must produce different blind indexes");
        assertNotEquals(indexA, indexC, "Different salts must produce different blind indexes");
    }

    @Test
    @DisplayName("PatientService O(1) search resolves patient by NHS number blind index")
    void testSearchByNhsBlindIndex() {
        String nhsNumber = "9876543210";
        String expectedBlindIndex = cryptoService.computeBlindIndex(nhsNumber, null);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setPatientId("PAT-001");
        patient.setNhsNumber(nhsNumber);
        patient.setBlindIndexNhs(expectedBlindIndex);

        when(patientRepository.findByBlindIndexNhs(expectedBlindIndex)).thenReturn(Optional.of(patient));

        List<com.roberthevesi.cryptoshred_health.dto.PatientResponse> results = patientService.search(nhsNumber);

        assertEquals(1, results.size());
        assertEquals("PAT-001", results.get(0).getPatientId());
        verify(patientRepository, times(1)).findByBlindIndexNhs(expectedBlindIndex);
    }

    @Test
    @DisplayName("PatientService O(1) search resolves patient by MRN / PatientId blind index")
    void testSearchByMrnBlindIndex() {
        String patientId = "PAT-XYZ12345";
        String expectedBlindIndex = cryptoService.computeBlindIndex(patientId, null);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setPatientId(patientId);
        patient.setBlindIndexMrn(expectedBlindIndex);

        when(patientRepository.findByBlindIndexMrn(expectedBlindIndex)).thenReturn(Optional.of(patient));

        List<com.roberthevesi.cryptoshred_health.dto.PatientResponse> results = patientService.search(patientId);

        assertEquals(1, results.size());
        assertEquals(patientId, results.get(0).getPatientId());
        verify(patientRepository, times(1)).findByBlindIndexMrn(expectedBlindIndex);
    }

    @Test
    @DisplayName("PatientService O(1) search resolves patient by Last Name blind index")
    void testSearchByLastNameBlindIndex() {
        String lastName = "O'Connor";
        String expectedBlindIndex = cryptoService.computeBlindIndex(lastName, null);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setPatientId("PAT-777");
        patient.setLastName(lastName);
        patient.setBlindIndexLastName(expectedBlindIndex);

        when(patientRepository.findByBlindIndexLastName(expectedBlindIndex)).thenReturn(List.of(patient));

        List<com.roberthevesi.cryptoshred_health.dto.PatientResponse> results = patientService.search(lastName);

        assertEquals(1, results.size());
        assertEquals("PAT-777", results.get(0).getPatientId());
        verify(patientRepository, times(1)).findByBlindIndexLastName(expectedBlindIndex);
    }
}
