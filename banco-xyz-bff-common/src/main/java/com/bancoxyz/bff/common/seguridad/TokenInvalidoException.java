package com.bancoxyz.bff.common.seguridad;

/** El token no se pudo validar: firma, vigencia, emisor o audiencia incorrectos. */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String mensaje) {
        super(mensaje);
    }

    public TokenInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
