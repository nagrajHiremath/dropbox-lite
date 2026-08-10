package com.dropbox.account_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit String/String consumer config, mirroring metadata-service's
 * KafkaConsumerConfig exactly (group-id/deserializers visible in one place,
 * retry-then-DLT error handling per TECHNICAL_DESIGN.md 41) - this service's
 * first Kafka consumer, so there's no existing account-service config to
 * extend instead.
 */
@Slf4j
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final String GROUP_ID = "account-service";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(kafkaTemplate);
        ConsumerRecordRecoverer loggingRecoverer = (record, ex) -> {
            log.error("Sending to DLT: topic={} partition={} offset={} eventId={} reason={}",
                    record.topic(), record.partition(), record.offset(),
                    extractEventId((String) record.value(), objectMapper), ex.getMessage());
            dlt.accept(record, ex);
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(loggingRecoverer, new FixedSequenceBackOff(2000L, 10_000L, 30_000L));
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retry attempt {} for topic={} partition={} offset={} eventId={}: {}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(),
                        extractEventId((String) record.value(), objectMapper), ex.getMessage()));
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    private static String extractEventId(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(json).path("eventId").asText("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }
}
