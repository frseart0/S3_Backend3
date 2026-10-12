package com.bancoxyz.bff.common.error;

/**
 * El canal no pudo completar la operacion porque el nucleo no respondio.
 * No es un error de negocio: el cliente puede reintentar mas tarde.
 */
public class CanalNoDisponibleException extends RuntimeException {

    private final String codigo;

    public CanalNoDisponibleException(String mensaje) {
        this("CAJERO_NO_DISPONIBLE", mensaje, null);
    }

    public CanalNoDisponibleException(String mensaje, Throwable causa) {
        this("CAJERO_NO_DISPONIBLE", mensaje, causa);
    }

    public CanalNoDisponibleException(String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
