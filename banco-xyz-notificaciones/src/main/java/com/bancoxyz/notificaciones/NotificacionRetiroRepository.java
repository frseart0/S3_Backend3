package com.bancoxyz.notificaciones;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class NotificacionRetiroRepository {

    private final JdbcTemplate jdbc;

    public NotificacionRetiroRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotente por clave de evento: un reintento de Kafka no duplica el
     * comprobante.
     */
    public void guardar(String claveEvento,
                        String codigoAutorizacion,
                        Long cuentaId,
                        BigDecimal monto,
                        String canal,
                        String payload) {
        jdbc.update("""
                INSERT INTO notificacion_retiro
                    (clave_evento, codigo_autorizacion, cuenta_id, monto, canal, payload)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (clave_evento) DO NOTHING
                """, claveEvento, codigoAutorizacion, cuentaId, monto, canal, payload);
    }
}
