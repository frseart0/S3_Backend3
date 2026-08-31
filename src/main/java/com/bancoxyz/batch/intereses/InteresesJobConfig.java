package com.bancoxyz.batch.intereses;

import com.bancoxyz.batch.common.listener.BankSkipListener;
import com.bancoxyz.batch.common.listener.JobSummaryListener;
import com.bancoxyz.batch.common.partition.LineRangePartitioner;
import com.bancoxyz.batch.common.partition.PartitionHandlerFactory;
import com.bancoxyz.batch.common.policy.BankSkipPolicy;
import com.bancoxyz.batch.config.InteresProperties;
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
 * Job "Calculo de Intereses Mensuales".
 *
 * <pre>
 * interesesPartitionStep (master, Partitioning gridSize particiones)
 *   -> interesesWorkerStep (reader/processor/writer, faultTolerant, N particiones en paralelo)
 * </pre>
 */
@Configuration
public class InteresesJobConfig {

    public static final String JOB_NAME = "calculoInteresesMensualesJob";
    private static final String WORKER_STEP_NAME = "interesesWorkerStep";
    private static final int CHUNK_SIZE = 5;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PartitionProperties partitionProperties;
    private final SkipProperties skipProperties;
    private final RetryProperties retryProperties;
    private final InteresProperties interesProperties;

    public InteresesJobConfig(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               DataSource dataSource,
                               JdbcTemplate jdbcTemplate,
                               PartitionProperties partitionProperties,
                               SkipProperties skipProperties,
                               RetryProperties retryProperties,
                               InteresProperties interesProperties) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.partitionProperties = partitionProperties;
        this.skipProperties = skipProperties;
        this.retryProperties = retryProperties;
        this.interesProperties = interesProperties;
    }

    @Bean
    public Job calculoInteresesMensualesJob(Step interesesPartitionStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(new JobSummaryListener())
                .start(interesesPartitionStep)
                .build();
    }

    @Bean
    public Step interesesPartitionStep(Step interesesWorkerStep,
                                        @Value("${batch.data.intereses-path}") Resource interesesResource) {
        TaskExecutorPartitionHandler partitionHandler = PartitionHandlerFactory.build(
                interesesWorkerStep, partitionProperties, "intereses-partition-");

        return new StepBuilder("interesesPartitionStep", jobRepository)
                .partitioner(WORKER_STEP_NAME, new LineRangePartitioner(interesesResource))
                .partitionHandler(partitionHandler)
                .build();
    }

    @Bean
    public Step interesesWorkerStep(FlatFileItemReader<InteresRaw> interesItemReader,
                                     JdbcBatchItemWriter<CuentaInteres> interesItemWriter,
                                     InteresItemProcessor interesItemProcessor) {
        return new StepBuilder(WORKER_STEP_NAME, jobRepository)
                .<InteresRaw, CuentaInteres>chunk(CHUNK_SIZE, transactionManager)
                .reader(interesItemReader)
                .processor(interesItemProcessor)
                .writer(interesItemWriter)
                .faultTolerant()
                .skipPolicy(new BankSkipPolicy(skipProperties.limit()))
                .retryPolicy(buildRetryPolicy())
                .backOffPolicy(buildBackOffPolicy())
                .listener(new BankSkipListener(jdbcTemplate, JOB_NAME, WORKER_STEP_NAME))
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<InteresRaw> interesItemReader(
            @Value("#{stepExecutionContext['startLine']}") Integer startLine,
            @Value("#{stepExecutionContext['endLine']}") Integer endLine,
            @Value("${batch.data.intereses-path}") Resource interesesResource) {

        FlatFileItemReader<InteresRaw> reader = new FlatFileItemReaderBuilder<InteresRaw>()
                .name("interesItemReader")
                .resource(interesesResource)
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .fieldSetMapper(buildFieldSetMapper())
                .build();

        reader.setCurrentItemCount(startLine - 1);
        reader.setMaxItemCount(endLine);
        return reader;
    }

    @Bean
    public InteresItemProcessor interesItemProcessor() {
        return new InteresItemProcessor(interesProperties);
    }

    @Bean
    public JdbcBatchItemWriter<CuentaInteres> interesItemWriter() {
        return new JdbcBatchItemWriterBuilder<CuentaInteres>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO cuentas_interes
                            (cuenta_id, nombre, tipo, saldo_inicial, edad_original, edad_utilizada,
                             tasa_aplicada, interes_calculado, saldo_final, estado, observacion, procesado_por_hilo)
                        VALUES (:cuentaId, :nombre, :tipo, :saldoInicial, :edadOriginal, :edadUtilizada,
                                :tasaAplicada, :interesCalculado, :saldoFinal, :estado, :observacion, :hilo)
                        """)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    private BeanWrapperFieldSetMapper<InteresRaw> buildFieldSetMapper() {
        BeanWrapperFieldSetMapper<InteresRaw> mapper = new BeanWrapperFieldSetMapper<>();
        mapper.setTargetType(InteresRaw.class);
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
