package com.bancoxyz.domain.contract;

import java.util.List;

/**
 * Resultado de una verificacion de identidad. El core-api nunca emite tokens:
 * solo confirma quien es el usuario y en que canales puede operar, y cada BFF
 * decide como representar esa sesion (duracion, claims, refresh).
 */
public record ResultadoAutenticacion(
        boolean autenticado,
        String usuario,
        Long cuentaId,
        String nombre,
        List<String> canalesPermitidos,
        boolean bloqueado,
        Integer intentosRestantes,
        String motivo) {

    public static ResultadoAutenticacion rechazo(String motivo) {
        return new ResultadoAutenticacion(false, null, null, null, List.of(), false, null, motivo);
    }

    public static ResultadoAutenticacion bloqueado(String motivo) {
        return new ResultadoAutenticacion(false, null, null, null, List.of(), true, 0, motivo);
    }
}
