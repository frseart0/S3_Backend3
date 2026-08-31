package com.bancoxyz.batch.transacciones;

import com.bancoxyz.batch.common.listener.BankSkipListener;
import com.bancoxyz.batch.common.listener.JobSummaryListener;
import com.bancoxyz.batch.common.partition.LineRangePartitioner;
import com.bancoxyz.batch.common.partition.PartitionHandlerFactory;
import com.bancoxyz.batch.common.policy.BankSkipPolicy;
import com.bancoxyz.batch.config.PartitionProperties;
import com.bancoxyz.batch.config.RetryProperties;
import com.bancoxyz.batch.config.SkipProperties;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Job "Reporte de Transacciones Diarias".
 *
 * <pre>
 * transaccionesPartitionStep (master, Partitioning gridSize particiones)
 *   -> transaccionesWorkerStep (reader/processor/writer, faultTolerant, N particiones en paralelo)
 * resumenDiarioStep (tasklet, agrega el resumen por fecha)
 * </pre>
 */
@Configuration
public class TransaccionesJobConfig {

    public static final String JOB_NAME = "reporteTransaccionesDiariasJob";
    private static final String WORKER_STEP_NAME = "transaccionesWorkerStep";
    private static final int CHUNK_SIZE = 5;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PartitionProperties partitionProperties;
    private final SkipProperties skipProperties;
    private final RetryProperties retryProperties;

    public TransaccionesJobConfig(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   DataSource dataSource,
                                   JdbcTemplate jdbcTemplate,
                                   PartitionProperties partitionProperties,
                                   SkipProperties skipProperties,
                                   RetryProperties retryProperties) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.partitionProperties = partitionProperties;
        this.skipProperties = skipProperties;
        this.retryProperties = retryProperties;
    }

    @Bean
    public Job reporteTransaccionesDiariasJob(Step transaccionesPartitionStep, Step resumenDiarioStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(new JobSummaryListener())
                .start(transaccionesPartitionStep)
                .next(resumenDiarioStep)
                .build();
    }

    @Bean
    public Step transaccionesPartitionStep(Step transaccionesWorkerStep,
                                            @Value("${batch.data.transacciones-path}") Resource transaccionesResource) {
        TaskExecutorPartitionHandler partitionHandler = PartitionHandlerFactory.build(
                transaccionesWorkerStep, partitionProperties, "transacciones-partition-");

        return new StepBuilder("transaccionesPartitionStep", jobRepository)
                .partitioner(WORKER_STEP_NAME, new LineRangePartitioner(transaccionesResource))
                .partitionHandler(partitionHandler)
                .build();
    }

    @Bean
    public Step transaccionesWorkerStep(FlatFileItemReader<TransaccionRaw> transaccionItemReader,
                                         JdbcBatchItemWriter<Transaccion> transaccionItemWriter,
                                         TransaccionItemProcessor transaccionItemProcessor) {
        return new StepBuilder(WORKER_STEP_NAME, jobRepository)
                .<TransaccionRaw, Transaccion>chunk(CHUNK_SIZE, transactionManager)
                .reader(transaccionItemReader)
                .processor(transaccionItemProcessor)
                .writer(transaccionItemWriter)
                .faultTolerant()
                .skipPolicy(new BankSkipPolicy(skipProperties.limit()))
                .retryPolicy(buildRetryPolicy())
                .backOffPolicy(buildBackOffPolicy())
                .listener(new BankSkipListener(jdbcTemplate, JOB_NAME, WORKER_STEP_NAME))
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<TransaccionRaw> transaccionItemReader(
            @Value("#{stepExecutionContext['startLine']}") Integer startLine,
            @Value("#{stepExecutionContext['endLine']}") Integer endLine,
            @Value("${batch.data.transacciones-path}") Resource transaccionesResource) {

        FlatFileItemReader<TransaccionRaw> reader = new FlatFileItemReaderBuilder<TransaccionRaw>()
                .name("transaccionItemReader")
                .resource(transaccionesResource)
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("id", "fecha", "monto", "tipo")
                .fieldSetMapper(buildFieldSetMapper())
                .build();

        reader.setCurrentItemCount(startLine - 1);
        reader.setMaxItemCount(endLine);
        return reader;
    }

    @Bean
    public TransaccionItemProcessor transaccionItemProcessor() {
        return new TransaccionItemProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<Transaccion> transaccionItemWriter() {
        return new JdbcBatchItemWriterBuilder<Transaccion>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO transacciones
                            (transaccion_id_origen, fecha, monto, tipo, estado, motivo, procesado_por_hilo)
                        VALUES (:idOrigen, :fecha, :monto, :tipo, :estado, :motivo, :hilo)
                        """)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    @Bean
    public Step resumenDiarioStep() {
        return new StepBuilder("resumenDiarioStep", jobRepository)
                .tasklet(new ResumenDiarioTasklet(jdbcTemplate), transactionManager)
                .build();
    }

    private BeanWrapperFieldSetMapper<TransaccionRaw> buildFieldSetMapper() {
        BeanWrapperFieldSetMapper<TransaccionRaw> mapper = new BeanWrapperFieldSetMapper<>();
        mapper.setTargetType(TransaccionRaw.class);
        return mapper;
    }

    private SimpleRetryPolicy buildRetryPolicy() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(TransientDataAccessException.class, true);
        return new SimpleRetryPolicy(retryProperties.limit(), retryableExceptions);
    }

    private ExponentialBackOffPolicy buildBackOffPolicy() {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryProperties.initialIntervalMs());
        backOffPolicy.setMultiplier(retryProperties.multiplier());
        backOffPolicy.setMaxInterval(retryProperties.maxIntervalMs());
        return backOffPolicy;
    }
}
