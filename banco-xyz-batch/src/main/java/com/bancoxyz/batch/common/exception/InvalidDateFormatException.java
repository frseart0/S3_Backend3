package com.bancoxyz.batch.common.exception;

/**
 * Se lanza cuando una fecha del archivo legacy no coincide con ninguno de los
 * formatos soportados (yyyy-MM-dd, dd-MM-yyyy, dd/MM/yyyy, yyyy/MM/dd) o es
 * un valor de calendario invalido (ej. mes 13).
 */
public class InvalidDateFormatException extends RuntimeException {

    public InvalidDateFormatException(String message) {
        super(message);
    }
}
