package com.bancoxyz.batch.common.exception;

/**
 * Se lanza cuando un registro se identifica como duplicado exacto de uno ya
 * procesado en la misma ejecucion del Job y la regla de negocio indica que
 * debe ser descartado (en lugar de marcado y conservado).
 */
public class DuplicateRecordException extends RuntimeException {

    public DuplicateRecordException(String message) {
        super(message);
    }
}
