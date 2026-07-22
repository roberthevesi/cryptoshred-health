package com.roberthevesi.cryptoshred_health.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_PATIENT_EVENTS = "patient-record-events";

    @Bean
    public NewTopic patientRecordEventsTopic() {
        return TopicBuilder.name(TOPIC_PATIENT_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
