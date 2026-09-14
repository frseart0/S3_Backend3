package com.bancoxyz.batch.common.util;

import java.math.BigDecimal;

/**
 * Utilidades para convertir campos numericos crudos (String) proveniente de
 * los CSV legacy, que pueden llegar vacios o con formato invalido, sin
 * lanzar excepciones no controladas.
 */
public final class NumberParsingUtils {

    private NumberParsingUtils() {
    }

    /**
     * @return el BigDecimal parseado, o {@code null} si el valor esta vacio
     * o no es numerico (la decision de que hacer con un null queda en manos
     * del ItemProcessor que conoce la regla de negocio del campo).
     */
    public static BigDecimal parseDecimalOrNull(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Integer parseIntOrNull(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Long parseLongOrNull(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
