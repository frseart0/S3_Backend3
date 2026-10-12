package com.bancoxyz.domain.contract;

/** Datos no monetarios de una cuenta. Los campos nulos no se modifican. */
public record SolicitudMantenimientoCuenta(String alias, String tipo) {
}
