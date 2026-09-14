package com.bancoxyz.core.cuentas;

import com.bancoxyz.domain.TipoCuenta;
import com.bancoxyz.domain.contract.DetalleInteres;
import com.bancoxyz.domain.contract.EstadoCuentaAnual;
import com.bancoxyz.domain.contract.Movimiento;
import com.bancoxyz.domain.contract.PerfilCuenta;
import com.bancoxyz.domain.contract.SaldoCuenta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lecturas sobre las tablas que dejaron los Jobs batch.
 *
 * <p>El dataset legacy trae varias filas por cuenta en {@code cuentas_interes}
 * (mismo id con distintos nombres y saldos), asi que el perfil y el detalle de
 * intereses se resuelven con {@code DISTINCT ON}: se toma la ultima fila
 * confiable de cada cuenta como version vigente.
 */
@Repository
public class CuentaRepository {

    private static final String FILA_VIGENTE = """
            SELECT DISTINCT ON (cuenta_id)
                   cuenta_id, nombre, tipo, saldo_inicial, tasa_aplicada,
                   interes_calculado, saldo_final, edad_utilizada, estado, observacion
            FROM cuentas_interes
            WHERE cuenta_id = ?
              AND estado IN ('VALIDA', 'VALIDA_CORREGIDA')
            ORDER BY cuenta_id, procesado_en DESC, id DESC
            """;

    private final JdbcTemplate jdbc;

    public CuentaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PerfilCuenta> buscarPerfil(long cuentaId) {
        return jdbc.query(FILA_VIGENTE, mapeadorPerfil(), cuentaId).stream().findFirst();
    }

    public Optional<DetalleInteres> buscarIntereses(long cuentaId) {
        return jdbc.query(FILA_VIGENTE, mapeadorIntereses(), cuentaId).stream().findFirst();
    }

    public Optional<SaldoCuenta> buscarSaldo(long cuentaId) {
        String sql = "SELECT cuenta_id, saldo, actualizado_en FROM cuenta_saldo WHERE cuenta_id = ?";
        return jdbc.query(sql, (rs, fila) -> new SaldoCuenta(
                rs.getLong("cuenta_id"),
                rs.getBigDecimal("saldo"),
                rs.getTimestamp("actualizado_en").toInstant()), cuentaId).stream().findFirst();
    }

    public List<EstadoCuentaAnual> estadosAnuales(long cuentaId) {
        String sql = """
                SELECT cuenta_id, anio, total_depositos, total_salidas, saldo_neto_anual,
                       cantidad_movimientos, cantidad_anomalias
                FROM estado_cuenta_anual
                WHERE cuenta_id = ?
                ORDER BY anio DESC
                """;
        return jdbc.query(sql, (rs, fila) -> new EstadoCuentaAnual(
                rs.getLong("cuenta_id"),
                rs.getInt("anio"),
                rs.getBigDecimal("total_depositos"),
                rs.getBigDecimal("total_salidas"),
                rs.getBigDecimal("saldo_neto_anual"),
                rs.getInt("cantidad_movimientos"),
                rs.getInt("cantidad_anomalias")), cuentaId);
    }

    public long contarMovimientos(long cuentaId, LocalDate desde, LocalDate hasta) {
        var argumentos = new ArrayList<Object>();
        argumentos.add(cuentaId);
        String sql = "SELECT COUNT(*) FROM movimientos_anuales WHERE cuenta_id = ?"
                + filtroFechas(desde, hasta, argumentos);
        Long total = jdbc.queryForObject(sql, Long.class, argumentos.toArray());
        return total == null ? 0L : total;
    }

    public List<Movimiento> movimientos(long cuentaId,
                                        LocalDate desde,
                                        LocalDate hasta,
                                        int pagina,
                                        int tamano) {
        var argumentos = new ArrayList<Object>();
        argumentos.add(cuentaId);
        String sql = """
                SELECT id, cuenta_id, fecha, tipo_transaccion, monto, descripcion,
                       estado, motivo, procesado_por_hilo
                FROM movimientos_anuales
                WHERE cuenta_id = ?
                """
                + filtroFechas(desde, hasta, argumentos)
                + " ORDER BY fecha DESC NULLS LAST, id DESC LIMIT ? OFFSET ?";
        argumentos.add(tamano);
        argumentos.add((long) pagina * tamano);

        return jdbc.query(sql, (rs, fila) -> new Movimiento(
                rs.getLong("id"),
                rs.getLong("cuenta_id"),
                fecha(rs, "fecha"),
                rs.getString("tipo_transaccion"),
                rs.getBigDecimal("monto"),
                rs.getString("descripcion"),
                rs.getString("estado"),
                rs.getString("motivo"),
                rs.getString("procesado_por_hilo")), argumentos.toArray());
    }

    /** Una cuenta existe si el batch dejo alguna huella de ella. */
    public boolean existe(long cuentaId) {
        String sql = """
                SELECT EXISTS (SELECT 1 FROM cuenta_saldo WHERE cuenta_id = ?)
                    OR EXISTS (SELECT 1 FROM cuentas_interes WHERE cuenta_id = ?)
                    OR EXISTS (SELECT 1 FROM movimientos_anuales WHERE cuenta_id = ?)
                """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, cuentaId, cuentaId, cuentaId));
    }

    private String filtroFechas(LocalDate desde, LocalDate hasta, List<Object> argumentos) {
        var filtro = new StringBuilder();
        if (desde != null) {
            filtro.append(" AND fecha >= ?");
            argumentos.add(Date.valueOf(desde));
        }
        if (hasta != null) {
            filtro.append(" AND fecha <= ?");
            argumentos.add(Date.valueOf(hasta));
        }
        return filtro.toString();
    }

    private RowMapper<PerfilCuenta> mapeadorPerfil() {
        return (rs, fila) -> new PerfilCuenta(
                rs.getLong("cuenta_id"),
                rs.getString("nombre"),
                TipoCuenta.desde(rs.getString("tipo")),
                rs.getString("estado"),
                rs.getString("observacion"));
    }

    private RowMapper<DetalleInteres> mapeadorIntereses() {
        return (rs, fila) -> new DetalleInteres(
                rs.getLong("cuenta_id"),
                TipoCuenta.desde(rs.getString("tipo")),
                rs.getBigDecimal("saldo_inicial"),
                rs.getBigDecimal("tasa_aplicada"),
                rs.getBigDecimal("interes_calculado"),
                rs.getBigDecimal("saldo_final"),
                (Integer) rs.getObject("edad_utilizada"),
                rs.getString("estado"),
                rs.getString("observacion"));
    }

    private LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        Date valor = rs.getDate(columna);
        return valor == null ? null : valor.toLocalDate();
    }
}
