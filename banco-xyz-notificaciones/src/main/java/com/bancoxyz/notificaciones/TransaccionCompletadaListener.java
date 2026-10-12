package com.bancoxyz.notificaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransaccionCompletadaListener {

    private static final Logger log = LoggerFactory.getLogger(TransaccionCompletadaListener.class);

    private final NotificacionRetiroRepository repositorio;
    private final ObjectMapper objectMapper;

    public TransaccionCompletadaListener(NotificacionRetiroRepository repositorio, ObjectMapper objectMapper) {
        this.repositorio = repositorio;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${notificaciones.topico-transacciones:banco.transacciones.completadas}")
    public void consumir(String payload) throws Exception {
        JsonNode json = objectMapper.readTree(payload);
        String clave = texto(json, "claveIdempotencia");
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException("El evento de transaccion no trae claveIdempotencia");
        }
        repositorio.guardarTransaccion(
                clave,
                texto(json, "tipo"),
                texto(json, "codigoAutorizacion"),
                numero(json, "cuentaId"),
                numero(json, "cuentaDestinoId"),
                json.hasNonNull("monto") ? new BigDecimal(json.get("monto").asText()) : null,
                texto(json, "canal"),
                payload);
        log.info("Transaccion {} registrada para la clave {}", texto(json, "tipo"), clave);
    }

    private static String texto(JsonNode json, String campo) {
        return json.hasNonNull(campo) ? json.get(campo).asText() : null;
    }

    private static Long numero(JsonNode json, String campo) {
        return json.hasNonNull(campo) ? json.get(campo).asLong() : null;
    }
}
