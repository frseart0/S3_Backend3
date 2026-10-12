package com.bancoxyz.domain.contract;

import java.util.List;

/** Datos personales que administra el microservicio de clientes. */
public record PerfilCliente(
        String usuario,
        String nombre,
        Long cuentaId,
        List<String> canales,
        boolean bloqueado) {
}
