package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.PatientRecordResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientRecordCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.redis.time-to-live-ms:600000}")
    private long ttlMs;

    private static final String KEY_PREFIX = "patient:record:";

    public PatientRecordResponse get(UUID id) {
        String key = buildKey(id);
        try {
            Object obj = redisTemplate.opsForValue().get(key);
            if (obj instanceof PatientRecordResponse response) {
                log.info("Redis CACHE HIT for record {}", id);
                return response;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch record {} from Redis cache: {}", id, e.getMessage());
        }
        log.info("Redis CACHE MISS for record {}", id);
        return null;
    }

    public void put(UUID id, PatientRecordResponse response) {
        if (id == null || response == null) return;
        String key = buildKey(id);
        try {
            redisTemplate.opsForValue().set(key, response, Duration.ofMillis(ttlMs));
            log.info("Redis CACHE PUT for record {} (TTL: {}ms)", id, ttlMs);
        } catch (Exception e) {
            log.warn("Failed to store record {} in Redis cache: {}", id, e.getMessage());
        }
    }

    public void evict(UUID id) {
        if (id == null) return;
        String key = buildKey(id);
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.info("Redis CACHE EVICT for record {} (Success: {})", id, deleted);
        } catch (Exception e) {
            log.warn("Failed to evict record {} from Redis cache: {}", id, e.getMessage());
        }
    }

    public void clear() {
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Redis CACHE CLEAR flushed {} keys", keys.size());
            }
        } catch (Exception e) {
            log.warn("Failed to clear Redis cache: {}", e.getMessage());
        }
    }

    private String buildKey(UUID id) {
        return KEY_PREFIX + id.toString();
    }
}
