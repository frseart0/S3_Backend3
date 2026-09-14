package com.bancoxyz.domain;

/**
 * Estado con el que los Jobs batch clasifican cada fila del dataset legacy.
 * Es el vocabulario que comparten la columna {@code estado} de las tablas de
 * negocio y las APIs que las exponen.
 */
public enum EstadoRegistro {

    /** La fila venia correcta en el archivo de origen. */
    VALIDA,

    /** Se aplico una correccion automatica documentada en la observacion. */
    VALIDA_CORREGIDA,

    /** Se conserva para auditoria pero no cumple las reglas de negocio. */
    ANOMALIA,

    /** Repetida dentro de la misma ejecucion del Job. */
    DUPLICADA;

    public static EstadoRegistro desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return ANOMALIA;
        }
        return valueOf(valor.trim().toUpperCase());
    }

    /** Solo los registros validos (con o sin correccion) cuentan para el saldo. */
    public boolean esConfiable() {
        return this == VALIDA || this == VALIDA_CORREGIDA;
    }
}
