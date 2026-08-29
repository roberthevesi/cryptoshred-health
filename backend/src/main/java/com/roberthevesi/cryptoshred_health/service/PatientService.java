package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitEventDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.GP;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.GpRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import com.roberthevesi.cryptoshred_health.util.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;
    private final GpRepository gpRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VaultKmsService vaultKmsService;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final PatientCacheService patientCacheService;
    private final EventLogPublisher eventLogPublisher;

    public PatientService(
            PatientRepository patientRepository,
            GpRepository gpRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            VaultKmsService vaultKmsService,
            EnvelopeEncryptionService envelopeEncryptionService,
            ObjectMapper objectMapper,
            PatientCacheService patientCacheService,
            EventLogPublisher eventLogPublisher) {
        this(patientRepository, gpRepository, userRepository, passwordEncoder, vaultKmsService,
                envelopeEncryptionService, new CryptoService(), objectMapper, patientCacheService, eventLogPublisher);
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> findAll() {
        return findAll(true);
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> findAll(boolean includeDeleted) {
        List<Patient> patients = includeDeleted
                ? patientRepository.findAll()
                : patientRepository.findByIsActiveTrue();
        return patients.stream()
                .map(this::resolvePatientResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PatientResponse findByPatientId(String patientId) {
        Patient patient = patientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found: " + patientId));

        if (patient.isShredded() || !patient.isActive()) {
            return toResponse(patient); // Skip Redis check entirely for shredded/deactivated patients
        }

        PatientResponse cached = patientCacheService.get(patientId);
        if (cached != null) {
            log.info("⚡ [REDIS HIT] Serving patient demographic record {} from Redis L2 cache", patientId);
            return cached;
        }

        log.info("🐢 [REDIS MISS] Patient {} not in Redis. Decrypting via Vault KMS and caching...", patientId);
        PatientResponse resp = toResponse(patient);
        if (resp.isActive() && !resp.isShredded()) {
            patientCacheService.put(patientId, resp);
        }
        return resp;
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return findAll(false);
        }
        String q = query.trim();
        String blindIndex = cryptoService.computeBlindIndex(q, null);

        Set<String> matchedPatientIds = new HashSet<>();
        List<PatientResponse> results = new ArrayList<>();

        // 1. O(1) indexed lookup by NHS Number
        patientRepository.findByBlindIndexNhs(blindIndex)
                .filter(p -> p.isActive() && !p.isShredded())
                .ifPresent(p -> {
                    matchedPatientIds.add(p.getPatientId());
                    results.add(resolvePatientResponse(p));
                });

        // 2. O(1) indexed lookup by MRN
        patientRepository.findByBlindIndexMrn(blindIndex)
                .filter(p -> p.isActive() && !p.isShredded() && !matchedPatientIds.contains(p.getPatientId()))
                .ifPresent(p -> {
                    matchedPatientIds.add(p.getPatientId());
                    results.add(resolvePatientResponse(p));
                });

        // 3. O(1) indexed lookup by Surname
        List<Patient> byLastName = patientRepository.findByBlindIndexLastName(blindIndex);
        for (Patient p : byLastName) {
            if (p.isActive() && !p.isShredded() && !matchedPatientIds.contains(p.getPatientId())) {
                matchedPatientIds.add(p.getPatientId());
                results.add(resolvePatientResponse(p));
            }
        }

        if (!results.isEmpty()) {
            return results;
        }

        // 4. Graceful in-memory fuzzy search fallback for substring matches
        String qLower = q.toLowerCase();
        return findAll(false).stream()
                .filter(p -> p.isActive() && !p.isShredded())
                .filter(p -> (p.getPatientId() != null && p.getPatientId().toLowerCase().contains(qLower))
                        || (p.getFirstName() != null && p.getFirstName().toLowerCase().contains(qLower))
                        || (p.getLastName() != null && p.getLastName().toLowerCase().contains(qLower))
                        || (p.getNhsNumber() != null && p.getNhsNumber().toLowerCase().contains(qLower)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<PatientResponse> findByNhsNumber(String nhsNumber) {
        if (nhsNumber == null || nhsNumber.isBlank()) {
            return Optional.empty();
        }
        String blindIndex = cryptoService.computeBlindIndex(nhsNumber, null);
        return patientRepository.findByBlindIndexNhs(blindIndex)
                .filter(p -> p.isActive() && !p.isShredded())
                .map(this::resolvePatientResponse);
    }

    @Transactional(readOnly = true)
    public Optional<PatientResponse> findByMrn(String mrn) {
        if (mrn == null || mrn.isBlank()) {
            return Optional.empty();
        }
        String blindIndex = cryptoService.computeBlindIndex(mrn, null);
        return patientRepository.findByBlindIndexMrn(blindIndex)
                .filter(p -> p.isActive() && !p.isShredded())
                .map(this::resolvePatientResponse);
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> findByLastName(String lastName) {
        if (lastName == null || lastName.isBlank()) {
            return Collections.emptyList();
        }
        String blindIndex = cryptoService.computeBlindIndex(lastName, null);
        return patientRepository.findByBlindIndexLastName(blindIndex).stream()
                .filter(p -> p.isActive() && !p.isShredded())
                .map(this::resolvePatientResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatientForCurrentUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("User email cannot be null or empty");
        }
        String trimmedEmail = email.trim();
        User user = userRepository.findByEmail(trimmedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + trimmedEmail));
        Patient patient = patientRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Patient profile not found for user: " + trimmedEmail));
        return resolvePatientResponse(patient);
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> findByGp(UUID gpId) {
        return patientRepository.findByGpId(gpId).stream()
                .filter(Patient::isActive)
                .map(this::resolvePatientResponse)
                .collect(Collectors.toList());
    }

    private PatientResponse resolvePatientResponse(Patient patient) {
        if (patient.isShredded() || !patient.isActive()) {
            return toResponse(patient); // Skip Redis check entirely for shredded/deactivated patients
        }
        PatientResponse cached = patientCacheService.get(patient.getPatientId());
        if (cached != null) {
            return cached;
        }
        PatientResponse resp = toResponse(patient);
        if (resp.isActive() && !resp.isShredded()) {
            patientCacheService.put(resp.getPatientId(), resp);
        }
        return resp;
    }

    @Transactional
    public PatientResponse create(PatientRequest request) {
        Patient patient = new Patient();

        String patientId = request.getPatientId();
        if (patientId == null || patientId.trim().isBlank()) {
            patientId = generateUniquePatientId();
        } else {
            patientId = patientId.trim();
            if (patientRepository.findByPatientId(patientId).isPresent()) {
                throw new IllegalArgumentException("Patient ID '" + patientId + "' is already assigned to another patient.");
            }
        }
        patient.setPatientId(patientId);

        // 1. Generate Vault Transit KEK & DEK for Patient Profile
        UUID patientUuid = UUID.randomUUID();
        patient.setId(patientUuid);
        String keyId = patientUuid.toString();
        String vaultKeyName = "patient_" + patientUuid;
        byte[] dek = envelopeEncryptionService.generateDek();
        String wrappedDek = null;
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload;
        try {
            vaultKmsService.ensureKeyExists(vaultKeyName);
            wrappedDek = vaultKmsService.wrapDek(vaultKeyName, dek);

            // 2. Build demographic PII JSON payload and envelope encrypt
            Map<String, Object> piiPayload = new HashMap<>();
            piiPayload.put("firstName", request.getFirstName());
            piiPayload.put("lastName", request.getLastName());
            piiPayload.put("dateOfBirth", request.getDateOfBirth());
            piiPayload.put("gender", request.getGender());
            piiPayload.put("email", request.getEmail());
            piiPayload.put("phoneNumber", request.getPhoneNumber());
            piiPayload.put("address", request.getAddress());
            piiPayload.put("nhsNumber", request.getNhsNumber());
            piiPayload.put("bloodType", request.getBloodType());
            piiPayload.put("emergencyContactName", request.getEmergencyContactName());
            piiPayload.put("emergencyContactPhone", request.getEmergencyContactPhone());
            piiPayload.put("emergencyContactRelationship", request.getEmergencyContactRelationship());
            piiPayload.put("insuranceProvider", request.getInsuranceProvider());
            piiPayload.put("insurancePolicyNumber", request.getInsurancePolicyNumber());
            piiPayload.put("insuranceGroupNumber", request.getInsuranceGroupNumber());

            String piiJson = objectMapper.writeValueAsString(piiPayload);
            byte[] aad = patient.getPatientId() != null ? patient.getPatientId().getBytes(StandardCharsets.UTF_8) : null;
            encryptedPayload =
                    envelopeEncryptionService.encrypt(piiJson.getBytes(StandardCharsets.UTF_8), dek, aad);

            EncryptionKey encryptionKey = new EncryptionKey(keyId, vaultKeyName, wrappedDek, encryptedPayload.ivBase64());
            patient.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
            patient.setEncryptionKey(encryptionKey);
        } catch (Exception e) {
            log.error("Failed to encrypt patient demographics: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to encrypt patient demographics", e);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }

        // 3. Populate HMAC-SHA256 blind indexes for fast O(1) searches
        patient.setBlindIndexNhs(cryptoService.computeBlindIndex(request.getNhsNumber(), null));
        patient.setBlindIndexMrn(cryptoService.computeBlindIndex(patient.getPatientId(), null));
        patient.setBlindIndexLastName(cryptoService.computeBlindIndex(request.getLastName(), null));

        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        if (request.getDateOfBirth() != null && !request.getDateOfBirth().isBlank()) {
            patient.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
        }
        patient.setGender(request.getGender());
        patient.setEmail(request.getEmail());
        patient.setPhoneNumber(request.getPhoneNumber());
        patient.setAddress(request.getAddress());
        patient.setNhsNumber(request.getNhsNumber());
        patient.setBloodType(request.getBloodType());
        patient.setEmergencyContactName(request.getEmergencyContactName());
        patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        patient.setEmergencyContactRelationship(request.getEmergencyContactRelationship());
        patient.setInsuranceProvider(request.getInsuranceProvider());
        patient.setInsurancePolicyNumber(request.getInsurancePolicyNumber());
        patient.setInsuranceGroupNumber(request.getInsuranceGroupNumber());

        if (request.getGpId() != null) {
            GP gp = gpRepository.findById(request.getGpId())
                    .orElseThrow(() -> new RuntimeException("GP not found: " + request.getGpId()));
            patient.setGp(gp);
        }

        // 3. Auto-provision or link User account for patient if email provided
        String temporaryPassword = null;
        if (request.getEmail() != null && !request.getEmail().trim().isBlank() && userRepository != null) {
            String email = request.getEmail().trim();
            Optional<User> existingUser = userRepository.findByEmail(email);
            User patientUser;
            if (existingUser.isPresent()) {
                patientUser = existingUser.get();
            } else if (passwordEncoder != null) {
                temporaryPassword = TemporaryPasswordGenerator.generate();
                patientUser = new User();
                patientUser.setEmail(email);
                patientUser.setPassword(passwordEncoder.encode(temporaryPassword));
                patientUser.setRole(Role.PATIENT);
                patientUser = userRepository.save(patientUser);
                log.info("Auto-provisioned User account for patient {} with email: {}", patientId, email);
            } else {
                patientUser = null;
            }
            if (patientUser != null) {
                patient.setUser(patientUser);
            }
        }

        Patient saved = patientRepository.save(patient);

        eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .patientId(saved.getPatientId())
                .eventType("PATIENT_CREATED")
                .vaultKeyName(vaultKeyName)
                .wrappedDek(wrappedDek)
                .iv(encryptedPayload.ivBase64())
                .encryptedDataBlob(encryptedPayload.ciphertextBase64())
                .timestamp(LocalDateTime.now())
                .build());

        PatientResponse resp = toResponse(saved);
        if (temporaryPassword != null) {
            resp.setTemporaryPassword(temporaryPassword);
        }
        if (resp.isActive() && !resp.isShredded()) {
            patientCacheService.put(resp.getPatientId(), resp);
        }
        return resp;
    }

    private String generateUniquePatientId() {
        return "PAT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    @Transactional
    public PatientResponse update(String patientId, PatientRequest request) {
        Patient patient = patientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found: " + patientId));

        if (patient.isShredded()) {
            throw new IllegalStateException("Cannot update a crypto-shredded patient profile");
        }

        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        if (request.getDateOfBirth() != null && !request.getDateOfBirth().isBlank()) {
            patient.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
        } else {
            patient.setDateOfBirth(null);
        }
        patient.setGender(request.getGender());
        patient.setEmail(request.getEmail());
        patient.setPhoneNumber(request.getPhoneNumber());
        patient.setAddress(request.getAddress());
        patient.setNhsNumber(request.getNhsNumber());
        patient.setBloodType(request.getBloodType());
        patient.setEmergencyContactName(request.getEmergencyContactName());
        patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        patient.setEmergencyContactRelationship(request.getEmergencyContactRelationship());
        patient.setInsuranceProvider(request.getInsuranceProvider());
        patient.setInsurancePolicyNumber(request.getInsurancePolicyNumber());
        patient.setInsuranceGroupNumber(request.getInsuranceGroupNumber());

        // Update HMAC-SHA256 blind indexes
        patient.setBlindIndexNhs(cryptoService.computeBlindIndex(request.getNhsNumber(), null));
        patient.setBlindIndexMrn(cryptoService.computeBlindIndex(patient.getPatientId(), null));
        patient.setBlindIndexLastName(cryptoService.computeBlindIndex(request.getLastName(), null));

        if (request.getGpId() != null) {
            GP gp = gpRepository.findById(request.getGpId())
                    .orElseThrow(() -> new RuntimeException("GP not found: " + request.getGpId()));
            patient.setGp(gp);
        } else {
            patient.setGp(null);
        }

        if (request.getEmail() != null && !request.getEmail().trim().isBlank() && userRepository != null) {
            userRepository.findByEmail(request.getEmail().trim()).ifPresent(patient::setUser);
        }

        // Re-encrypt demographic PII
        if (patient.getEncryptionKey() != null && !patient.getEncryptionKey().isInvalidated()) {
            byte[] dek = null;
            try {
                dek = vaultKmsService.unwrapDek(
                        patient.getEncryptionKey().getVaultKeyName(),
                        patient.getEncryptionKey().getWrappedDek());

                Map<String, Object> piiPayload = new HashMap<>();
                piiPayload.put("firstName", request.getFirstName());
                piiPayload.put("lastName", request.getLastName());
                piiPayload.put("dateOfBirth", request.getDateOfBirth());
                piiPayload.put("gender", request.getGender());
                piiPayload.put("email", request.getEmail());
                piiPayload.put("phoneNumber", request.getPhoneNumber());
                piiPayload.put("address", request.getAddress());
                piiPayload.put("nhsNumber", request.getNhsNumber());
                piiPayload.put("bloodType", request.getBloodType());
                piiPayload.put("emergencyContactName", request.getEmergencyContactName());
                piiPayload.put("emergencyContactPhone", request.getEmergencyContactPhone());
                piiPayload.put("emergencyContactRelationship", request.getEmergencyContactRelationship());
                piiPayload.put("insuranceProvider", request.getInsuranceProvider());
                piiPayload.put("insurancePolicyNumber", request.getInsurancePolicyNumber());
                piiPayload.put("insuranceGroupNumber", request.getInsuranceGroupNumber());

                String piiJson = objectMapper.writeValueAsString(piiPayload);
                byte[] aad = patient.getPatientId() != null ? patient.getPatientId().getBytes(StandardCharsets.UTF_8) : null;
                EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                    envelopeEncryptionService.encrypt(piiJson.getBytes(StandardCharsets.UTF_8), dek, aad);

                patient.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
                patient.getEncryptionKey().setIv(encryptedPayload.ivBase64());
            } catch (Exception e) {
                log.error("Failed to re-encrypt demographic payload for patient {}: {}", patientId, e.getMessage(), e);
                throw new IllegalStateException("Failed to re-encrypt patient demographics; update aborted.", e);
            } finally {
                if (dek != null) {
                    Arrays.fill(dek, (byte) 0);
                }
            }
        }

        Patient updated = patientRepository.save(patient);

        if (patient.getEncryptionKey() != null) {
            eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                    .eventId(UUID.randomUUID())
                    .patientId(updated.getPatientId())
                    .eventType("PATIENT_UPDATED")
                    .vaultKeyName(patient.getEncryptionKey().getVaultKeyName())
                    .wrappedDek(patient.getEncryptionKey().getWrappedDek())
                    .iv(patient.getEncryptionKey().getIv())
                    .encryptedDataBlob(updated.getEncryptedDataBlob())
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        PatientResponse resp = toResponse(updated);
        if (resp.isActive() && !resp.isShredded()) {
            patientCacheService.put(resp.getPatientId(), resp);
        } else {
            patientCacheService.evict(patientId);
        }
        return resp;
    }

    @Transactional
    public void deactivate(String patientId) {
        Patient patient = patientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found: " + patientId));
        patient.setActive(false);
        patientRepository.save(patient);
        patientCacheService.evict(patientId);

        eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .patientId(patientId)
                .eventType("PATIENT_DEACTIVATED")
                .timestamp(LocalDateTime.now())
                .build());
    }

    public PatientResponse toResponse(Patient patient) {
        boolean isShredded = patient.isShredded() ||
                (patient.getEncryptionKey() != null && patient.getEncryptionKey().isInvalidated());

        String firstName = patient.getFirstName();
        String lastName = patient.getLastName();
        LocalDate dob = patient.getDateOfBirth();
        String gender = patient.getGender();
        String email = patient.getEmail();
        String phone = patient.getPhoneNumber();
        String address = patient.getAddress();
        String nhs = patient.getNhsNumber();
        String bloodType = patient.getBloodType();
        String emergencyContactName = patient.getEmergencyContactName();
        String emergencyContactPhone = patient.getEmergencyContactPhone();
        String emergencyContactRelationship = patient.getEmergencyContactRelationship();
        String insuranceProvider = patient.getInsuranceProvider();
        String insurancePolicyNumber = patient.getInsurancePolicyNumber();
        String insuranceGroupNumber = patient.getInsuranceGroupNumber();

        // If encrypted data blob exists and key is valid, unwrap and verify via Vault
        if (!isShredded && patient.getEncryptedDataBlob() != null && patient.getEncryptionKey() != null) {
            byte[] dek = null;
            byte[] decryptedBytes = null;
            try {
                dek = vaultKmsService.unwrapDek(
                        patient.getEncryptionKey().getVaultKeyName(),
                        patient.getEncryptionKey().getWrappedDek());
                byte[] aad = patient.getPatientId() != null ? patient.getPatientId().getBytes(StandardCharsets.UTF_8) : null;
                decryptedBytes = envelopeEncryptionService.decrypt(
                        patient.getEncryptedDataBlob(),
                        patient.getEncryptionKey().getIv(),
                        dek,
                        aad);
                String piiJson = new String(decryptedBytes, StandardCharsets.UTF_8);
                Map<?, ?> map = objectMapper.readValue(piiJson, Map.class);
                firstName = (String) map.get("firstName");
                lastName = (String) map.get("lastName");
                gender = (String) map.get("gender");
                email = (String) map.get("email");
                phone = (String) map.get("phoneNumber");
                address = (String) map.get("address");
                nhs = (String) map.get("nhsNumber");
                String dobStr = (String) map.get("dateOfBirth");
                if (dobStr != null && !dobStr.isBlank()) {
                    dob = LocalDate.parse(dobStr);
                }
                if (map.containsKey("bloodType")) bloodType = (String) map.get("bloodType");
                if (map.containsKey("emergencyContactName")) emergencyContactName = (String) map.get("emergencyContactName");
                if (map.containsKey("emergencyContactPhone")) emergencyContactPhone = (String) map.get("emergencyContactPhone");
                if (map.containsKey("emergencyContactRelationship")) emergencyContactRelationship = (String) map.get("emergencyContactRelationship");
                if (map.containsKey("insuranceProvider")) insuranceProvider = (String) map.get("insuranceProvider");
                if (map.containsKey("insurancePolicyNumber")) insurancePolicyNumber = (String) map.get("insurancePolicyNumber");
                if (map.containsKey("insuranceGroupNumber")) insuranceGroupNumber = (String) map.get("insuranceGroupNumber");
            } catch (Exception e) {
                log.warn("Vault decryption failed for patient {}: key destroyed or invalid", patient.getPatientId());
                isShredded = true;
            } finally {
                if (dek != null) {
                    Arrays.fill(dek, (byte) 0);
                }
                if (decryptedBytes != null) {
                    Arrays.fill(decryptedBytes, (byte) 0);
                }
            }
        }

        if (isShredded) {
            firstName = "[SHREDDED]";
            lastName = "[SHREDDED]";
            dob = null;
            gender = "[SHREDDED]";
            email = null;
            phone = null;
            address = null;
            nhs = null;
            bloodType = null;
            emergencyContactName = null;
            emergencyContactPhone = null;
            emergencyContactRelationship = null;
            insuranceProvider = null;
            insurancePolicyNumber = null;
            insuranceGroupNumber = null;
        }

        return PatientResponse.builder()
                .id(patient.getId())
                .patientId(patient.getPatientId())
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dob)
                .gender(gender)
                .email(email)
                .phoneNumber(phone)
                .address(address)
                .nhsNumber(nhs)
                .bloodType(bloodType)
                .emergencyContactName(emergencyContactName)
                .emergencyContactPhone(emergencyContactPhone)
                .emergencyContactRelationship(emergencyContactRelationship)
                .insuranceProvider(insuranceProvider)
                .insurancePolicyNumber(insurancePolicyNumber)
                .insuranceGroupNumber(insuranceGroupNumber)
                .gp(patient.getGp() != null ? toGpResponse(patient.getGp()) : null)
                .isActive(patient.isActive() && !isShredded)
                .shredded(isShredded)
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .build();
    }

    private GpResponse toGpResponse(GP gp) {
        return GpResponse.builder()
                .id(gp.getId())
                .firstName(gp.getFirstName())
                .lastName(gp.getLastName())
                .email(gp.getEmail())
                .phoneNumber(gp.getPhoneNumber())
                .gmcNumber(gp.getGmcNumber())
                .specialisation(gp.getSpecialisation())
                .practiceName(gp.getPracticeName())
                .isActive(gp.isActive())
                .createdAt(gp.getCreatedAt())
                .build();
    }
}
