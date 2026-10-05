package com.bancoxyz.core.eventos;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "core.eventos.habilitado", havingValue = "true")
public class EventosKafkaConfig {

    public static final String TOPICO_DLQ = "banco.retiros.dlq";

    @Bean
    public NewTopic topicoRetiros(EventosProperties propiedades) {
        return TopicBuilder.name(propiedades.topico()).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic topicoRetirosDlq() {
        return TopicBuilder.name(TOPICO_DLQ).partitions(1).replicas(1).build();
    }
}
