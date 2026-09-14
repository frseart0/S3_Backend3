package com.bancoxyz.core.autenticacion;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Fila de {@code usuarios_canal} tal como se guarda en la base. */
public record RegistroUsuario(
        long id,
        String usuario,
        String passwordHash,
        String nombre,
        long cuentaId,
        String canalesPermitidos,
        String tarjeta,
        String pinHash,
        int intentosFallidos,
        boolean bloqueado) {

    public Set<String> canales() {
        if (canalesPermitidos == null || canalesPermitidos.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(canalesPermitidos.split(","))
                .map(canal -> canal.trim().toLowerCase())
                .filter(canal -> !canal.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean habilitadoEn(String canal) {
        return canal != null && canales().contains(canal.trim().toLowerCase());
    }
}
