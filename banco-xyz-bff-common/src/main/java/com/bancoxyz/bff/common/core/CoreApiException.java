package com.bancoxyz.bff.common.core;

import org.springframework.http.HttpStatusCode;

/**
 * El core-api respondio con un error. Se conserva el estado y el codigo
 * originales para poder propagarlos: si el core rechaza un retiro por saldo
 * insuficiente, el cliente debe ver ese motivo y no un 500 generico.
 */
public class CoreApiException extends RuntimeException {

    private final HttpStatusCode estado;
    private final String codigo;

    public CoreApiException(HttpStatusCode estado, String codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    public HttpStatusCode estado() {
        return estado;
    }

    public String codigo() {
        return codigo;
    }
}
