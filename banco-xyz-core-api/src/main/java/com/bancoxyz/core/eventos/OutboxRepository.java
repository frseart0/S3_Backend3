package com.bancoxyz.core.eventos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbc;

    public OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(String tipo, String claveEvento, String payload) {
        jdbc.update("""
                INSERT INTO outbox_evento (tipo, clave_evento, payload)
                VALUES (?, ?, ?)
                """, tipo, claveEvento, payload);
    }

    public List<EventoPendiente> pendientes(int limite) {
        return jdbc.query("""
                SELECT id, tipo, clave_evento, payload
                FROM outbox_evento
                WHERE publicado = FALSE
                ORDER BY id
                LIMIT ?
                """, (rs, fila) -> new EventoPendiente(
                rs.getLong("id"),
                rs.getString("tipo"),
                rs.getString("clave_evento"),
                rs.getString("payload")), limite);
    }

    public void marcarPublicado(long id) {
        jdbc.update("""
                UPDATE outbox_evento
                SET publicado = TRUE, publicado_en = now()
                WHERE id = ?
                """, id);
    }

    public record EventoPendiente(long id, String tipo, String claveEvento, String payload) {
    }
}
