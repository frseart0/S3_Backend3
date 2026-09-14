package com.bancoxyz.domain.contract;

/**
 * Verificacion de tarjeta + PIN desde un cajero automatico. El
 * {@code dispositivoId} y su clave se validan contra {@code dispositivos_atm}
 * antes de siquiera comprobar el PIN.
 */
public record SolicitudPin(
        String tarjeta,
        String pin,
        String dispositivoId,
        String dispositivoClave) {
}
