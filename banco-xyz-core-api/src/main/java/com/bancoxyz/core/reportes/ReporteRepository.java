package com.bancoxyz.core.reportes;

import com.bancoxyz.domain.contract.ResumenDiario;
import com.bancoxyz.domain.contract.TransaccionDiaria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reportes del Job 1: transacciones diarias y su resumen agregado. */
@Repository
public class ReporteRepository {

    private final JdbcTemplate jdbc;

    public ReporteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TransaccionDiaria> transacciones(LocalDate fecha, String estado, int limite) {
        var argumentos = new ArrayList<Object>();
        var sql = new StringBuilder("""
                SELECT id, transaccion_id_origen, fecha, monto, tipo, estado, motivo
                FROM transacciones
                WHERE 1 = 1
                """);
        if (fecha != null) {
            sql.append(" AND fecha = ?");
            argumentos.add(Date.valueOf(fecha));
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND estado = ?");
            argumentos.add(estado.trim().toUpperCase());
        }
        sql.append(" ORDER BY fecha DESC NULLS LAST, id DESC LIMIT ?");
        argumentos.add(limite);

        return jdbc.query(sql.toString(), (rs, fila) -> new TransaccionDiaria(
                rs.getLong("id"),
                (Long) rs.getObject("transaccion_id_origen"),
                rs.getDate("fecha") == null ? null : rs.getDate("fecha").toLocalDate(),
                rs.getBigDecimal("monto"),
                rs.getString("tipo"),
                rs.getString("estado"),
                rs.getString("motivo")), argumentos.toArray());
    }

    public List<ResumenDiario> resumenDiario(LocalDate fecha) {
        var argumentos = new ArrayList<Object>();
        var sql = new StringBuilder("""
                SELECT fecha, total_transacciones, total_validas, total_anomalias,
                       total_duplicadas, monto_total_credito, monto_total_debito
                FROM resumen_transacciones_diarias
                """);
        if (fecha != null) {
            sql.append(" WHERE fecha = ?");
            argumentos.add(Date.valueOf(fecha));
        }
        sql.append(" ORDER BY fecha DESC");

        return jdbc.query(sql.toString(), (rs, fila) -> new ResumenDiario(
                rs.getDate("fecha").toLocalDate(),
                rs.getInt("total_transacciones"),
                rs.getInt("total_validas"),
                rs.getInt("total_anomalias"),
                rs.getInt("total_duplicadas"),
                rs.getBigDecimal("monto_total_credito"),
                rs.getBigDecimal("monto_total_debito")), argumentos.toArray());
    }
}
