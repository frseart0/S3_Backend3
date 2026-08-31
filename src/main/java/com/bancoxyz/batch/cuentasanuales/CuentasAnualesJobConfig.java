package com.bancoxyz.batch.cuentasanuales;

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
 * Job "Generacion de Estados de Cuenta Anuales".
 *
 * <pre>
 * cuentasAnualesPartitionStep (master, Partitioning gridSize particiones)
 *   -> cuentasAnualesWorkerStep (reader/processor/writer, faultTolerant, N particiones en paralelo)
 * estadoCuentaStep (tasklet, agrega totales anuales por cuenta para auditoria)
 * </pre>
 */
@Configuration
public class CuentasAnualesJobConfig {

    public static final String JOB_NAME = "estadosCuentaAnualesJob";
    private static final String WORKER_STEP_NAME = "cuentasAnualesWorkerStep";
    private static final int CHUNK_SIZE = 5;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PartitionProperties partitionProperties;
    private final SkipProperties skipProperties;
    private final RetryProperties retryProperties;

    public CuentasAnualesJobConfig(JobRepository jobRepository,
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
    public Job estadosCuentaAnualesJob(Step cuentasAnualesPartitionStep, Step estadoCuentaStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(new JobSummaryListener())
                .start(cuentasAnualesPartitionStep)
                .next(estadoCuentaStep)
                .build();
    }

    @Bean
    public Step cuentasAnualesPartitionStep(Step cuentasAnualesWorkerStep,
                                             @Value("${batch.data.cuentas-anuales-path}") Resource cuentasAnualesResource) {
        TaskExecutorPartitionHandler partitionHandler = PartitionHandlerFactory.build(
                cuentasAnualesWorkerStep, partitionProperties, "cuentas-anuales-partition-");

        return new StepBuilder("cuentasAnualesPartitionStep", jobRepository)
                .partitioner(WORKER_STEP_NAME, new LineRangePartitioner(cuentasAnualesResource))
                .partitionHandler(partitionHandler)
                .build();
    }

    @Bean
    public Step cuentasAnualesWorkerStep(FlatFileItemReader<MovimientoRaw> movimientoItemReader,
                                          JdbcBatchItemWriter<MovimientoAnual> movimientoItemWriter,
                                          MovimientoItemProcessor movimientoItemProcessor) {
        return new StepBuilder(WORKER_STEP_NAME, jobRepository)
                .<MovimientoRaw, MovimientoAnual>chunk(CHUNK_SIZE, transactionManager)
                .reader(movimientoItemReader)
                .processor(movimientoItemProcessor)
                .writer(movimientoItemWriter)
                .faultTolerant()
                .skipPolicy(new BankSkipPolicy(skipProperties.limit()))
                .retryPolicy(buildRetryPolicy())
                .backOffPolicy(buildBackOffPolicy())
                .listener(new BankSkipListener(jdbcTemplate, JOB_NAME, WORKER_STEP_NAME))
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<MovimientoRaw> movimientoItemReader(
            @Value("#{stepExecutionContext['startLine']}") Integer startLine,
            @Value("#{stepExecutionContext['endLine']}") Integer endLine,
            @Value("${batch.data.cuentas-anuales-path}") Resource cuentasAnualesResource) {

        FlatFileItemReader<MovimientoRaw> reader = new FlatFileItemReaderBuilder<MovimientoRaw>()
                .name("movimientoItemReader")
                .resource(cuentasAnualesResource)
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("cuentaId", "fecha", "transaccion", "monto", "descripcion")
                .fieldSetMapper(buildFieldSetMapper())
                .build();

        reader.setCurrentItemCount(startLine - 1);
        reader.setMaxItemCount(endLine);
        return reader;
    }

    @Bean
    public MovimientoItemProcessor movimientoItemProcessor() {
        return new MovimientoItemProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<MovimientoAnual> movimientoItemWriter() {
        return new JdbcBatchItemWriterBuilder<MovimientoAnual>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO movimientos_anuales
                            (cuenta_id, fecha, tipo_transaccion, monto, descripcion, estado, motivo, procesado_por_hilo)
                        VALUES (:cuentaId, :fecha, :tipoTransaccion, :monto, :descripcion, :estado, :motivo, :hilo)
                        """)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    @Bean
    public Step estadoCuentaStep() {
        return new StepBuilder("estadoCuentaStep", jobRepository)
                .tasklet(new EstadoCuentaTasklet(jdbcTemplate), transactionManager)
                .build();
    }

    private BeanWrapperFieldSetMapper<MovimientoRaw> buildFieldSetMapper() {
        BeanWrapperFieldSetMapper<MovimientoRaw> mapper = new BeanWrapperFieldSetMapper<>();
        mapper.setTargetType(MovimientoRaw.class);
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
