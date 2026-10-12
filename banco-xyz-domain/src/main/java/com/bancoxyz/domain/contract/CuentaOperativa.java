package com.bancoxyz.domain.contract;

/** Estado de ciclo de vida de una cuenta: ABIERTA o CERRADA. */
public record CuentaOperativa(
        Long cuentaId,
        String tipo,
        String estado,
        String alias,
        String clienteUsuario) {
}
