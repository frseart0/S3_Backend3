package com.bancoxyz.batch.common.exception;

/**
 * Se lanza cuando una categoria/tipo (tipo de transaccion, tipo de cuenta,
 * etc.) no pertenece al conjunto de valores de negocio validos.
 */
public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException(String message) {
        super(message);
    }
}
