package com.bancoxyz.domain.contract;

/**
 * Verificacion de credenciales contra el core-api. El {@code canal} viaja en
 * la solicitud porque un usuario puede estar habilitado en unos canales y no
 * en otros (ej. un cliente sin tarjeta no puede operar en cajeros).
 */
public record SolicitudCredenciales(
        String usuario,
        String password,
        String canal) {
}
