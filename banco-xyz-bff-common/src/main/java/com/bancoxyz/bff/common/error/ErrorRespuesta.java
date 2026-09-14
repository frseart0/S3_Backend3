package com.bancoxyz.bff.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Formato unico de error de los tres BFF. Los campos vacios no se serializan,
 * asi el canal movil no paga el costo de un cuerpo de error inflado.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorRespuesta(
        String codigo,
        String mensaje,
        Instant ocurridoEn,
        List<String> detalles) {

    public static ErrorRespuesta de(String codigo, String mensaje) {
        return new ErrorRespuesta(codigo, mensaje, Instant.now(), List.of());
    }

    public static ErrorRespuesta de(String codigo, String mensaje, List<String> detalles) {
        return new ErrorRespuesta(codigo, mensaje, Instant.now(), detalles);
    }
}
