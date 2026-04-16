package com.example.batch.config;

import com.example.batch.entity.Customer;
import com.example.batch.partitioner.IdRangePartitioner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.MyBatisPagingItemReader;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.mybatis.spring.batch.builder.MyBatisPagingItemReaderBuilder;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

@Configuration
public class PartitionJobConfig {

    // 1. Job 설정
    @Bean
    public Job customerJob(JobRepository jobRepository, Step managerStep) {
        return new JobBuilder("customerJob", jobRepository)
                .start(managerStep)
                .build();
    }

    // 2. Manager Step 설정 (중복 제거됨)
    @Bean
    public Step managerStep(JobRepository jobRepository, Step workerStep, Partitioner partitioner) {
        return new StepBuilder("managerStep", jobRepository)
                .partitioner("workerStep", partitioner)
                .step(workerStep)
                .gridSize(5) // 5개의 병렬 파티션
                .taskExecutor(new VirtualThreadTaskExecutor()) // Java 21 가상 스레드
                .build();
    }

    // 3. Worker Step 설정 (실제 Chunk 단위 처리)
    @Bean
    public Step workerStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           MyBatisPagingItemReader<Customer> reader,
                           MyBatisBatchItemWriter<Customer> writer) {
        return new StepBuilder("workerStep", jobRepository)
                .<Customer, Customer>chunk(100, transactionManager)
                .reader(reader)
                .writer(writer)
                .build();
    }

    // 4. ItemReader (파티션 범위를 주입받음)
    @Bean
    @StepScope
    public MyBatisPagingItemReader<Customer> reader(
            SqlSessionFactory sqlSessionFactory,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {

        // minId, maxId가 null일 경우를 대비한 방어 로직 (데이터 없을 때 대응)
        if (minId == null || maxId == null) {
            return new MyBatisPagingItemReaderBuilder<Customer>()
                    .sqlSessionFactory(sqlSessionFactory)
                    .queryId("com.example.batch.mapper.CustomerMapper.findByIdRange")
                    .parameterValues(Map.of("minId", 0L, "maxId", 0L))
                    .pageSize(100)
                    .build();
        }

        return new MyBatisPagingItemReaderBuilder<Customer>()
                .sqlSessionFactory(sqlSessionFactory)
                .queryId("com.example.batch.mapper.CustomerMapper.findByIdRange")
                .parameterValues(Map.of("minId", minId, "maxId", maxId))
                .pageSize(100)
                .build();
    }

    // 5. ItemWriter (MyBatis 배치 업데이트)
    @Bean
    public MyBatisBatchItemWriter<Customer> writer(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisBatchItemWriterBuilder<Customer>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId("com.example.batch.mapper.CustomerMapper.updateStatus")
                .itemToParameterConverter(item -> Map.of("id", item.id()))
                .build();
    }

    // 6. Partitioner 설정
    @Bean
    public Partitioner partitioner(JdbcTemplate jdbcTemplate) {
        return new IdRangePartitioner(jdbcTemplate);
    }
}
