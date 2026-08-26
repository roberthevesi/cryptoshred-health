package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientCacheService {

    private static final String CACHE_PREFIX = "patient:";

    @Value("${app.redis.time-to-live-ms:900000}")
    private long ttlMs;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void put(String patientId, PatientResponse response) {
        if (patientId == null || response == null) return;
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(CACHE_PREFIX + patientId, json, Duration.ofMillis(ttlMs));
            log.debug("Cached patient demographic profile in Redis: {}", patientId);
        } catch (Exception e) {
            log.warn("Failed to cache patient {} in Redis: {}", patientId, e.getMessage());
        }
    }

    public PatientResponse get(String patientId) {
        if (patientId == null) return null;
        try {
            String json = redisTemplate.opsForValue().get(CACHE_PREFIX + patientId);
            if (json == null) return null;
            return objectMapper.readValue(json, PatientResponse.class);
        } catch (Exception e) {
            log.warn("Failed to read patient {} from Redis: {}", patientId, e.getMessage());
            return null;
        }
    }

    public void evict(String patientId) {
        if (patientId == null) return;
        try {
            redisTemplate.delete(CACHE_PREFIX + patientId);
            log.info("Proactively evicted patient {} from Redis cache", patientId);
        } catch (Exception e) {
            log.warn("Failed to evict patient {} from Redis: {}", patientId, e.getMessage());
        }
    }
}
