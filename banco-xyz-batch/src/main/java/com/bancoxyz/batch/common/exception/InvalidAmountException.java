package com.bancoxyz.batch.common.exception;

/**
 * Se lanza cuando un monto/saldo es obligatorio para poder registrar el
 * movimiento pero llega vacio, no numerico o fuera de un rango aceptable.
 */
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
