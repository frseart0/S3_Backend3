package com.bancoxyz.core.error;

/**
 * Una regla de integridad del nucleo impidio la operacion (saldo insuficiente,
 * limite diario excedido, monto fuera de rango). Es un rechazo de negocio, no
 * un fallo: el canal debe mostrarle el motivo al cliente.
 */
public class OperacionRechazadaException extends RuntimeException {

    private final String codigo;

    public OperacionRechazadaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
