package com.example.batch.config;

import com.example.batch.entity.Customer;
import com.example.batch.partitioner.TimeRangePartitioner;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
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

    // 2. Manager Step 설정
    @Bean
    public Step managerStep(JobRepository jobRepository, Step workerStep, Partitioner partitioner, @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor) {
        return new StepBuilder("managerStep", jobRepository)
                .partitioner("workerStep", partitioner)
                .step(workerStep)
                .gridSize(20) // 가상 스레드 활용을 위해 파티션 수 확대 (기존 5 -> 20)
                .taskExecutor(taskExecutor) // 주입받은 가상 스레드 TaskExecutor 사용
                .build();
    }

    @Bean(name = "batchTaskExecutor")
    public TaskExecutor taskExecutor() {
        return new VirtualThreadTaskExecutor();
    }

    // 3. Worker Step 설정 (실제 Chunk 단위 처리)
    @Bean
    public Step workerStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           MyBatisPagingItemReader<Customer> reader,
                           MyBatisBatchItemWriter<Customer> writer) {
        return new StepBuilder("workerStep", jobRepository)
                .<Customer, Customer>chunk(1000, transactionManager) // Chunk Size 확대 (기존 100 -> 1000)
                .reader(reader)
                .writer(writer)
                .build();
    }

    // 4. ItemReader (시간 범위를 주입받음)
    @Bean
    @StepScope
    public MyBatisPagingItemReader<Customer> reader(
            SqlSessionFactory sqlSessionFactory,
            @Value("#{stepExecutionContext['minTime']}") String minTime,
            @Value("#{stepExecutionContext['maxTime']}") String maxTime) {

        // minTime, maxTime이 null일 경우를 대비한 방어 로직
        if (minTime == null || maxTime == null) {
            return new MyBatisPagingItemReaderBuilder<Customer>()
                    .sqlSessionFactory(sqlSessionFactory)
                    .queryId("com.example.batch.mapper.CustomerMapper.findByTimeRange")
                    .parameterValues(Map.of("minTime", "1970-01-01T00:00:00", "maxTime", "1970-01-01T00:00:00"))
                    .pageSize(1000) // Page Size 확대
                    .build();
        }

        return new MyBatisPagingItemReaderBuilder<Customer>()
                .sqlSessionFactory(sqlSessionFactory)
                .queryId("com.example.batch.mapper.CustomerMapper.findByTimeRange")
                .parameterValues(Map.of("minTime", minTime, "maxTime", maxTime))
                .pageSize(1000) // Page Size 확대
                .build();
    }

    // 5. ItemWriter (MyBatis 배치 업데이트)
    @Bean
    public MyBatisBatchItemWriter<Customer> writer(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisBatchItemWriterBuilder<Customer>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId("com.example.batch.mapper.CustomerMapper.updateStatus")
                .itemToParameterConverter(item -> Map.of("id", item.id()))
                .assertUpdates(false) // 성능을 위해 업데이트 결과 체크 생략 가능
                .build();
    }

    // 6. Partitioner 설정
    @Bean
    public Partitioner partitioner(JdbcTemplate jdbcTemplate) {
        return new TimeRangePartitioner(jdbcTemplate);
    }
}
