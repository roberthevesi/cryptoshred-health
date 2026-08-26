package com.roberthevesi.cryptoshred_health.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_PATIENT_EVENTS = "patient-record-events";

    /** 1 Year Retention (365 days in milliseconds = 31,536,000,000 ms). */
    @Value("${app.kafka.retention-ms:31536000000}")
    private String retentionMs;

    @Bean
    public NewTopic patientRecordEventsTopic() {
        return TopicBuilder.name(TOPIC_PATIENT_EVENTS)
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, retentionMs)
                .build();
    }
}
