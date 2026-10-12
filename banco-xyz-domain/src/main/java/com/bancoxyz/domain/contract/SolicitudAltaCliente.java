package com.bancoxyz.domain.contract;

/** Alta de un cliente. {@code canales} es la lista separada por comas (web, mobile, atm). */
public record SolicitudAltaCliente(
        String usuario,
        String password,
        String nombre,
        Long cuentaId,
        String canales) {
}
