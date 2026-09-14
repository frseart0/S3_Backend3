package com.bancoxyz.core.cuentas;

import com.bancoxyz.domain.contract.ComprobanteRetiro;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Lado de escritura de un retiro: el saldo que se bloquea, la bitacora de
 * operaciones que garantiza la idempotencia y el movimiento que queda en la
 * misma tabla que alimenta el batch, para que la cartola del cliente muestre
 * el retiro junto al resto de sus movimientos.
 */
@Repository
public class RetiroRepository {

    private final JdbcTemplate jdbc;

    public RetiroRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Bloquea la fila del saldo hasta el fin de la transaccion. Es lo que
     * impide que dos retiros simultaneos sobre la misma cuenta lean el mismo
     * saldo y la sobregiren.
     */
    public Optional<BigDecimal> bloquearSaldo(long cuentaId) {
        String sql = "SELECT saldo FROM cuenta_saldo WHERE cuenta_id = ? FOR UPDATE";
        return jdbc.query(sql, (rs, fila) -> rs.getBigDecimal("saldo"), cuentaId).stream().findFirst();
    }

    public void actualizarSaldo(long cuentaId, BigDecimal nuevoSaldo) {
        jdbc.update("""
                UPDATE cuenta_saldo
                SET saldo = ?, version = version + 1, actualizado_en = now()
                WHERE cuenta_id = ?
                """, nuevoSaldo, cuentaId);
    }

    public Optional<ComprobanteRetiro> buscarPorClaveIdempotencia(String clave) {
        String sql = """
                SELECT codigo_autorizacion, cuenta_id, monto, saldo_resultante, realizado_en
                FROM operaciones_cuenta
                WHERE clave_idempotencia = ?
                """;
        return jdbc.query(sql, (rs, fila) -> new ComprobanteRetiro(
                rs.getString("codigo_autorizacion"),
                rs.getLong("cuenta_id"),
                rs.getBigDecimal("monto"),
                rs.getBigDecimal("saldo_resultante"),
                rs.getTimestamp("realizado_en").toInstant(),
                true), clave).stream().findFirst();
    }

    public BigDecimal totalRetiradoHoy(long cuentaId) {
        String sql = """
                SELECT COALESCE(SUM(monto), 0)
                FROM operaciones_cuenta
                WHERE cuenta_id = ?
                  AND tipo = 'RETIRO'
                  AND realizado_en >= CURRENT_DATE
                """;
        BigDecimal total = jdbc.queryForObject(sql, BigDecimal.class, cuentaId);
        return total == null ? BigDecimal.ZERO : total;
    }

    public void registrarOperacion(String claveIdempotencia,
                                   String codigoAutorizacion,
                                   long cuentaId,
                                   String canal,
                                   BigDecimal monto,
                                   BigDecimal saldoResultante,
                                   String referenciaDispositivo) {
        jdbc.update("""
                INSERT INTO operaciones_cuenta
                    (clave_idempotencia, codigo_autorizacion, cuenta_id, tipo, canal,
                     monto, saldo_resultante, referencia_dispositivo)
                VALUES (?, ?, ?, 'RETIRO', ?, ?, ?, ?)
                """, claveIdempotencia, codigoAutorizacion, cuentaId, canal,
                monto, saldoResultante, referenciaDispositivo);
    }

    /**
     * Deja el retiro en {@code movimientos_anuales} con monto negativo, la
     * misma convencion de signo que usa el {@code MovimientoItemProcessor} del
     * batch para las salidas de dinero.
     */
    public void registrarMovimiento(long cuentaId, BigDecimal monto, String canal, String codigoAutorizacion) {
        jdbc.update("""
                INSERT INTO movimientos_anuales
                    (cuenta_id, fecha, tipo_transaccion, monto, descripcion,
                     estado, motivo, procesado_por_hilo)
                VALUES (?, ?, 'retiro', ?, ?, 'VALIDA', NULL, 'core-api')
                """, cuentaId, Date.valueOf(LocalDate.now()), monto.negate(),
                "Retiro canal " + canal + " (" + codigoAutorizacion + ")");
    }
}
