package com.bancoxyz.batch.common.listener;

import com.bancoxyz.batch.config.RestartProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReejecucionJobListenerTest {

    @Mock
    private JobOperator jobOperator;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobExplorer jobExplorer;

    private ResultadoBatch resultado;
    private ReejecucionJobListener listener;

    @BeforeEach
    void crear() {
        resultado = new ResultadoBatch();
        listener = new ReejecucionJobListener(
                jobOperator, jobRepository, jobExplorer, new RestartProperties(2), resultado);
    }

    @Test
    void unJobCompletadoNoSeReejecuta() throws Exception {
        listener.afterJob(ejecucion(1L, BatchStatus.COMPLETED));

        verify(jobOperator, never()).restart(1L);
        assertThat(resultado.codigoSalida()).isZero();
    }

    @Test
    void elPrimerFalloDisparaUnaReejecucion() throws Exception {
        JobExecution fallida = ejecucion(7L, BatchStatus.FAILED);
        when(jobExplorer.getJobExecutions(fallida.getJobInstance())).thenReturn(List.of(fallida));
        when(jobOperator.restart(7L)).thenReturn(8L);
        when(jobExplorer.getJobExecution(8L)).thenReturn(ejecucion(8L, BatchStatus.COMPLETED));

        listener.afterJob(fallida);

        verify(jobRepository).update(fallida);
        verify(jobOperator).restart(7L);
        assertThat(resultado.codigoSalida()).isZero();
    }

    @Test
    void noSuperaElTopeDeReejecuciones() throws Exception {
        JobExecution primera = ejecucion(1L, BatchStatus.FAILED);
        JobExecution segunda = ejecucion(2L, BatchStatus.FAILED);
        JobExecution tercera = ejecucion(3L, BatchStatus.FAILED);
        when(jobExplorer.getJobExecutions(tercera.getJobInstance()))
                .thenReturn(List.of(primera, segunda, tercera));

        listener.afterJob(tercera);

        verify(jobOperator, never()).restart(3L);
        assertThat(resultado.codigoSalida()).isEqualTo(BatchStatus.FAILED.ordinal());
    }

    private static JobExecution ejecucion(long id, BatchStatus estado) {
        var instancia = new JobInstance(1L, "reporteTransaccionesDiariasJob");
        var ejecucion = new JobExecution(instancia, id, new JobParameters());
        ejecucion.setStatus(estado);
        return ejecucion;
    }
}
