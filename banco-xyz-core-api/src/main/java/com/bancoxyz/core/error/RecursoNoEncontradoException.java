package com.bancoxyz.core.error;

/** La cuenta, el periodo o el recurso pedido no existe en los datos migrados. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
