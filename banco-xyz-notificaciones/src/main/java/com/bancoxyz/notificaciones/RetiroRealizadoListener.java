package com.bancoxyz.notificaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class RetiroRealizadoListener {

    private static final Logger log = LoggerFactory.getLogger(RetiroRealizadoListener.class);

    private final NotificacionRetiroRepository repositorio;
    private final ObjectMapper objectMapper;

    public RetiroRealizadoListener(NotificacionRetiroRepository repositorio, ObjectMapper objectMapper) {
        this.repositorio = repositorio;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${notificaciones.topico:banco.retiros.realizados}")
    public void consumir(String payload) throws Exception {
        JsonNode json = objectMapper.readTree(payload);
        String clave = texto(json, "claveIdempotencia");
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException("El evento de retiro no trae claveIdempotencia");
        }
        repositorio.guardar(
                clave,
                texto(json, "codigoAutorizacion"),
                json.hasNonNull("cuentaId") ? json.get("cuentaId").asLong() : null,
                json.hasNonNull("monto") ? new BigDecimal(json.get("monto").asText()) : null,
                texto(json, "canal"),
                payload);
        log.info("Comprobante asincrono registrado para el retiro {}", clave);
    }

    private static String texto(JsonNode json, String campo) {
        return json.hasNonNull(campo) ? json.get(campo).asText() : null;
    }
}
