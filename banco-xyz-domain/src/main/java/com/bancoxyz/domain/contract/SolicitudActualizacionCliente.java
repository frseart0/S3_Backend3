package com.bancoxyz.domain.contract;

/** Mantenimiento del perfil. Los campos nulos no se modifican. */
public record SolicitudActualizacionCliente(
        String nombre,
        String canales,
        Boolean bloqueado) {
}
