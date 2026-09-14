package com.bancoxyz.batch.common.util;

import com.bancoxyz.batch.common.exception.InvalidDateFormatException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * El dataset legacy del Banco XYZ mezcla varios formatos de fecha en el
 * mismo archivo CSV. Esta utilidad intenta parsear una fecha probando, en
 * orden, cada uno de los formatos observados en los datos.
 */
public final class DateParsingUtils {

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private DateParsingUtils() {
    }

    public static LocalDate parseFlexible(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new InvalidDateFormatException("Fecha vacia");
        }
        String trimmed = rawValue.trim();
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // se intenta con el siguiente formato soportado
            }
        }
        throw new InvalidDateFormatException("Formato de fecha no reconocido: '" + rawValue + "'");
    }
}
