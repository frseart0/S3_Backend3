package com.bancoxyz.bff.common.error;

/**
 * El canal no pudo completar la operacion porque el nucleo no respondio.
 * No es un error de negocio: el cliente puede reintentar mas tarde.
 */
public class CanalNoDisponibleException extends RuntimeException {

    public CanalNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public CanalNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
