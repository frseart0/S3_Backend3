package com.bancoxyz.batch.transacciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Segundo Step del Job de Reporte de Transacciones Diarias: compila el
 * resumen por fecha (totales, anomalias, duplicadas y montos por tipo) a
 * partir de lo que el Step anterior ya persistio en {@code transacciones}.
 */
public class ResumenDiarioTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(ResumenDiarioTasklet.class);

    private static final String SQL_RESUMEN = """
            INSERT INTO resumen_transacciones_diarias
                (fecha, total_transacciones, total_validas, total_anomalias, total_duplicadas,
                 monto_total_credito, monto_total_debito)
            SELECT fecha,
                   COUNT(*)                                                              AS total_transacciones,
                   COUNT(*) FILTER (WHERE estado = 'VALIDA')                             AS total_validas,
                   COUNT(*) FILTER (WHERE estado = 'ANOMALIA')                           AS total_anomalias,
                   COUNT(*) FILTER (WHERE estado = 'DUPLICADA')                          AS total_duplicadas,
                   COALESCE(SUM(monto) FILTER (WHERE estado = 'VALIDA' AND tipo = 'credito'), 0) AS monto_credito,
                   COALESCE(SUM(monto) FILTER (WHERE estado = 'VALIDA' AND tipo = 'debito'), 0)  AS monto_debito
            FROM transacciones
            WHERE fecha IS NOT NULL
            GROUP BY fecha
            ON CONFLICT (fecha) DO UPDATE SET
                total_transacciones = EXCLUDED.total_transacciones,
                total_validas = EXCLUDED.total_validas,
                total_anomalias = EXCLUDED.total_anomalias,
                total_duplicadas = EXCLUDED.total_duplicadas,
                monto_total_credito = EXCLUDED.monto_total_credito,
                monto_total_debito = EXCLUDED.monto_total_debito,
                generado_en = now()
            """;

    private final JdbcTemplate jdbcTemplate;

    public ResumenDiarioTasklet(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int filasAfectadas = jdbcTemplate.update(SQL_RESUMEN);
        log.info("Resumen diario de transacciones generado/actualizado para {} fecha(s).", filasAfectadas);
        return RepeatStatus.FINISHED;
    }
}
