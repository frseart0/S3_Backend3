package com.bancoxyz.pagos.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Entrega a Kafka lo que el outbox ya confirmo en la misma transaccion del
 * movimiento. Los retiros van a un topico y el resto de las transacciones a otro.
 * Si el envio falla, la fila sigue pendiente y el siguiente ciclo la reintenta.
 */
@Component
@ConditionalOnProperty(name = "core.eventos.habilitado", havingValue = "true")
public class PublicadorRetiros {

    private static final Logger log = LoggerFactory.getLogger(PublicadorRetiros.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final EventosProperties propiedades;

    public PublicadorRetiros(OutboxRepository outbox,
                             KafkaTemplate<String, String> kafka,
                             EventosProperties propiedades) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.propiedades = propiedades;
    }

    @Scheduled(fixedDelayString = "${core.eventos.intervalo-ms:2000}")
    public void publicarPendientes() {
        for (OutboxRepository.EventoPendiente evento : outbox.pendientes(50)) {
            try {
                kafka.send(topicoDe(evento.tipo()), evento.claveEvento(), evento.payload())
                        .get(5, TimeUnit.SECONDS);
                outbox.marcarPublicado(evento.id());
            } catch (Exception ex) {
                log.warn("No se pudo publicar el evento {} ({}): {}",
                        evento.claveEvento(), evento.tipo(), ex.getMessage());
                break;
            }
        }
    }

    private String topicoDe(String tipo) {
        if ("transaccion.completada".equals(tipo)) {
            return propiedades.topicoTransacciones();
        }
        return propiedades.topico();
    }
}
