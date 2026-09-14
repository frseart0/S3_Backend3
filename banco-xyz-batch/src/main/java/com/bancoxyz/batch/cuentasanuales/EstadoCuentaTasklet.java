package com.bancoxyz.batch.cuentasanuales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Segundo Step del Job de Estados de Cuenta Anuales: compila, por cuenta y
 * anio, los totales de depositos/salidas y el saldo neto para el informe de
 * auditoria, a partir de lo que el Step anterior persistio en
 * {@code movimientos_anuales}.
 */
public class EstadoCuentaTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(EstadoCuentaTasklet.class);

    private static final String SQL_ESTADO_CUENTA = """
            INSERT INTO estado_cuenta_anual
                (cuenta_id, anio, total_depositos, total_salidas, saldo_neto_anual,
                 cantidad_movimientos, cantidad_anomalias)
            SELECT cuenta_id,
                   EXTRACT(YEAR FROM fecha)::int                                                    AS anio,
                   COALESCE(SUM(monto) FILTER (WHERE tipo_transaccion = 'deposito' AND estado <> 'ANOMALIA'), 0) AS total_depositos,
                   COALESCE(SUM(ABS(monto)) FILTER (WHERE tipo_transaccion IN ('retiro', 'compra')), 0)          AS total_salidas,
                   COALESCE(SUM(monto), 0)                                                          AS saldo_neto_anual,
                   COUNT(*)                                                                         AS cantidad_movimientos,
                   COUNT(*) FILTER (WHERE estado = 'ANOMALIA')                                      AS cantidad_anomalias
            FROM movimientos_anuales
            WHERE cuenta_id IS NOT NULL AND fecha IS NOT NULL
            GROUP BY cuenta_id, EXTRACT(YEAR FROM fecha)
            ON CONFLICT (cuenta_id, anio) DO UPDATE SET
                total_depositos = EXCLUDED.total_depositos,
                total_salidas = EXCLUDED.total_salidas,
                saldo_neto_anual = EXCLUDED.saldo_neto_anual,
                cantidad_movimientos = EXCLUDED.cantidad_movimientos,
                cantidad_anomalias = EXCLUDED.cantidad_anomalias,
                generado_en = now()
            """;

    private final JdbcTemplate jdbcTemplate;

    public EstadoCuentaTasklet(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int filasAfectadas = jdbcTemplate.update(SQL_ESTADO_CUENTA);
        log.info("Estado de cuenta anual generado/actualizado para {} combinacion(es) cuenta/anio.", filasAfectadas);
        return RepeatStatus.FINISHED;
    }
}
