package com.bancoxyz.notificaciones;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErroresConfig {

    @Bean
    public NewTopic topicoRetiros(@Value("${notificaciones.topico:banco.retiros.realizados}") String topico) {
        return TopicBuilder.name(topico).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic topicoDlq(@Value("${notificaciones.topico-dlq:banco.retiros.dlq}") String topico) {
        return TopicBuilder.name(topico).partitions(1).replicas(1).build();
    }

    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafka,
                                           @Value("${notificaciones.topico-dlq:banco.retiros.dlq}") String dlq) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, ex) -> new TopicPartition(dlq, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
    }
}
