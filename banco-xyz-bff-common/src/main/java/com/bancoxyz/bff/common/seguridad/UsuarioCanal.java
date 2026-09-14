package com.bancoxyz.bff.common.seguridad;

import com.bancoxyz.domain.Canal;

/**
 * Identidad autenticada dentro de un BFF. Es el {@code principal} que los
 * controladores reciben con {@code @AuthenticationPrincipal}, y la cuenta que
 * trae es la unica que el usuario puede consultar: los endpoints no aceptan un
 * {@code cuentaId} por parametro, se toma siempre del token.
 *
 * @param dispositivoId cajero desde el que se abrio la sesion; {@code null} en web y movil
 */
public record UsuarioCanal(
        String usuario,
        Long cuentaId,
        String nombre,
        Canal canal,
        String dispositivoId) {

    public static UsuarioCanal de(String usuario, Long cuentaId, String nombre, Canal canal) {
        return new UsuarioCanal(usuario, cuentaId, nombre, canal, null);
    }
}
