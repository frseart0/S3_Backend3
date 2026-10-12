package com.bancoxyz.batch.common.listener;

import com.bancoxyz.batch.config.RestartProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Si el Job termina en FAILED, lo reejecuta con {@link JobOperator#restart}
 * hasta {@code batch.restart.max-intentos} veces. El retry de cada step sigue
 * cubriendo los errores transitorios de base de datos; esto cubre el fallo
 * critico que aborta la ejecucion completa.
 */
@Component
public class ReejecucionJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ReejecucionJobListener.class);

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final JobExplorer jobExplorer;
    private final RestartProperties propiedades;
    private final ResultadoBatch resultado;

    public ReejecucionJobListener(JobOperator jobOperator,
                                  JobRepository jobRepository,
                                  JobExplorer jobExplorer,
                                  RestartProperties propiedades,
                                  ResultadoBatch resultado) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.jobExplorer = jobExplorer;
        this.propiedades = propiedades;
        this.resultado = resultado;
    }

    @Override
    public void afterJob(JobExecution ejecucion) {
        String nombre = ejecucion.getJobInstance().getJobName();
        if (ejecucion.getStatus() != BatchStatus.FAILED) {
            resultado.exito();
            return;
        }

        // El launcher persiste el estado al volver de execute(). Restart lee
        // la base, asi que el FAILED tiene que quedar escrito antes.
        jobRepository.update(ejecucion);

        int fallos = contarFallos(ejecucion);
        if (fallos > propiedades.maxIntentos()) {
            log.error("Job [{}] agoto {} reejecuciones automaticas y queda en FAILED",
                    nombre, propiedades.maxIntentos());
            resultado.fracaso();
            return;
        }

        log.warn("Job [{}] termino en FAILED (intento fallido {}). Reejecutando.", nombre, fallos);
        try {
            Long nuevaId = jobOperator.restart(ejecucion.getId());
            JobExecution nueva = jobExplorer.getJobExecution(nuevaId);
            if (nueva != null && nueva.getStatus() == BatchStatus.COMPLETED) {
                resultado.exito();
                log.info("Job [{}] se recupero en la ejecucion {}", nombre, nuevaId);
            } else if (nueva == null || nueva.getStatus() == BatchStatus.FAILED) {
                resultado.fracaso();
            }
        } catch (Exception ex) {
            log.error("No se pudo reejecutar el Job [{}]: {}", nombre, ex.getMessage());
            resultado.fracaso();
        }
    }

    int contarFallos(JobExecution actual) {
        List<JobExecution> todas = jobExplorer.getJobExecutions(actual.getJobInstance());
        boolean incluyeActual = false;
        int fallos = 0;
        for (JobExecution ejecucion : todas) {
            if (ejecucion.getId() != null && ejecucion.getId().equals(actual.getId())) {
                incluyeActual = true;
            }
            if (ejecucion.getStatus() == BatchStatus.FAILED
                    || (ejecucion.getId() != null && ejecucion.getId().equals(actual.getId()))) {
                fallos++;
            }
        }
        if (!incluyeActual) {
            fallos++;
        }
        return fallos;
    }
}
