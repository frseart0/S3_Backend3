package com.bancoxyz.bff.web.dto;

import com.bancoxyz.domain.contract.PerfilCuenta;

/**
 * Perfil para la banca en linea: incluye la etiqueta legible del tipo de
 * cuenta y el estado con que el batch clasifico el registro, porque la web
 * tiene espacio para mostrar el origen del dato.
 */
public record PerfilWeb(
        Long cuentaId,
        String nombre,
        String tipo,
        String tipoDescripcion,
        String estadoRegistro,
        String observacion) {

    public static PerfilWeb desde(PerfilCuenta perfil) {
        return new PerfilWeb(
                perfil.cuentaId(),
                perfil.nombre(),
                perfil.tipo() == null ? null : perfil.tipo().name(),
                perfil.tipo() == null ? "Sin clasificar" : perfil.tipo().etiqueta(),
                perfil.estado(),
                perfil.observacion());
    }
}
