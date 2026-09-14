package com.bancoxyz.bff.web.dto;

import java.util.List;

/** Sesion del canal web. Incluye los datos del titular para pintar el header. */
public record SesionWeb(
        String token,
        String tipo,
        long expiraEnSegundos,
        String usuario,
        String nombre,
        Long cuentaId,
        String canal,
        List<String> permisos) {
}
