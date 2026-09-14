package com.bancoxyz.domain.contract;

import com.bancoxyz.domain.TipoCuenta;

/**
 * Perfil del titular de una cuenta, reconstruido a partir de lo que dejo el
 * Job de intereses en {@code cuentas_interes}.
 */
public record PerfilCuenta(
        Long cuentaId,
        String nombre,
        TipoCuenta tipo,
        String estado,
        String observacion) {
}
