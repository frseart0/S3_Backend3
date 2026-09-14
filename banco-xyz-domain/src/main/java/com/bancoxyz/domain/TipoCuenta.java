package com.bancoxyz.domain;

/**
 * Tipos de cuenta reconocidos por el banco. El dataset legacy los trae en
 * minusculas y con valores basura (ej. {@code -1}), por lo que el parseo es
 * tolerante y devuelve {@code null} en vez de fallar.
 */
public enum TipoCuenta {

    AHORRO,
    PRESTAMO,
    HIPOTECA;

    public static TipoCuenta desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Etiqueta legible para los clientes (web muestra el nombre completo). */
    public String etiqueta() {
        return switch (this) {
            case AHORRO -> "Cuenta de ahorro";
            case PRESTAMO -> "Prestamo";
            case HIPOTECA -> "Credito hipotecario";
        };
    }
}
