package com.bancoxyz.batch.common.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registra en la tabla {@code batch_error_log} cada item que fue salteado
 * (skip) durante la lectura, procesamiento o escritura, para tener
 * trazabilidad/auditoria de los errores de datos manejados por el
 * {@link com.bancoxyz.batch.common.policy.BankSkipPolicy}.
 */
public class BankSkipListener implements SkipListener<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(BankSkipListener.class);

    private final JdbcTemplate jdbcTemplate;
    private final String jobName;
    private final String stepName;

    public BankSkipListener(JdbcTemplate jdbcTemplate, String jobName, String stepName) {
        this.jdbcTemplate = jdbcTemplate;
        this.jobName = jobName;
        this.stepName = stepName;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        persist("READ", null, t);
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        persist("PROCESS", item, t);
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        persist("WRITE", item, t);
    }

    private void persist(String etapa, Object item, Throwable t) {
        String detalleItem = item == null ? null : truncate(item.toString(), 1000);
        String mensajeError = truncate(t.getClass().getSimpleName() + ": " + t.getMessage(), 1000);

        log.warn("[{}][{}] Item salteado en etapa {}: {}", jobName, stepName, etapa, mensajeError);

        jdbcTemplate.update(
                "INSERT INTO batch_error_log (job_name, step_name, etapa, detalle_item, mensaje_error) " +
                        "VALUES (?, ?, ?, ?, ?)",
                jobName, stepName, etapa, detalleItem, mensajeError);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
