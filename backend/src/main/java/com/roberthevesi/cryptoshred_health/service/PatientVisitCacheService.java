package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientVisitCacheService {

    private static final String CACHE_PREFIX = "patient_visit:";
    @Value("${app.redis.time-to-live-ms:900000}")
    private long ttlMs;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void put(UUID visitId, PatientVisitResponse response) {
        if (visitId == null || response == null) return;
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(CACHE_PREFIX + visitId, json, Duration.ofMillis(ttlMs));
            log.debug("Cached patient visit in Redis: {}", visitId);
        } catch (Exception e) {
            log.warn("Failed to cache patient visit {} in Redis: {}", visitId, e.getMessage());
        }
    }

    public PatientVisitResponse get(UUID visitId) {
        if (visitId == null) return null;
        try {
            String json = redisTemplate.opsForValue().get(CACHE_PREFIX + visitId);
            if (json == null) {
                log.info("🐢 [REDIS MISS] patient_visit:{} not found in Redis cache", visitId);
                return null;
            }
            log.info("⚡ [REDIS HIT] patient_visit:{} loaded from Redis cache", visitId);
            return objectMapper.readValue(json, PatientVisitResponse.class);
        } catch (Exception e) {
            log.warn("Failed to read patient visit {} from Redis: {}", visitId, e.getMessage());
            return null;
        }
    }

    public void evict(UUID visitId) {
        if (visitId == null) return;
        try {
            redisTemplate.delete(CACHE_PREFIX + visitId);
            log.info("Proactively evicted patient visit {} from Redis cache", visitId);
        } catch (Exception e) {
            log.warn("Failed to evict patient visit {} from Redis: {}", visitId, e.getMessage());
        }
    }
}
