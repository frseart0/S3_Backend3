package com.bancoxyz.core.error;

import java.time.Instant;

/**
 * Error del core-api. El par {@code codigo}/{@code mensaje} es el contrato que
 * los BFF leen para decidir si propagan el motivo al cliente final.
 */
public record ErrorRespuesta(String codigo, String mensaje, Instant ocurridoEn) {

    public static ErrorRespuesta de(String codigo, String mensaje) {
        return new ErrorRespuesta(codigo, mensaje, Instant.now());
    }
}
