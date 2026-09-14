package com.bancoxyz.bff.common.error;

/**
 * Regla de negocio del canal incumplida antes de llamar al core (ej. un monto
 * de retiro que no es multiplo de la denominacion del cajero).
 */
public class OperacionInvalidaException extends RuntimeException {

    private final String codigo;

    public OperacionInvalidaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
