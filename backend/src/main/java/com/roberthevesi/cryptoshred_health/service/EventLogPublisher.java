package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.config.KafkaTopicConfig;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventLogPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishEvent(PatientVisitEventDto eventDto) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(eventDto);
            String key = eventDto.getVisitId() != null
                    ? eventDto.getVisitId().toString()
                    : (eventDto.getPatientId() != null ? eventDto.getPatientId() : eventDto.getEventId().toString());

            kafkaTemplate.send(KafkaTopicConfig.TOPIC_PATIENT_EVENTS, key, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Kafka event log publication failed for event {}: {}",
                                     eventDto.getEventId(), ex.getMessage());
                        } else {
                            log.info("Kafka event [{}] published to topic {} (partition {}, offset {})",
                                    eventDto.getEventType(),
                                    result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize or publish Kafka event {}: {}", eventDto.getEventId(), e.getMessage());
        }
    }
}
