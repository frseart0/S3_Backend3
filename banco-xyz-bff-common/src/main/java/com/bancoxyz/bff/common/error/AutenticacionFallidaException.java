package com.bancoxyz.bff.common.error;

/**
 * Credenciales, PIN o dispositivo rechazados durante el login de un canal.
 * Se distingue el rechazo del bloqueo porque el cliente debe reaccionar
 * distinto: en un rechazo puede reintentar, en un bloqueo no.
 */
public class AutenticacionFallidaException extends RuntimeException {

    private final boolean bloqueado;

    private AutenticacionFallidaException(String mensaje, boolean bloqueado) {
        super(mensaje);
        this.bloqueado = bloqueado;
    }

    public static AutenticacionFallidaException rechazo(String mensaje) {
        return new AutenticacionFallidaException(mensaje, false);
    }

    public static AutenticacionFallidaException bloqueo(String mensaje) {
        return new AutenticacionFallidaException(mensaje, true);
    }

    public boolean bloqueado() {
        return bloqueado;
    }
}
