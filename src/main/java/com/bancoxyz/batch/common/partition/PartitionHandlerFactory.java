package com.bancoxyz.batch.common.partition;

import com.bancoxyz.batch.config.PartitionProperties;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Construye el {@link TaskExecutorPartitionHandler} (master) que reparte las
 * particiones generadas por un {@link LineRangePartitioner} entre un pool de
 * hilos dedicado, ejecutando el worker step en paralelo.
 */
public final class PartitionHandlerFactory {

    private PartitionHandlerFactory() {
    }

    public static TaskExecutorPartitionHandler build(Step workerStep, PartitionProperties properties, String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();

        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setTaskExecutor(executor);
        handler.setStep(workerStep);
        handler.setGridSize(properties.gridSize());
        return handler;
    }
}
