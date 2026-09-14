package com.bancoxyz.domain;

/**
 * Canales de atencion del banco. Cada canal tiene su propio BFF y su propio
 * emisor de tokens: el canal viaja en el claim {@code aud} del JWT y en la
 * cabecera {@code X-Channel} de las llamadas al core-api, de modo que el core
 * siempre sabe desde donde se origino una operacion.
 */
public enum Canal {

    WEB,
    MOBILE,
    ATM;

    /** Valor usado en el claim {@code aud} del JWT y en {@code X-Channel}. */
    public String codigo() {
        return name().toLowerCase();
    }

    /** Rol de Spring Security asociado al canal. */
    public String rol() {
        return "ROLE_" + name();
    }

    public static Canal desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
