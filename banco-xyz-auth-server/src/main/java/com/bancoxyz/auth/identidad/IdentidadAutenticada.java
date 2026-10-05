package com.bancoxyz.auth.identidad;

/**
 * Usuario ya verificado contra el core-api. Viaja como principal del token
 * para que el customizer copie cuenta, nombre y canal a los claims del JWT.
 */
public record IdentidadAutenticada(
        String usuario,
        Long cuentaId,
        String nombre,
        String canal,
        String dispositivoId) {
}
