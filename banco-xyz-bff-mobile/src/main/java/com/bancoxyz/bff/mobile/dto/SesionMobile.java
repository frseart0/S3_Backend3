package com.bancoxyz.bff.mobile.dto;

/**
 * Sesion movil: access token corto mas refresh token de larga duracion, para
 * que la app no tenga que pedir la clave cada vez que se abre.
 */
public record SesionMobile(
        String token,
        String refreshToken,
        long expiraEnSegundos,
        String nombre) {
}
