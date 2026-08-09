package com.dropbox.metadata_service.config;

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
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit String/String consumer config, mirroring upload-service's
 * KafkaProducerConfig - avoids relying on Boot's ambiguous default
 * autoconfiguration and keeps group-id/deserializers in one visible place.
 *
 * EVT-03: the listener container's error handler retries a failing record
 * with a fixed 2s/10s/30s backoff (TECHNICAL_DESIGN.md 41), then publishes it
 * to <original-topic>.DLT via DeadLetterPublishingRecoverer - its default
 * destination resolver already appends ".DLT", matching the documented
 * storage.lifecycle.v1.DLT topic with no custom resolver needed. That
 * recoverer also automatically adds original-topic/partition/offset and
 * exception-message/stacktrace headers and republishes the untouched record
 * value, satisfying "original event identifiable"/"error reason visible"
 * without extra code.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final String GROUP_ID = "metadata-service";

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
            ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedSequenceBackOff(2000L, 10_000L, 30_000L));
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
