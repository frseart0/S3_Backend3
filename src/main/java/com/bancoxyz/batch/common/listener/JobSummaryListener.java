package com.bancoxyz.batch.common.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;

import java.time.Duration;

/**
 * Loggea, al finalizar el Job, un resumen legible por humano con lo leido,
 * escrito, filtrado y salteado en cada Step -- sirve como evidencia de
 * ejecucion en consola para la entrega del proyecto.
 */
public class JobSummaryListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobSummaryListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("==================================================================");
        log.info(">>> Iniciando Job [{}] - parametros: {}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getJobParameters());
        log.info("==================================================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("==================================================================");
        log.info(">>> Job [{}] finalizado con estado: {}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getStatus());

        for (StepExecution step : jobExecution.getStepExecutions()) {
            Duration duration = (step.getStartTime() != null && step.getEndTime() != null)
                    ? Duration.between(step.getStartTime(), step.getEndTime())
                    : Duration.ZERO;

            long totalSkips = step.getReadSkipCount() + step.getProcessSkipCount() + step.getWriteSkipCount();

            log.info("  - Step [{}] estado={} leidos={} escritos={} filtrados={} " +
                            "saltados(read/process/write)={}/{}/{} duracion={}ms",
                    step.getStepName(), step.getStatus(), step.getReadCount(), step.getWriteCount(),
                    step.getFilterCount(), step.getReadSkipCount(), step.getProcessSkipCount(),
                    step.getWriteSkipCount(), duration.toMillis());

            if (totalSkips > 0) {
                log.warn("    Total de items saltados en [{}]: {} (ver detalle en batch_error_log)",
                        step.getStepName(), totalSkips);
            }
        }
        log.info("==================================================================");
    }
}
