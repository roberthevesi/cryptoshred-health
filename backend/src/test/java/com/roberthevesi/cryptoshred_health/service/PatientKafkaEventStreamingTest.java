package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.KeyRotationRequestDto;
import com.roberthevesi.cryptoshred_health.dto.KeyRotationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitEventDto;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitResponse;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.EncryptionKeyRepository;
import com.roberthevesi.cryptoshred_health.repository.GpRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PatientKafkaEventStreamingTest {

    private PatientRepository patientRepository;
    private PatientVisitRepository patientVisitRepository;
    private EncryptionKeyRepository encryptionKeyRepository;
    private GpRepository gpRepository;
    private UserRepository userRepository;
    private VaultKmsService vaultKmsService;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private EventLogPublisher eventLogPublisher;
    private EventLogConsumer eventLogConsumer;
    private PatientService patientService;
    private PatientVisitService patientVisitService;
    private ErasureService erasureService;
    private KeyManagementService keyManagementService;
    private PatientCacheService patientCacheService;
    private PatientVisitCacheService patientVisitCacheService;
    private ProofSigningService proofSigningService;
    private MerkleTreeService merkleTreeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = Mockito.mock(KafkaTemplate.class);

    // In-memory key store simulating HashiCorp Vault KMS
    private final Map<String, byte[]> vaultStorage = new ConcurrentHashMap<>();
    // In-memory patient storage
    private final Map<String, Patient> patientDb = new ConcurrentHashMap<>();
    // In-memory visit storage
    private final Map<UUID, PatientVisit> visitDb = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        vaultStorage.clear();
        patientDb.clear();
        visitDb.clear();

        when(kafkaTemplate.send(anyString(), any(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        patientRepository = Mockito.mock(PatientRepository.class);
        patientVisitRepository = Mockito.mock(PatientVisitRepository.class);
        encryptionKeyRepository = Mockito.mock(EncryptionKeyRepository.class);
        gpRepository = Mockito.mock(GpRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        envelopeEncryptionService = new EnvelopeEncryptionService();
        patientCacheService = Mockito.mock(PatientCacheService.class);
        patientVisitCacheService = Mockito.mock(PatientVisitCacheService.class);
        proofSigningService = Mockito.mock(ProofSigningService.class);
        merkleTreeService = Mockito.mock(MerkleTreeService.class);

        User doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setEmail("doctor@hospital.com");
        doctorUser.setRole(Role.DOCTOR);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(doctorUser));

        when(proofSigningService.sign(anyString())).thenReturn("MOCK_RSA_SIGNATURE_HEX");
        when(merkleTreeService.getMerkleRoot()).thenReturn("MOCK_MERKLE_ROOT");
        when(merkleTreeService.getInclusionProof(anyString())).thenReturn(List.of("proof1", "proof2"));

        // Mock Vault Transit operations
        doNothing().when(vaultKmsService).ensureKeyExists(anyString());

        when(vaultKmsService.wrapDek(anyString(), any())).thenAnswer(inv -> {
            String keyName = inv.getArgument(0);
            byte[] dek = inv.getArgument(1);
            vaultStorage.put(keyName, dek);
            return "wrapped:" + keyName;
        });

        when(vaultKmsService.unwrapDek(anyString(), anyString())).thenAnswer(inv -> {
            String keyName = inv.getArgument(0);
            if (!vaultStorage.containsKey(keyName)) {
                throw new RuntimeException("Vault key invalid or destroyed: " + keyName);
            }
            return vaultStorage.get(keyName);
        });

        when(vaultKmsService.rewrapDek(anyString(), anyString())).thenAnswer(inv -> {
            String keyName = inv.getArgument(0);
            if (!vaultStorage.containsKey(keyName)) {
                throw new RuntimeException("Vault key invalid or destroyed: " + keyName);
            }
            return "rewrapped:" + keyName;
        });

        doAnswer(inv -> {
            String keyName = inv.getArgument(0);
            vaultStorage.remove(keyName);
            return null;
        }).when(vaultKmsService).destroyKey(anyString());

        // Mock Repositories
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            patientDb.put(p.getPatientId(), p);
            return p;
        });

        when(patientRepository.findByPatientId(anyString())).thenAnswer(inv -> {
            String pid = inv.getArgument(0);
            return Optional.ofNullable(patientDb.get(pid));
        });

        when(patientRepository.findByEncryptionKey(any(EncryptionKey.class))).thenAnswer(inv -> {
            EncryptionKey k = inv.getArgument(0);
            return patientDb.values().stream()
                    .filter(p -> p.getEncryptionKey() != null &&
                            (p.getEncryptionKey().equals(k) ||
                             Objects.equals(p.getEncryptionKey().getKeyId(), k.getKeyId()) ||
                             Objects.equals(p.getEncryptionKey().getVaultKeyName(), k.getVaultKeyName())))
                    .findFirst();
        });

        when(patientVisitRepository.save(any(PatientVisit.class))).thenAnswer(inv -> {
            PatientVisit v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            visitDb.put(v.getId(), v);
            return v;
        });

        when(patientVisitRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(visitDb.get(id));
        });

        when(patientVisitRepository.findByPatientIdentifier(anyString())).thenAnswer(inv -> {
            String pid = inv.getArgument(0);
            return visitDb.values().stream()
                    .filter(v -> (v.getPatient() != null && pid.equalsIgnoreCase(v.getPatient().getPatientId())) ||
                                 (v.getMrn() != null && pid.equalsIgnoreCase(v.getMrn())))
                    .collect(Collectors.toList());
        });

        eventLogPublisher = new EventLogPublisher(kafkaTemplate, objectMapper);
        eventLogConsumer = new EventLogConsumer(objectMapper, vaultKmsService, envelopeEncryptionService);

        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder = Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);

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

        erasureService = new ErasureService(
                patientVisitRepository,
                patientRepository,
                encryptionKeyRepository,
                vaultKmsService,
                eventLogPublisher,
                patientVisitCacheService,
                patientCacheService,
                proofSigningService,
                merkleTreeService,
                objectMapper
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
                objectMapper,
                erasureService
        );

        keyManagementService = new KeyManagementService(
                encryptionKeyRepository,
                patientRepository,
                patientVisitRepository,
                vaultKmsService,
                eventLogPublisher
        );
    }

    @Test
    @DisplayName("Complete Patient Lifecycle: Creation event -> Decryption while active -> Crypto-shredding -> Undecryptable historical event")
    void testCompletePatientLifecycleAndKafkaCryptoShredding() throws Exception {
        // 1. Register new Patient
        PatientRequest request = new PatientRequest();
        request.setFirstName("Clara");
        request.setLastName("Oswald");
        request.setDateOfBirth("1986-11-23");
        request.setGender("Female");
        request.setEmail("clara.oswald@coalhill.edu");
        request.setPhoneNumber("+44 7911 123456");
        request.setAddress("76 Totter's Lane, Shoreditch, London");
        request.setNhsNumber("987 654 3210");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        PatientResponse created = patientService.create(request);
        assertNotNull(created);
        String patientId = created.getPatientId();

        // 2. Verify PATIENT_CREATED event was published to Kafka with patientId as partition key
        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());
        assertEquals(patientId, keyCaptor.getValue(), "Kafka message key must be patientId for partition affinity");

        PatientVisitEventDto createdEvent = objectMapper.readValue(payloadCaptor.getValue(), PatientVisitEventDto.class);
        assertEquals("PATIENT_CREATED", createdEvent.getEventType());
        assertEquals(patientId, createdEvent.getPatientId());
        assertNotNull(createdEvent.getVaultKeyName());
        assertNotNull(createdEvent.getWrappedDek());
        assertNotNull(createdEvent.getIv());
        assertNotNull(createdEvent.getEncryptedDataBlob());

        // 3. Verify that EventLogConsumer can decrypt the PATIENT_CREATED event payload while active
        String decryptedPii = eventLogConsumer.attemptDecryptEventPayload(createdEvent);
        assertNotNull(decryptedPii);
        assertTrue(decryptedPii.contains("Clara"));
        assertTrue(decryptedPii.contains("Oswald"));
        assertTrue(decryptedPii.contains("clara.oswald@coalhill.edu"));
        assertTrue(decryptedPii.contains("987 654 3210"));

        // 4. Crypto-Shred the Patient profile via ErasureService
        VerifiableDeletionProofDto proof = erasureService.forgetPatient(patientId, "DPO-Security-Officer");
        assertNotNull(proof);
        assertEquals("PATIENT_DELETED", proof.getStatus());
        verify(vaultKmsService, times(1)).destroyKey(createdEvent.getVaultKeyName());

        // 5. Verify that attempting to decrypt the immutable historical Kafka event log now FAILS permanently
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> eventLogConsumer.attemptDecryptEventPayload(createdEvent)
        );

        assertTrue(ex.getMessage().contains("Vault KEK destroyed"), "Decryption must fail due to destroyed Vault KEK");
    }

    @Test
    @DisplayName("Patient Update & Deactivation Events: Emits PATIENT_UPDATED and PATIENT_DEACTIVATED")
    void testPatientUpdateAndDeactivationEvents() throws Exception {
        // 1. Create patient
        PatientRequest createReq = new PatientRequest();
        createReq.setFirstName("Rory");
        createReq.setLastName("Williams");
        createReq.setEmail("rory.nurse@nhs.net");

        PatientResponse created = patientService.create(createReq);
        String patientId = created.getPatientId();

        // 2. Update patient
        PatientRequest updateReq = new PatientRequest();
        updateReq.setFirstName("Rory");
        updateReq.setLastName("Williams-Pond");
        updateReq.setEmail("rory.pond@nhs.net");
        updateReq.setPhoneNumber("+44 7700 900888");

        reset(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), any(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        ArgumentCaptor<String> updatePayloadCaptor = ArgumentCaptor.forClass(String.class);

        PatientResponse updated = patientService.update(patientId, updateReq);
        assertEquals("Williams-Pond", updated.getLastName());

        verify(kafkaTemplate, times(1)).send(anyString(), eq(patientId), updatePayloadCaptor.capture());
        PatientVisitEventDto updateEvent = objectMapper.readValue(updatePayloadCaptor.getValue(), PatientVisitEventDto.class);
        assertEquals("PATIENT_UPDATED", updateEvent.getEventType());
        assertEquals(patientId, updateEvent.getPatientId());

        // Decrypt updated event
        String decryptedUpdateJson = eventLogConsumer.attemptDecryptEventPayload(updateEvent);
        assertTrue(decryptedUpdateJson.contains("Williams-Pond"));
        assertTrue(decryptedUpdateJson.contains("rory.pond@nhs.net"));

        // 3. Deactivate patient
        reset(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), any(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        ArgumentCaptor<String> deactivatePayloadCaptor = ArgumentCaptor.forClass(String.class);

        patientService.deactivate(patientId);

        verify(kafkaTemplate, times(1)).send(anyString(), eq(patientId), deactivatePayloadCaptor.capture());
        PatientVisitEventDto deactivateEvent = objectMapper.readValue(deactivatePayloadCaptor.getValue(), PatientVisitEventDto.class);
        assertEquals("PATIENT_DEACTIVATED", deactivateEvent.getEventType());
        assertEquals(patientId, deactivateEvent.getPatientId());
    }

    @Test
    @DisplayName("Patient Key Rotation: Emits PATIENT_KEY_ROTATED event with patientId")
    void testPatientKeyRotationEmitsPatientKeyRotatedEvent() throws Exception {
        // 1. Create patient
        PatientRequest createReq = new PatientRequest();
        createReq.setFirstName("Amy");
        createReq.setLastName("Pond");
        createReq.setEmail("amy.pond@example.com");

        PatientResponse created = patientService.create(createReq);
        String patientId = created.getPatientId();
        Patient patient = patientDb.get(patientId);

        when(encryptionKeyRepository.findAll()).thenReturn(List.of(patient.getEncryptionKey()));

        // 2. Rotate keys
        reset(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), any(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        ArgumentCaptor<String> keyRotatePayloadCaptor = ArgumentCaptor.forClass(String.class);

        KeyRotationRequestDto rotReq = KeyRotationRequestDto.builder()
                .scope("PATIENT")
                .targetId(patientId)
                .build();

        KeyRotationResponseDto rotResp = keyManagementService.rotateKeys(rotReq);
        assertEquals("SUCCESS", rotResp.getStatus());
        assertEquals(1, rotResp.getRotatedCount());

        verify(kafkaTemplate, times(1)).send(anyString(), eq(patientId), keyRotatePayloadCaptor.capture());
        PatientVisitEventDto rotEvent = objectMapper.readValue(keyRotatePayloadCaptor.getValue(), PatientVisitEventDto.class);
        assertEquals("PATIENT_KEY_ROTATED", rotEvent.getEventType());
        assertEquals(patientId, rotEvent.getPatientId());
        assertEquals(patient.getEncryptionKey().getVaultKeyName(), rotEvent.getVaultKeyName());
    }

    @Test
    @DisplayName("Kafka Partitioning: Prioritizes patientId across demographic and clinical visit events")
    void testKafkaPartitioningPrioritizesPatientId() {
        UUID visitId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // Case 1: demographic event with patientId only
        eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                .eventId(eventId)
                .patientId("PAT-11111")
                .eventType("PATIENT_CREATED")
                .build());
        verify(kafkaTemplate, times(1)).send(anyString(), eq("PAT-11111"), anyString());

        // Case 2: clinical visit event with both patientId and visitId
        eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                .eventId(eventId)
                .visitId(visitId)
                .patientId("PAT-11111")
                .eventType("VISIT_CREATED")
                .build());
        verify(kafkaTemplate, times(2)).send(anyString(), eq("PAT-11111"), anyString());

        // Case 3: unlinked visit event with visitId only
        eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                .eventId(eventId)
                .visitId(visitId)
                .eventType("VISIT_CREATED")
                .build());
        verify(kafkaTemplate, times(1)).send(anyString(), eq(visitId.toString()), anyString());

        // Case 4: system event without patientId or visitId
        eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                .eventId(eventId)
                .eventType("SYSTEM_EVENT")
                .build());
        verify(kafkaTemplate, times(1)).send(anyString(), eq(eventId.toString()), anyString());
    }

    @Test
    @DisplayName("Visit Lifecycle: VISIT_CREATED & VISIT_UPDATED streaming -> active decryption -> forgetVisit crypto-shredding -> permanent decryption failure")
    void testVisitCreatedAndUpdatedKafkaEventStreamingAndCryptoShredding() throws Exception {
        // 1. Create a patient first
        PatientRequest patientReq = new PatientRequest();
        patientReq.setFirstName("Donna");
        patientReq.setLastName("Noble");
        patientReq.setEmail("donna.noble@tardis.com");
        patientReq.setDateOfBirth("1980-05-15");
        patientReq.setGender("Female");
        PatientResponse patientResp = patientService.create(patientReq);
        String patientId = patientResp.getPatientId();

        // 2. Create a new clinical visit
        PatientVisitRequest createVisitReq = new PatientVisitRequest();
        createVisitReq.setPatientId(patientId);
        createVisitReq.setPatientName("Donna Noble");
        createVisitReq.setBloodPressure("120/80");
        createVisitReq.setHeartRate(72);
        createVisitReq.setRespiratoryRate("16");
        createVisitReq.setTemperature("36.6");
        createVisitReq.setOxygenSaturation("99%");
        createVisitReq.setDiagnosis("Routine Physical Examination");
        createVisitReq.setChiefComplaint("Annual health checkup");
        createVisitReq.setMedicalNotes("Patient is in overall excellent health.");
        createVisitReq.setAttendingDoctor("Dr. John Smith");
        createVisitReq.setDepartment("General Practice");

        reset(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), any(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        ArgumentCaptor<String> visitCreatedPayloadCaptor = ArgumentCaptor.forClass(String.class);

        PatientVisitResponse visitResponse = patientVisitService.create(createVisitReq, "doctor@hospital.com");
        assertNotNull(visitResponse);
        UUID visitId = visitResponse.getId();
        assertNotNull(visitId);

        // Verify VISIT_CREATED event published to Kafka with all required envelope fields
        verify(kafkaTemplate, times(1)).send(anyString(), eq(patientId), visitCreatedPayloadCaptor.capture());
        PatientVisitEventDto createdEvent = objectMapper.readValue(visitCreatedPayloadCaptor.getValue(), PatientVisitEventDto.class);

        assertEquals("VISIT_CREATED", createdEvent.getEventType());
        assertEquals(visitId, createdEvent.getVisitId());
        assertEquals(patientId, createdEvent.getPatientId());
        assertNotNull(createdEvent.getVaultKeyName());
        assertNotNull(createdEvent.getWrappedDek(), "VISIT_CREATED event must contain wrappedDek");
        assertNotNull(createdEvent.getIv(), "VISIT_CREATED event must contain iv");
        assertNotNull(createdEvent.getEncryptedDataBlob(), "VISIT_CREATED event must contain encryptedDataBlob");

        // Decrypt VISIT_CREATED event payload while active
        String decryptedCreatedJson = eventLogConsumer.attemptDecryptEventPayload(createdEvent);
        assertNotNull(decryptedCreatedJson);
        assertTrue(decryptedCreatedJson.contains("Routine Physical Examination"));
        assertTrue(decryptedCreatedJson.contains("120/80"));
        assertTrue(decryptedCreatedJson.contains("Annual health checkup"));

        // 3. Update the clinical visit
        PatientVisitRequest updateVisitReq = new PatientVisitRequest();
        updateVisitReq.setPatientId(patientId);
        updateVisitReq.setPatientName("Donna Noble");
        updateVisitReq.setBloodPressure("125/82");
        updateVisitReq.setHeartRate(75);
        updateVisitReq.setDiagnosis("Mild Hypertension Follow-up");
        updateVisitReq.setChiefComplaint("Slight headache");
        updateVisitReq.setMedicalNotes("Prescribed lifestyle modifications.");
        updateVisitReq.setAttendingDoctor("Dr. John Smith");
        updateVisitReq.setDepartment("Cardiology");

        reset(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), any(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        ArgumentCaptor<String> visitUpdatedPayloadCaptor = ArgumentCaptor.forClass(String.class);

        PatientVisitResponse updatedVisitResponse = patientVisitService.update(visitId, updateVisitReq, "doctor@hospital.com");
        assertNotNull(updatedVisitResponse);

        // Verify VISIT_UPDATED event published to Kafka with all required envelope fields
        verify(kafkaTemplate, times(1)).send(anyString(), eq(patientId), visitUpdatedPayloadCaptor.capture());
        PatientVisitEventDto updatedEvent = objectMapper.readValue(visitUpdatedPayloadCaptor.getValue(), PatientVisitEventDto.class);

        assertEquals("VISIT_UPDATED", updatedEvent.getEventType());
        assertEquals(visitId, updatedEvent.getVisitId());
        assertEquals(patientId, updatedEvent.getPatientId());
        assertEquals(createdEvent.getVaultKeyName(), updatedEvent.getVaultKeyName());
        assertNotNull(updatedEvent.getWrappedDek(), "VISIT_UPDATED event must contain wrappedDek");
        assertNotNull(updatedEvent.getIv(), "VISIT_UPDATED event must contain iv");
        assertNotNull(updatedEvent.getEncryptedDataBlob(), "VISIT_UPDATED event must contain encryptedDataBlob");

        // Decrypt VISIT_UPDATED event payload while active
        String decryptedUpdatedJson = eventLogConsumer.attemptDecryptEventPayload(updatedEvent);
        assertNotNull(decryptedUpdatedJson);
        assertTrue(decryptedUpdatedJson.contains("Mild Hypertension Follow-up"));
        assertTrue(decryptedUpdatedJson.contains("125/82"));
        assertTrue(decryptedUpdatedJson.contains("Prescribed lifestyle modifications."));

        // 4. Crypto-shred the individual visit via ErasureService
        VerifiableDeletionProofDto visitProof = erasureService.forgetVisit(visitId, "DPO-Security-Officer");
        assertNotNull(visitProof);
        assertEquals("VISIT_DELETED", visitProof.getStatus());
        verify(vaultKmsService, times(1)).destroyKey(createdEvent.getVaultKeyName());

        // 5. Verify that attempting to decrypt historical VISIT_CREATED and VISIT_UPDATED events fails with 'Vault KEK destroyed'
        IllegalStateException exCreated = assertThrows(
                IllegalStateException.class,
                () -> eventLogConsumer.attemptDecryptEventPayload(createdEvent)
        );
        assertTrue(exCreated.getMessage().contains("Vault KEK destroyed"), "Decryption must fail due to destroyed Vault KEK");

        IllegalStateException exUpdated = assertThrows(
                IllegalStateException.class,
                () -> eventLogConsumer.attemptDecryptEventPayload(updatedEvent)
        );
        assertTrue(exUpdated.getMessage().contains("Vault KEK destroyed"), "Decryption must fail due to destroyed Vault KEK");
    }
}
