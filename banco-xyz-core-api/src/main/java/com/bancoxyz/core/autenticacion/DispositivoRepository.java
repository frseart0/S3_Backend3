package com.bancoxyz.core.autenticacion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Cajeros registrados: el hardware tambien es una identidad que se verifica. */
@Repository
public class DispositivoRepository {

    private final JdbcTemplate jdbc;

    public DispositivoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Dispositivo> buscarActivo(String dispositivoId) {
        String sql = """
                SELECT dispositivo_id, clave_hash, ubicacion
                FROM dispositivos_atm
                WHERE dispositivo_id = ? AND activo = TRUE
                """;
        return jdbc.query(sql, (rs, fila) -> new Dispositivo(
                rs.getString("dispositivo_id"),
                rs.getString("clave_hash"),
                rs.getString("ubicacion")), dispositivoId).stream().findFirst();
    }

    public boolean existe(String dispositivoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM dispositivos_atm WHERE dispositivo_id = ?)",
                Boolean.class, dispositivoId));
    }

    public void crear(String dispositivoId, String claveHash, String ubicacion) {
        jdbc.update("""
                INSERT INTO dispositivos_atm (dispositivo_id, clave_hash, ubicacion)
                VALUES (?, ?, ?)
                """, dispositivoId, claveHash, ubicacion);
    }

    public record Dispositivo(String dispositivoId, String claveHash, String ubicacion) {
    }
}
