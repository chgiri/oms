package com.giri.oms.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record KafkaAppProperties(Topics topics, Outbox outbox) {

    public record Topics(String orderEvents) {
    }

    public record Outbox(long pollIntervalMs, int batchSize) {
    }
}
